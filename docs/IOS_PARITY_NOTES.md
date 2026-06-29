# iOS ⇄ Android Parity Notes

> **Audience: the iOS agent.** This file records decisions made on the **Android** port that
> the **iOS** app must match (or consciously diverge from) so the two stay identical in **look
> and function**. When you change a shared contract on iOS, update this file and the Android
> agent will mirror it. Android source of truth for these decisions: `AGENT_GUIDE.md`.

Last updated by the Android port: **cross-device data + auth migrated from Firebase to Supabase** (shared Postgres, RLS, anonymous Auth). Firestore retired. Firebase now powers only the IRIS assistant model (AI Logic) on Android.

---

## 1. Backends — who owns what now

- **Shared data + auth = Supabase.** Both platforms talk to the **same Supabase project** (ref `bmlbbdyytbdmhizdqwnh`), so they share one Postgres database (protected by Row Level Security) and the same Auth users. This replaced Cloud Firestore.
- **Firebase = IRIS only (Android).** The `jetsetter-pro` Firebase project (number `857695467541`) is still used on Android **only** for the IRIS assistant model via Firebase AI Logic (Gemini). It is **no longer** the data or auth backend. *(iOS IRIS still uses Apple/Claude — see §4.)*
- **Android app ids:** `com.trainovate.jetsetterpro` (release) and `com.trainovate.jetsetterpro.debug` (debug).
- **iOS action:** point the iOS data/auth layer at the **same Supabase project** (URL + publishable anon key). Do **not** stand up a second Supabase project — cross-device sync depends on one. The schema lives in `supabase/migrations/0001_init_trips_expenses.sql`; run it once (either client may apply it). Enable **Anonymous Sign-Ins** in the Supabase Auth settings.

## 2. Supabase schema — THE cross-device contract (schema v1, must match exactly)

Canonical DDL: **`supabase/migrations/0001_init_trips_expenses.sql`**. Tables live in `public`.

### `public.trips` (one row per trip)

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` (PK) | the client-generated trip id (UUID string) |
| `user_id` | `uuid` | owner; defaults to `auth.uid()`; RLS-enforced |
| `name` | `text` | |
| `destination` | `text` | |
| `start_date` | `text` | ISO-8601 date, e.g. `2026-07-14` |
| `end_date` | `text` | ISO-8601 date |
| `items` | `jsonb` | **native JSON array** of itinerary items (see below) |
| `packing_list` | `jsonb` | **native JSON array** of packing items |
| `updated_at` | `timestamptz` | server default `now()` |

> ✅ **Schema-v1 change from the old Firestore contract:** `items`/`packing_list` are now **native `jsonb` arrays**, not JSON-in-a-string. Top-level columns are **snake_case** (`user_id`, `start_date`, `end_date`, `packing_list`). The JSON keys *inside* the arrays stay **camelCase** (below). iOS must map its top-level `CodingKeys` to snake_case but keep the array-element keys camelCase.

**`items[]` element** (`ItineraryItem`): `id` (string), `title` (string), `type` (string enum — `FLIGHT` | `HOTEL` | `ACTIVITY` | `TRANSPORT` | `RESTAURANT`), `startDate` (ISO-8601 string), `endDate` (string?, optional), `location` (string?, optional), `notes` (string?, optional).

**`packing_list[]` element** (`PackingItem`): `id` (string), `name` (string), `isPacked` (bool).

### `public.expenses` (one row per expense)

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` (PK) | client-generated expense id |
| `user_id` | `uuid` | owner; defaults to `auth.uid()`; RLS-enforced |
| `amount` | `double precision` | |
| `currency` | `text` | default `USD` |
| `category` | `text` | enum name — `FOOD` \| `TRANSPORT` \| `ACCOMMODATION` \| `ENTERTAINMENT` \| `BUSINESS` \| `SHOPPING` \| `MEDICAL` \| `MILEAGE` \| `OTHER` |
| `merchant` | `text` | |
| `date` | `text` | ISO-8601 date |
| `notes` | `text` | optional |
| `updated_at` | `timestamptz` | server default `now()` |

