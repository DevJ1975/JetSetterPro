# API Setup & Connection TODO — JetSetter Pro

> **What this is:** the running checklist of every external API the app integrates, where to sign
> up, how to wire the key, the auth scheme, and the (approximate) cost. Keep it in sync with
> `local.properties.example`, `core/secrets/Secrets.kt`, and `docs/API_REFERENCE.md` (which has the
> exact endpoints, auth headers, and request/response models). Cross-platform contracts live in
> `docs/IOS_PARITY_NOTES.md`.
>
> ⚠️ **Pricing changes constantly — every cost below is approximate; confirm on the provider's page
> before relying on it.** Free-tier limits especially drift.

---

## 0. How keys are wired (the one pattern)

Every key follows the same path — **you only ever edit `local.properties`**:

```
local.properties (git-ignored)        ← you put the key here
   │  read at build time by the `secret()` helper in app/build.gradle.kts
   ▼  (falls back to a same-named environment variable if absent from the file)
BuildConfig.API_*  (generated String constant)
   ▼
core/secrets/Secrets.kt   → Secrets.isConfigured(value)  (blank / "YOUR_…" / "REPLACE_ME" ⇒ not configured)
   ▼
Repository:  configured ? liveApi()  :  MockData     ← every feature degrades to realistic mock data
```

**To connect any API:** copy `local.properties.example` → `local.properties`, paste the key on the
right line, rebuild. No code change. A blank/placeholder key just means that feature shows mock data.

> **Heads-up (env-var fallback):** `secret()` also reads a same-named **environment variable** if the
> key isn't in `local.properties`. So if `API_ANTHROPIC` is exported in your shell, IRIS goes live
> even with a blank line in the file. Handy for CI; surprising locally.

