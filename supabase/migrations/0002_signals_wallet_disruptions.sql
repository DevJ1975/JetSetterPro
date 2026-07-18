-- JetSetter Pro — shared Supabase schema (schema v2): travel signals, wallet items,
-- disruption events (plan B5).
--
-- ⚠️  NOT YET APPLIED to the live project (ref bmlbbdyytbdmhizdqwnh) — apply manually in the
-- dashboard SQL Editor or via the Supabase MCP once dashboard access exists (see todo.md).
-- Until it is applied, the Android client's best-effort sync guards absorb every failure
-- (missing table → silent no-op / empty), so shipping the client ahead of the schema is safe.
--
-- This is the cross-platform contract: Android and iOS read/write the SAME tables in the SAME
-- project. Keep this file and docs/IOS_PARITY_NOTES.md in lockstep; if you change a column,
-- change both clients together and bump the version.
--
-- DESIGN NOTES
--  • `travel_signals.payload` is a native `jsonb` object carrying the signal's
--    value/attributes/timestamp/source (camelCase keys, the iOS-shared encoding); the signal
--    kind's camelCase wire name (e.g. `seatChosen`) lives in its own `kind` column. Keeping the
--    body opaque means signal fields can evolve without a schema change.
--  • `wallet_items` columns mirror the client's TravelWalletItem fields snake_cased
--    (feature/travelwallet/TravelwalletModels.kt); `type` is the TravelWalletPassType enum
--    constant name: BOARDING_PASS | HOTEL | RENTAL_CAR | EVENT_TICKET | INSURANCE.
--  • `disruption_events.payload` holds the full provider payload as `jsonb` for
--    forward-compatible detail; `status` carries the disruption state label (e.g. DELAYED).
--  • Dates/timestamps the clients own (`date`, `detected_at`) are stored as `text`
--    (ISO-8601 strings) to exactly match the clients' String fields — the 0001 convention.
--  • Every row is owned by `user_id` and protected by RLS keyed on auth.uid(); this works for
--    anonymous users too (auth.uid() is the anonymous user's id).

create extension if not exists "pgcrypto";

-- ── travel_signals ───────────────────────────────────────────────────────────
create table if not exists public.travel_signals (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade default auth.uid(),
    kind       text not null,                            -- camelCase wire name, e.g. seatChosen | expenseLogged
    payload    jsonb not null default '{}'::jsonb,       -- { value, attributes, timestamp, source }
    updated_at timestamptz not null default now()
);

create index if not exists travel_signals_user_id_idx on public.travel_signals (user_id);

-- ── wallet_items ─────────────────────────────────────────────────────────────
create table if not exists public.wallet_items (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users (id) on delete cascade default auth.uid(),
    type             text not null,                      -- enum name: BOARDING_PASS | HOTEL | RENTAL_CAR | EVENT_TICKET | INSURANCE
    title            text not null,
    subtitle         text not null,
    key_detail_label text not null,                      -- e.g. Seat / Confirmation / Policy
    key_detail_value text not null,
    date             text not null,                      -- ISO-8601 date, e.g. 2026-07-14
    is_favorite      boolean not null default false,
    updated_at       timestamptz not null default now()
);

create index if not exists wallet_items_user_id_idx on public.wallet_items (user_id);

-- ── disruption_events ────────────────────────────────────────────────────────
create table if not exists public.disruption_events (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references auth.users (id) on delete cascade default auth.uid(),
    flight_number text not null,                         -- e.g. DL1423
    route         text not null,                         -- e.g. JFK → LHR
    status        text not null,                         -- e.g. ON_TIME | DELAYED | CANCELLED | REBOOKED
    reason        text not null,
    detected_at   text not null,                         -- ISO-8601 timestamp string (client-owned)
    payload       jsonb not null default '{}'::jsonb,    -- full provider payload, forward-compatible
    updated_at    timestamptz not null default now()
);

create index if not exists disruption_events_user_id_idx on public.disruption_events (user_id);

-- ── Row Level Security: owner-only on every operation ────────────────────────
alter table public.travel_signals    enable row level security;
alter table public.wallet_items      enable row level security;
alter table public.disruption_events enable row level security;

drop policy if exists travel_signals_owner_all on public.travel_signals;
create policy travel_signals_owner_all on public.travel_signals
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists wallet_items_owner_all on public.wallet_items;
create policy wallet_items_owner_all on public.wallet_items
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists disruption_events_owner_all on public.disruption_events;
create policy disruption_events_owner_all on public.disruption_events
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- ── Realtime: publish row changes so the live cross-device listener fires ─────
-- (Wrapped so re-running the migration doesn't error if the table is already published.)
do $$
begin
    begin
        alter publication supabase_realtime add table public.travel_signals;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.wallet_items;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.disruption_events;
    exception when duplicate_object then null;
    end;
end $$;
