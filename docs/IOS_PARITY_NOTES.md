# iOS ⇄ Android Parity Notes — the living cross-platform contract

> **This is a running document shared by BOTH platforms.** It is the single source of truth for
> every contract the iOS app and the Android app must agree on — backend schema, auth model, the
> IRIS assistant's behavior, and the design system. **Read it before you change anything shared,
> and update it in the same change.** When you open Xcode, reference this file; when you open
> Android Studio, reference this file. Whichever platform changes a shared contract first writes
> the change here, and the other platform mirrors it.
>
> Audience: **both** the iOS and Android developers/agents. (Android's deeper internal rationale
> lives in `AGENT_GUIDE.md`; iOS keeps its own, but anything *cross-platform* belongs **here**.)

### How to use this document (the parity protocol)

1. **Before** editing anything that crosses the platform boundary — a DB table/column, an enum
   value, an auth flow, the IRIS system prompt or demo replies, a shared model field, a renamed UI
   component — check the relevant section below.
2. If your change alters a shared contract, **edit this file in the same commit/PR** and add a row
   to the change log. Bump the schema version in §2 if you touch the Supabase shape.
3. The other platform treats a change here as a to-do: mirror it, then tick it off (§6 checklist).
4. **Intentional divergences are allowed** but must be recorded with the reason (e.g. IRIS's
   on-device tier differs by OS) so neither side "fixes" them by accident.
5. Keep field names, enum casing, and wire shapes **byte-identical** unless a section says otherwise.

### Change log (newest first)

| Date | Platform | Change |
|---|---|---|
| 2026-07-03 | Android | **Demo mode + trip-day features** landed for the investor build: seeded traveler persona, presentation-safe IRIS copy, cabin-chime disruption alerts, in-app-only navigation rule, interactive seat map, GPS + weather departure loop with IRIS briefing. **New §7 — iOS should converge.** |
| 2026-07-03 | Android | IRIS demo/canned replies **reworded to be presentation-safe** — the *"Add your Anthropic API key…"* hint is **removed**; leave-by/traffic/weather replies now render from live Departure Optimizer state. Supersedes the §4 wording note. |
| 2026-06-29 | Android | IRIS cloud tier moved to **Anthropic Claude `claude-sonnet-4-6`** (streaming + tool use); Firebase fully removed from source. See §4. |
| 2026-06-29 | Android | Cross-device backend migrated **Firestore → Supabase** (Postgres + RLS + anonymous Auth); `trips`/`expenses` schema v1. See §1–3. |

> Snapshot: data + auth = **Supabase** (shared project, RLS, anonymous sign-in). IRIS = on-device
> tier *(Phase C, pending)* → **Claude** → demo, on both platforms. Firebase is **retired**.

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
- **Parity rule:** keep the **system prompt, tone, and demo/canned replies word-for-word identical** across platforms. They live in `core.ai.IrisPersona` (`SYSTEM_PROMPT` + `demoResponse`).
  - **⚠️ Updated 2026-07-03 (supersedes the old wording note):** the demo replies are now **presentation-safe** — the *"Add your Anthropic API key to turn on live AI."* hint has been **removed** (it must never appear in front of investors). iOS should drop that line too. The current keyword→reply map (delay/disruption, pack, expense, leave/traffic/navigate/drive, weather, seat/check, blank, else) and the exact strings are the source of truth in `core.ai.IrisPersona.demoResponse`; the **leave-by / traffic / weather** replies are **rendered from the live Departure Optimizer estimate** (see §7), not hardcoded, so they never contradict that screen. Mirror this behavior on iOS.
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
- [ ] IRIS system prompt + demo replies match Android verbatim (§4) — including the **removed** API-key hint and the **live-state** leave-by/traffic/weather replies (§7).
- [ ] Confirm `AccentTag`/`GoldTag` and other renamed components are visually identical (§5).
- [ ] **Demo mode + seeded persona match Android (§7):** same `DemoSeeder` persona values, a demo-mode toggle with one-tap reset, cabin-chime disruption alerts, the in-app-only navigation rule, the interactive seat map, and the GPS + weather departure loop with the IRIS briefing.

## 7. Demo mode & the seeded traveler persona (Android-first — iOS converge)