**Never commit `local.properties`** (it's git-ignored). For CI, inject keys as Gradle properties or
env vars.

### Status legend
✅ connected (key in use) · 🟡 plumbed, key needed · ⬜ not wired yet (mock only) · ⛔ retired

---

## 1. Status at a glance

| API | Powers | Key(s) | Status | Priority |
|---|---|---|---|---|
| **Supabase** | Cross-device sync (trips, expenses) + auth | `SUPABASE_URL`, `SUPABASE_ANON_KEY` | ✅ keys set — see §2 for the 2 dashboard steps still needed | P0 |
| **Anthropic Claude** | IRIS assistant (chat + tool use) | `API_ANTHROPIC` | 🟡 add key to go live (else demo replies) | P0 |
| **Google Maps** | Flight map overlay | `MAPS_API_KEY` | ✅ key set (debug cert needs allow-listing) | P1 |
| **FlightAware AeroAPI** | Live flight status / board / in-flight | `API_FLIGHTAWARE` | 🟡 | P1 |
| **Google Vision** | Receipt OCR (expense scan) | `API_GOOGLE_VISION` | 🟡 | P1 |
| **Amadeus** | Flight offers / disruption alternatives | `API_AMADEUS_CLIENT_ID/SECRET` | 🟡 | P1 |
| **Duffel** | Flight rebooking | `API_DUFFEL` | 🟡 | P2 |
| **Expedia Rapid** | Hotel search | `API_EXPEDIA_CLIENT_ID/SECRET` | 🟡 | P2 |
| **SITA WorldTracer** | Baggage tracking | `API_SITA_WORLDTRACER` | 🟡 | P1 |
| **Uber** | Ground-transport price/time | `API_UBER_SERVER_TOKEN` | 🟡 | P1 |
| **Lyft** | Ground-transport price/time | `API_LYFT_CLIENT_ID/SECRET` | 🟡 | P1 |
| **Enterprise / Hertz / National** | Rental-car search + deep links | `API_ENTERPRISE` / `API_HERTZ` / `API_NATIONAL` | ⬜ | P2 |
| **Expensify / Ramp / Brex / Divvy** | Expense export | `API_EXPENSIFY_PARTNER_KEY`, `API_RAMP_*`, `API_BREX_CLIENT_ID`, `API_DIVVY_CLIENT_ID` | ⬜ | P2 |
| **Firebase** | (was: Firestore + Auth + AI Logic) | `API_FIREBASE_*` | ⛔ retired — replaced by Supabase + Claude | — |

---

## 2. Supabase — shared backend (data + auth) ✅ + 2 setup steps

- **Powers:** cross-device sync of trips & expenses (Postgres + Row Level Security) and anonymous
  user auth. The **same project the iOS app uses** — don't create a second one.
- **Keys:** `SUPABASE_URL`, `SUPABASE_ANON_KEY` — already in `local.properties` (project ref
  `bmlbbdyytbdmhizdqwnh`).
- **Sign up / console:** <https://supabase.com> → the existing JetSetter project. The publishable
  ("anon") key is under **Project Settings → API**. It's safe to ship in the app; security is
  enforced by **RLS**, not by hiding the key.
- **Auth scheme:** anonymous sign-in → per-user JWT; RLS policies key off `auth.uid()`.
- **Cost:** **Free tier** (≈500 MB DB, 50K monthly active users, unlimited API requests, anonymous
  auth). **Pro ≈ $25/mo** when you outgrow it. <https://supabase.com/pricing>

> ### ⚠️ Two dashboard steps before sync actually works (code is ready; these are not):
> 1. **Run the schema** — open **SQL Editor** and run `supabase/migrations/0001_init_trips_expenses.sql`
>    (creates `trips` + `expenses`, RLS policies, realtime publication). *(Done if the tables exist.)*
> 2. **Enable Anonymous Sign-Ins** — **Authentication → Sign In / Providers → Anonymous Sign-Ins → ON**.
>    Until this is on, `signInAnonymously()` fails and the app silently runs on **local data only**.
>    *(As of the last check this was still **OFF** — flip it to test live sync.)*

---

## 3. Anthropic Claude — IRIS assistant 🟡

- **Powers:** the IRIS chat (streaming replies) and **tool use** (IRIS can add a trip, log an
  expense, summarize spend). Tier order: on-device *(Phase C, pending)* → **Claude** → demo replies.
- **Key:** `API_ANTHROPIC`. Blank ⇒ IRIS answers from canned demo replies (still fully usable offline).
- **Sign up:** <https://console.anthropic.com> → **API Keys** → create key (starts with `sk-ant-…`).
- **Auth / endpoint:** `POST https://api.anthropic.com/v1/messages`, headers `x-api-key: <key>` +
  `anthropic-version: 2023-06-01`. Model **`claude-sonnet-4-6`**, `max_tokens: 1024`, `stream: true`.
- **Cost:** **pay-as-you-go per token.** `claude-sonnet-4-6` ≈ **$3 / 1M input tokens, $15 / 1M
  output tokens** (each IRIS turn is tiny — a fraction of a cent). Requires a small prepaid balance.
  <https://www.anthropic.com/pricing>
- **Privacy rule:** only the conversation + the static system prompt are sent. The on-device learned
  profile (Phase F) must **never** be sent to Claude.

---

## 4. Google Maps Platform — flight map ✅

- **Powers:** the live-flight map overlay (Flight Tracker / Home). Without it, a Compose-drawn map
  is shown instead.
- **Key:** `MAPS_API_KEY` (injected as the `com.google.android.geo.API_KEY` manifest meta-data).
- **Sign up:** <https://console.cloud.google.com> → enable **Maps SDK for Android** → **APIs &
  Services → Credentials** → create an API key. **Restrict** it to the app's package +
  signing-cert SHA-1 (add both the **debug** and **release** SHA-1; see `docs`/release notes).
- **Cost:** **$200/mo free credit**; native Android map loads are generally not separately billed —
  verify on <https://mapsplatform.google.com/pricing/>.
- **Note from runtime test:** the debug build's cert isn't on the key's allow-list yet (logcat:
  *"Android Application (…): …;com.trainovate.jetsetterpro.debug"*), so the real map falls back to
  the Compose map in debug. Add the debug SHA-1 to the key to see Google Maps in debug.

---

## 5. Flights & disruption

### FlightAware AeroAPI 🟡 — `API_FLIGHTAWARE`
- **Powers:** live flight status, departures/arrivals board, in-flight tracking.
- **Sign up:** <https://www.flightaware.com/commercial/aeroapi/> → AeroAPI key.
- **Auth / endpoint:** `GET https://aeroapi.flightaware.com/aeroapi/flights/{ident}`, header `x-apikey`.
- **Cost:** a small **free Personal tier** monthly allowance; **Standard/Premium** are usage-based
  (per-result pricing). Confirm tiers on the AeroAPI page.

### Amadeus Self-Service 🟡 — `API_AMADEUS_CLIENT_ID` / `API_AMADEUS_CLIENT_SECRET`
- **Powers:** flight offers search + disruption-rebooking alternatives.
- **Sign up:** <https://developers.amadeus.com> → register app → get API Key + Secret.
- **Auth:** OAuth2 client-credentials → Bearer token.
- **Cost:** **free test environment** (quota-limited); production is pay-per-call after you move to
  the production keys. <https://developers.amadeus.com/pricing>