> Both `trips` and `expenses` sync is now wired on Android (Room offline-first + Supabase mirror). iOS should read/write these tables with the exact column + enum shapes above.

## 3. Auth

- Android signs in **anonymously** on first run (`core.auth.AuthRepository.ensureSignedIn()` → Supabase `signInAnonymously`) so sync works without forcing a login; the anonymous account can later be upgraded to email/Google (which **links**, preserving the uid). The session is persisted, so the same uid is reused across launches.
- RLS keys off `auth.uid()` = the Supabase user id; every row carries `user_id` and is only visible to its owner (anonymous users included).
- **iOS action:** use the same Supabase anonymous-first model so the `auth.uid()` namespace matches and an upgraded account links rather than forks. Requires **Anonymous Sign-Ins** enabled in the Supabase project.

## 4. IRIS assistant — now provider-aligned (Claude on both platforms)

- **Android:** IRIS streams from **Anthropic Claude**, model **`claude-sonnet-4-6`**, `POST /v1/messages` with `stream: true`, `max_tokens: 1024` (`core.ai.ClaudeClient`). Tiering mirrors iOS: on-device **Gemini Nano** (Phase C — ML Kit GenAI / AICore, not yet wired) → Claude → canned demo. Live when `API_ANTHROPIC` is set; otherwise the demo fallback answers. **Firebase AI Logic / Gemini is retired — Firebase is no longer used by Android at all.**
- **iOS:** Apple Intelligence (on-device) → Claude → demo. Same Claude model id — keep it in sync.
- **Parity rule:** keep the **system prompt, tone, and demo/canned replies word-for-word identical** across platforms. They live in `core.ai.IrisPersona` (`SYSTEM_PROMPT` + `demoResponse`). One wording change from the old Gemini build: the "no AI configured" hint now reads *"Add your Anthropic API key to turn on live AI."* — match it on iOS.
- **Privacy:** only the conversation + the static `SYSTEM_PROMPT` are sent to Claude. The on-device learned profile (Phase F) must **never** be put in the request.
- **Roles:** the app stores turns as `user`/`model` (`core.ai.AiMessage`); the Claude path maps `model → assistant` and drops any leading assistant turn so the request starts with `user`. Keep conversation content identical across platforms.

## 5. Design system (already aligned — keep it that way)

- Android tokens (`ui/theme/`) are a faithful port of iOS `JetsetterTheme`: same colors (deep navy + sky-blue accent), spacing scale (4/8/16/24/32), card radius 18dp / padding 16dp.
- **Naming difference to know:** the iOS `GoldTag` is named **`AccentTag`** on Android (the historical "gold" naming was dropped; it renders blue on both). Same component, different name — no visual difference intended.
- The app is **100% icon-drawn** (no image assets) on both platforms; Android maps SF Symbols → Material Symbols per `docs/SF_SYMBOL_MAP.md`. Keep any new iconography in sync via that map.

## 6. iOS agent checklist (to reach parity with current Android state)

- [ ] iOS data/auth layer points at the **same Supabase project** (ref `bmlbbdyytbdmhizdqwnh`); schema applied from `supabase/migrations/0001_init_trips_expenses.sql`; **Anonymous Sign-Ins** enabled.
- [ ] iOS app still registered in `jetsetter-pro` Firebase project if it shares any Firebase service (otherwise Firebase is Android-IRIS-only now).
- [ ] Supabase read/write uses `public.trips` / `public.expenses` with the **exact** column + jsonb shapes in §2 (snake_case columns, camelCase array keys, native `jsonb` not JSON-strings).
- [ ] Supabase **anonymous** sign-in on first launch (§3).
- [ ] IRIS system prompt + demo replies match Android verbatim (§4).
- [ ] Confirm `AccentTag`/`GoldTag` and other renamed components are visually identical (§5).