Landed 2026-07-03 for the investor build. These are **intentional Android-first changes**; iOS
should mirror them so a demo on either device tells the identical story. Android homes are in
parentheses for reference.

### 7.1 The one seeded persona (match these values exactly)
Every demo surface describes the **same traveler and trip** — keep iOS sample data byte-identical:

- **Traveler:** *Jordan Ellis*, home airport **LAS** (Android `DemoSeeder` fills blank profile fields).
- **Primary flight:** **Delta DL 1423, LAS → ATL**, **First cabin**, seat **3A**, gate **C22**,
  scheduled **7:00 AM** departure.
- **Trip:** *Atlanta Board Meeting*, **Jul 14–17 2026**, hotel **The Ritz-Carlton, Atlanta**
  (confirmation **RC-8842193**). Second trip on the books: *Tokyo Product Summit*, Sep 2026.
- **Expenses:** **$1,812.75** across 4 items — Delta airfare **$1,290** (largest), Ritz-Carlton
  $412.55, Bacchanalia $86.20, Uber $24.
- **Departure math:** leave by **5:19 AM** = 7:00 AM − (34-min drive + 15 parking + 22 TSA + 30 gate).
  Default weather **"Clear skies · 74°F"**.
- **Disruption story:** DL 1423 delayed **7:00 → 8:35 AM** (1h 35m, **weather hold at ATL**); three
  same-day alternatives — **AA 218** (First, arr 3:25 PM, $412), **DL 2207** (Comfort+, arr 4:58 PM,
  $289), **WN 1190** (Main, arr 6:40 PM, $198).
- **Wallet passes:** boarding pass DL 1423 First **3A · Zone 1**; Ritz-Carlton **RC-8842193**;
  Hertz Tesla Model 3; Q3 Leadership Summit ticket; AIG Travel Guard insurance.

### 7.2 Demo-mode switch + reset (`DemoSeeder`, DataStore `demoMode`)
- A **Demo mode toggle** (Android: More → Presentation, **and** an alpha `DEMO` chip on the Home
  header). Enabling it: resets all feature state to the persona above, marks onboarding complete,
  fills blank profile fields, and **arms a scripted disruption push ~25s later**.
- **"Reset demo data"** restores the pristine seeded dataset any time (between run-throughs).
- iOS should expose the same toggle + reset and the same ~25s scripted-alert beat.

### 7.3 Presentation-safe IRIS (see §4)
Demo/canned replies carry **no setup hints**; the leave-by/traffic/weather answers are rendered
from the **live Departure Optimizer estimate** so they can't contradict that screen. A
*"When should I leave?"* suggestion chip was added, and the live Claude tier gained a
`get_departure_briefing` tool over the same data.

### 7.4 Cabin-chime disruption alerts
- **Every** notification on the disruption channel — delay, gate change, cancellation, rebooking —
  plays a bundled cabin **"fasten seatbelt" chime** as the channel sound (Android:
  `res/raw/cabin_chime.wav`, set on notification channel `disruption_alerts_v2`). iOS should ship
  the same sound file and attach it to the equivalent notification category.

### 7.5 Interactive seat map (Check-In)
- Check-in flows **through seat selection**: a cabin seat map (First 2-2 / Main 3-3, seeded
  occupancy that always leaves the held seat free). Issued boarding passes support **"Change seat"**
  while the window is open; the chosen seat persists and prints on the pass. iOS: add the seat-map
  step with the same layout + persistence.

### 7.6 GPS + weather departure loop (Departure Optimizer)
- A **Navigate** button opens **in-app** route guidance (map + a simulated "Start drive" with
  remaining time/distance counting down against a steady ETA). The live estimate also rolls
  **weather** (condition + °F + risk) alongside traffic and TSA. iOS: mirror the in-app route sheet
  and the weather factor.

### 7.7 📌 Product rule — every experience stays in-app
No feature may hand the user off to an external app or browser (no map/dialer/browser launches);
navigation, booking, and export all render **inside** the app. The only exception is the system
notification whose tap deep-links back in. **This is a cross-platform product rule — hold iOS to it
too** (watch iOS `openURL`/`MKMapItem.openInMaps`/Safari hand-offs; bring those in-app).
