-- JetSetter Pro — shared Supabase schema (schema v1).
--
-- This is the cross-platform contract: Android and iOS read/write the SAME tables in the SAME
-- project (ref bmlbbdyytbdmhizdqwnh). Keep this file and docs/IOS_PARITY_NOTES.md §2 in lockstep;
-- if you change a column, change both clients together and bump the version.
--
-- HOW TO APPLY: this project is not reachable from the build machine's Supabase MCP account, so
-- run this by hand in the Supabase dashboard → SQL Editor (or `supabase db push` with the CLI
-- linked to the project). Also enable anonymous sign-ins:
--   Dashboard → Authentication → Sign In / Providers → Anonymous Sign-Ins → ON.
--
-- DESIGN NOTES
--  • Itinerary items and packing items are native `jsonb` arrays (NOT JSON-in-a-string as the old
--    Firestore mirror used). The JSON keys inside those arrays are camelCase (startDate, endDate,
--    location, notes, isPacked, type) so both clients decode the same shape.
--  • `type` inside an itinerary item is the enum NAME: FLIGHT | HOTEL | ACTIVITY | TRANSPORT | RESTAURANT.
--  • Dates are stored as `text` (ISO-8601 strings) to exactly match the clients' String fields and
--    avoid coercion surprises (an itinerary item's startDate may carry a time component).
--  • Every row is owned by `user_id` and protected by RLS keyed on auth.uid(); this works for
--    anonymous users too (auth.uid() is the anonymous user's id).

create extension if not exists "pgcrypto";

-- ── trips ────────────────────────────────────────────────────────────────────
create table if not exists public.trips (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references auth.users (id) on delete cascade default auth.uid(),
    name         text not null,
    destination  text not null,
    start_date   text not null,                          -- ISO-8601 date, e.g. 2026-07-14
    end_date     text not null,
    items        jsonb not null default '[]'::jsonb,     -- [{ id, title, type, startDate, endDate?, location?, notes? }]
    packing_list jsonb not null default '[]'::jsonb,     -- [{ id, name, isPacked }]
    updated_at   timestamptz not null default now()
);

create index if not exists trips_user_id_idx on public.trips (user_id);

-- ── expenses ─────────────────────────────────────────────────────────────────
-- (Schema is laid down now; the Android client wires expense sync in Phase A step 2.)
create table if not exists public.expenses (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade default auth.uid(),
    amount     double precision not null,
    currency   text not null default 'USD',
    category   text not null,                            -- enum name: FOOD | TRANSPORT | ACCOMMODATION | ENTERTAINMENT | BUSINESS | SHOPPING | MEDICAL | MILEAGE | OTHER
    merchant   text not null,
    date       text not null,                            -- ISO-8601 date
    notes      text,
    updated_at timestamptz not null default now()
);

create index if not exists expenses_user_id_idx on public.expenses (user_id);

-- ── Row Level Security: owner-only on every operation ────────────────────────
alter table public.trips    enable row level security;
alter table public.expenses enable row level security;

drop policy if exists trips_owner_all on public.trips;
create policy trips_owner_all on public.trips
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists expenses_owner_all on public.expenses;
create policy expenses_owner_all on public.expenses
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- ── Realtime: publish row changes so the live cross-device listener fires ─────
-- (Wrapped so re-running the migration doesn't error if the table is already published.)
do $$
begin
    begin
        alter publication supabase_realtime add table public.trips;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.expenses;
    exception when duplicate_object then null;
    end;
end $$;