### Duffel 🟡 — `API_DUFFEL`
- **Powers:** flight rebooking / offers.
- **Sign up:** <https://duffel.com> → Dashboard → access token.
- **Auth:** `Authorization: Bearer <token>`.
- **Cost:** **free test mode**; live mode requires a Duffel agreement (per-booking economics).

---

## 6. Hotels, baggage, ground transport, cars

### Expedia Rapid 🟡 — `API_EXPEDIA_CLIENT_ID` / `API_EXPEDIA_CLIENT_SECRET`
- **Powers:** hotel search & detail.
- **Sign up:** <https://developers.expediagroup.com> → apply to the **Rapid** API partner program.
- **Auth:** OAuth2 client-credentials → Bearer; token at `…/identity/oauth2/v3/token`.
- **Cost:** partner program (no public self-serve pricing) — apply for access.

### SITA WorldTracer 🟡 — `API_SITA_WORLDTRACER`
- **Powers:** lost/delayed baggage tracking by 10-digit IATA tag.
- **Sign up:** <https://www.sita.aero> → enterprise/airline partner onboarding (not self-serve).
- **Auth:** header `x-partner-key`; `GET /baggage/v1/baggage/{tagNumber}`.
- **Cost:** enterprise contract.

### Uber 🟡 — `API_UBER_SERVER_TOKEN`
- **Powers:** ride price/time estimates to/from the airport.
- **Sign up:** <https://developer.uber.com> → create app → Server Token.
- **Auth:** `Authorization: Token <server-token>`; `GET /v1.2/estimates/price`.
- **Cost:** free developer account; **Rides API access is gated** — request access for the
  estimates endpoints.

### Lyft 🟡 — `API_LYFT_CLIENT_ID` / `API_LYFT_CLIENT_SECRET`
- **Powers:** ride price/time estimates.
- **Sign up:** <https://developer.lyft.com>.
- **Auth:** OAuth2 client-credentials → Bearer; `GET /v1/cost`.
- **Cost:** free dev tier. ⚠️ **Verify availability** — Lyft's public API has had limited/changing
  access; if unavailable, the feature stays on deep-links + mock.

### Rental cars ⬜ — `API_ENTERPRISE` / `API_HERTZ` / `API_NATIONAL`
- **Powers:** rental search; falls back to **deep links** into each provider's app / Play Store.
- **Sign up:** each is a **partner/affiliate** arrangement (Enterprise/Hertz/National developer or
  affiliate programs) — no public self-serve key. Until keyed, the app uses deep links + mock data.
- **Auth:** API key header (Hertz uses `api-key`; Enterprise/National use `x-api-key`).

---

## 7. Expense export ⬜ (P2)

`API_EXPENSIFY_PARTNER_KEY`, `API_RAMP_CLIENT_ID`/`API_RAMP_CLIENT_SECRET`, `API_BREX_CLIENT_ID`,
`API_DIVVY_CLIENT_ID` — push exported expense reports into a corporate card / expense platform.

- **Expensify:** partner key — <https://www.expensify.com/tools/integrations/>.
- **Ramp:** OAuth2 — <https://docs.ramp.com> (developer program).
- **Brex:** OAuth2 / token — <https://developer.brex.com>.
- **Divvy (BILL Spend & Expense):** OAuth2 / token — <https://developer.bill.com>.
- **Cost:** all are **business partner programs** (apply per provider); no consumer self-serve. Until
  keyed, export produces a local PDF only.

---

## 8. Retired ⛔ — Firebase

`API_FIREBASE_PROJECT_ID`, `API_FIREBASE_API_KEY`, `google-services.json`, the `google-services`
gradle plugin, and the `firebase-*` dependencies are **no longer used**:

- Firestore + Firebase Auth → **Supabase** (§2).
- Firebase AI Logic (Gemini) → **Anthropic Claude** (§3).

The deps/plugin/`google-services.json` are still present but dead weight (Firebase still
initializes at startup for nothing). **Cleanup TODO:** remove the three `firebase-*` deps + the
`google-services` plugin + `google-services.json`, and delete the legacy `API_FIREBASE_*` lines.

---

## 9. Quick-start: make the app fully live

1. Copy `local.properties.example` → `local.properties`.
2. Paste **`API_ANTHROPIC`** (IRIS live) and any flight/transport keys you have.
3. In the **Supabase dashboard**: run the migration (§2 step 1) **and** enable Anonymous Sign-Ins
   (§2 step 2).
4. Add the **debug SHA-1** to the Google Maps key (§4) if you want the real map in debug.
5. Rebuild. Anything still blank keeps showing realistic mock data — the app is always demoable.
