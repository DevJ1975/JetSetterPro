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
| 2026-07-17 | Android | **Phase-1 iOS parity** landed: IRIS system prompt is now the **full sectioned persona** (`IrisPersona.BASE_PROMPT` — supersedes the 3-line lockstep rule, §4); personalization goes to **both** AI tiers via `IrisSystemPromptBuilder` (privacy invariant redefined, §4); **14-tool camelCase roster with staged confirmations** (§10); spec-shaped **learning stack** (`TravelSignal` + 365-day half-life engine + consent gates) and **IRIS memory** (`iris_memory`) replace `UserMemory` (§8); **12-kind proactive engine** (§12); **hands-free voice loop** (§9); **Loved Ones** SMS, bag estimator, shared local keys, Open-Meteo weather + frankfurter FX (§13). Divergences in §11; known Phase-1 limitations in §14. |
| 2026-06-29 | Android | IRIS gains an **on-device RAG knowledge base** (general-travel KB + the user's own data), a **Gemini Nano** on-device tier (ML Kit GenAI Prompt API), an on-device **preference-learning loop** (`UserMemory`), proactive anticipation upgrades, and **voice output (TTS)**. See §4, §7–§9. |
| 2026-06-29 | Android | IRIS cloud tier moved to **Anthropic Claude `claude-sonnet-4-6`** (streaming + tool use); Firebase fully removed from source. See §4. |
| 2026-06-29 | Android | Cross-device backend migrated **Firestore → Supabase** (Postgres + RLS + anonymous Auth); `trips`/`expenses` schema v1. See §1–3. |

> Snapshot: data + auth = **Supabase** (shared project, RLS, anonymous sign-in). IRIS = on-device
> tier (Gemini Nano on Android, Apple Intelligence on iOS) → **Claude** → demo, with full tool
> calling + staged confirmations on the Claude tier (§10). Firebase is **retired**.

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

- **Android:** IRIS streams from **Anthropic Claude**, model **`claude-sonnet-4-6`**, `POST /v1/messages` with `stream: true`, `max_tokens: 1024` (`core.ai.ClaudeClient`). Tiering mirrors iOS: on-device **Gemini Nano** (ML Kit GenAI Prompt API, `core.ai.GeminiNanoOnDeviceAi`) → Claude → canned demo, with the on-device tier tried **first whenever it is available** (regardless of the Anthropic key) — except that tool-intent turns divert to Claude (§11a). The Nano tier **self-gates** (`isAvailable()` is true only when AICore reports the feature `AVAILABLE`), so on the vast majority of devices routing still falls through to Claude. Live when `API_ANTHROPIC` is set; otherwise the demo fallback answers. **Firebase AI Logic / Gemini is retired — Firebase is no longer used by Android at all.**
- **iOS:** Apple Intelligence (on-device) → Claude → demo. Same Claude model id — keep it in sync.
- **System prompt — ⚠️ contract superseded (2026-07-17):** the old *"3-line prompt, word-for-word"* lockstep rule is **retired**. The canonical shared prompt is now the **full sectioned persona** — the IDENTITY block plus the labeled `PERSONA` / `VOICE` / `CAPABILITIES` / `ACTIONS` / `MEMORY` / `PRINCIPLES` / `FORMAT` sections — reproduced verbatim from the parity spec §1.2 in `core.ai.IrisPersona.BASE_PROMPT`. **iOS must ship the same text**, byte-for-byte. Demo/canned replies remain **word-for-word identical** (`IrisPersona.demoResponse`; the "no AI configured" hint still reads *"Add your Anthropic API key to turn on live AI."*).
- **Session-start dynamic sections (both tiers):** `core.ai.IrisSystemPromptBuilder` appends, in order: stored **preferences** summary → traveler **persona** → learned **profile** summary → **live context** snapshot (active/next trip + top-3 upcoming items, next flight, expense count + totals by currency, closed with a "don't invent details" line — `core.ai.LiveContextBuilder`). Empty sections are omitted; the dynamic block is trimmed to a ~4k-token budget. **These sections now go to BOTH AI tiers, Claude included** — the old "cloud gets PUBLIC only" gate is removed. RAG knowledge (§7) is appended per-turn on top by `core.rag.ContextAssembler`, also `PUBLIC` + `PERSONAL` for both tiers.
- **Privacy invariant — redefined:** the boundary is no longer "cloud LLM vs on-device"; both AI tiers are sanctioned processors for personalization. The invariant that remains, on both platforms: **third-party DATA APIs** (FlightAware, Open-Meteo, frankfurter, and future Uber/Lyft/SITA) **receive only IATA codes, coordinates, currency codes, and flight idents — never profile, preference, or memory data.** Android pins this with request-builder unit tests; iOS should do the same.
- **Roles:** the app stores turns as `user`/`model` (`core.ai.AiMessage`); the Claude path maps `model → assistant` and drops any leading assistant turn so the request starts with `user`. Keep conversation content identical across platforms. (The Claude request is a stateless full resend with client-side compaction — see §11b.)

## 5. Design system (already aligned — keep it that way)

- Android tokens (`ui/theme/`) are a faithful port of iOS `JetsetterTheme`: same colors (deep navy + sky-blue accent), spacing scale (4/8/16/24/32), card radius 18dp / padding 16dp.
- **Naming difference to know:** the iOS `GoldTag` is named **`AccentTag`** on Android (the historical "gold" naming was dropped; it renders blue on both). Same component, different name — no visual difference intended.
- The app is **100% icon-drawn** (no image assets) on both platforms; Android maps SF Symbols → Material Symbols per `docs/SF_SYMBOL_MAP.md`. Keep any new iconography in sync via that map.

## 6. iOS agent checklist (to reach parity with current Android state)

- [ ] iOS data/auth layer points at the **same Supabase project** (ref `bmlbbdyytbdmhizdqwnh`); schema applied from `supabase/migrations/0001_init_trips_expenses.sql`; **Anonymous Sign-Ins** enabled.
- [ ] iOS app still registered in `jetsetter-pro` Firebase project if it shares any Firebase service (otherwise Firebase is Android-IRIS-only now).
- [ ] Supabase read/write uses `public.trips` / `public.expenses` with the **exact** column + jsonb shapes in §2 (snake_case columns, camelCase array keys, native `jsonb` not JSON-strings).
- [ ] Supabase **anonymous** sign-in on first launch (§3).
- [ ] IRIS **base system prompt** (`IrisPersona.BASE_PROMPT` — the full sectioned persona) + demo replies match Android verbatim; dynamic sections appended in the §4 order.
- [ ] Confirm `AccentTag`/`GoldTag` and other renamed components are visually identical (§5).
- [ ] On-device RAG KB: seed from the **same** versioned artifact + manifest, implement a **parity embedder** (same model id + dim + normalization); note the tier gate is now PUBLIC+PERSONAL for both tiers (§7).
- [ ] Implement the **learning stack** (`TravelSignal` kinds, 365-day half-life engine, consent flags, FIFO-2000 store) and **IRIS memory** (`iris_memory`, 0.7/+0.1/cap 1.0) with the exact keys/enums in §8. *(Supersedes the old `UserMemory`/45-day item — Android has deleted `UserMemory`.)*
- [ ] Ship the **14-tool roster with staged confirmations** — same camelCase names, staging semantics, rejection string, and pending-action kind enum (§10).
- [ ] Mirror the **12 proactive suggestion kinds** in priority order, with the never-suppressed set and 3-dismissal preference backoff (§12).
- [ ] Adopt (or counter-propose) the shared **local keys, SMS templates, bag-estimator tiers, and weather/FX providers** (§13).
- [ ] Voice: opt-in speak-aloud (default off) **plus** the hands-free loop state machine — partials, ~1.2 s EOU, mic torn down while speaking, auto-resume (§9).
- [ ] Acknowledge the recorded divergences (Nano has no on-device tools; stateless Claude session; raw-title flight regex) so neither side "fixes" them unilaterally (§11).

## 7. On-device RAG knowledge base (IRIS grounding)

- **What:** a `kb_chunks` table (Room on Android) of pre-embedded chunks. Two kinds: `PUBLIC` general-travel knowledge (visa/entry, baggage, packing, loyalty, etiquette…) shipped in the app, and `PERSONAL` chunks indexed on-device from the user's own trips/expenses.
- **Artifact:** a pre-built SQLite DB bundled in assets (`iris_kb_v<n>.db`) + a `manifest.json` carrying `kb_version`, `embedder_id`, `embedding_dim`, `normalize`, `chunk_count`. Built by `tools/iris-kb` (a Python ingest→chunk→embed→eval pipeline; **RAG, not fine-tuning**). The app seeds once per `kb_version` (flag `kb_seeded_v<n>`, mirroring `trips_seeded_v2`).
- **Embedder-parity constraint (critical):** documents are embedded offline with the **same model** the device runs (`MediaPipeTextEmbedder`, model id `use-v1`). The app refuses a KB whose manifest `embedder_id`/`dim` don't match, and drops any stored row from a stale model. **iOS must embed with the identical model + dim + normalization**, or retrieval silently fails.
- **`kb_chunks` columns:** `id, text, source, sourceType (KB|USER|TOOL), sensitivity (PUBLIC|PERSONAL), embedding (little-endian float32 BLOB), dim, modelId, metadata (JSON), updatedAt`.
- **Privacy rule (updated 2026-07-17):** `PUBLIC` chunks are shippable/regenerable; `PERSONAL` chunks are device-local and never synced. **Both** kinds may now ground **both** AI tiers — Claude included — per the redefined invariant in §4 (the hard line is third-party *data* APIs, which never see personal/preference data). The old "PERSONAL never to a cloud LLM" gate is removed on Android.

## 8. On-device learning stack & IRIS memory (shared contract — replaces `UserMemory`)

> ⚠️ **Superseded (2026-07-17):** the old `UserMemory` (key `user_memory`, 45-day half-life) is
> **deleted** on Android. The spec-shaped stack below is the cross-platform contract; keys and
> enum wire values are byte-identical on both platforms.

- **Signals:** `core.intelligence.TravelSignal` `{id, kind, value, attributes, timestamp, source}` with **8 camelCase wire kinds**: `seatChosen`, `flightFlown`, `receiptScanned`, `expenseLogged`, `loyaltyAdded`, `tripCompleted`, `placeVisited`, `suggestionFeedback`. Stored under key **`jetsetter_travel_signals`**, JSON, **FIFO-capped at 2000**.
- **Engine:** `core.intelligence.TravelProfileEngine` — pure, explainable, **no trained model**. Recency weight `0.5^(ageDays / 365)` (**365-day half-life**); top-5 `WeightedValue{value, weight, count}` rankings (airlines/hotel brands/cities); seat parsing (columns A/F/K/L window, C/D/G/H aisle, B/E middle; rows 1–10/11–25/26+ zones; recency-weighted mode with confidence = dominant share); cabin = mode of `cabinHint`; spend stats per category+currency (mileage excluded); trip rhythm (mean duration/gap, peak months). Derived `TravelProfileData.summaryForPrompt()` returns `""` when empty.
- **Consent (silent no-op when off):** `UserPreferences` flags `learningEnabled` (master), `learnFromReceipts` (gates `receiptScanned`/`expenseLogged`), `learnFromCheckIns` (gates `seatChosen`), `learnFromTrips` (gates `flightFlown`/`tripCompleted`/`placeVisited`); loyalty + feedback are master-only. All default **true**. Profile recomputes on record + consent change; empty when disabled.
- **Persona:** a cached 2–3 sentence traveler persona (Nano → Claude → deterministic template) under key **`jetsetter_travel_persona`**; cleared when learning is off or the profile is empty.
- **IRIS memory (§1.5 of the spec):** `core.intelligence.IrisMemory` — `IrisPreference{id, category, value, createdAt, lastReinforcedAt, confidence}` under key **`iris_memory`** (JSON, ISO-8601). `remember()` creates at **0.7**, reinforces **+0.1**, caps at **1.0**; recall sorts confidence-desc. **8 camelCase categories:** `dietary`, `seating`, `hotelStyle`, `airlinePreference`, `transportation`, `destinations`, `activities`, `general`. Inspect/per-row-delete/**forget-everything** UI + the 4 consent switches live in `feature.irismemory`.
- **Use:** feeds the proactive engine (§12) and the system-prompt dynamic sections for **both** AI tiers (§4). Never sent to third-party data APIs.

## 9. Voice — TTS + hands-free loop

- **Speak-aloud (TTS):** `core.voice.VoiceOutput` (Android `TextToSpeech`). Opt-in via `UserPreferences.ttsEnabled` (**default off**); IRIS speaks a reply on completion, with sentence chunking under the ~4k-char TTS cap. A speaker toggle lives in the IRIS chat header.
- **Hands-free loop (new, 2026-07-17):** `core.voice.VoiceLoopStateMachine` — `IDLE → LISTENING → THINKING → SPEAKING → LISTENING …`; **stop → IDLE from any state**; one listen-retry on recognition error, second consecutive error ends the loop. `VoiceLoopController` owns mic + TTS + audio: **partial transcripts streamed** into the input field, on-device recognition preferred, end-of-utterance = **~1.2 s silence**, the recognizer is **torn down while speaking** and **auto-resumes** on TTS-done, transient-may-duck audio focus, speaker routing + Bluetooth. Toggle in the IRIS chat header; push-to-talk retained.
- **iOS parity:** `AVSpeechSynthesizer` + `SFSpeechRecognizer` with the same state machine, default-off TTS, partials, ~1.2 s EOU, mic-teardown-while-speaking, and auto-resume.

## 10. IRIS tool roster + staged confirmations (shared wire contract)

The **camelCase tool names are the wire contract** — keep them byte-identical. 14 tools
(`core.ai.IrisToolDispatcher`), split by semantics:

- **Read-only (execute immediately):** `getUserTrips(filter: upcoming/past/all)` (≤6 itinerary items each, + packing summary), `getWeather(location: city or IATA)`, `getVisaAndCountryEssentials(country)`, `rememberUserPreference(category, value)`, `getLearnedTravelProfile(aspect?)`, `getDepartureRecommendation(…)`, and `flightActions(action: luggageStatus)`.
- **Immediate navigation:** `openScreen(screen: home/itinerary/iris/expenses/more/checkIn/disruption/flightTracker/documentVault/packingList/groundTransport/currency)` and `trackFlight(flightNumber)` (opens Flight Tracker + triggers the live search). `flightActions(action: notifyLovedOnes, event: takeoff/landing)` is also immediate — it opens a **prefilled SMS composer** (§13), never silent SMS.
- **STAGED (data-changing — confirm before commit):** `logExpense(amount, merchant, currency?=USD, category?)`, `addTrip(destination, startDate, endDate, name?)` (validates end ≥ start), `checkInForFlight(flightNumber? = next upcoming)`, `generatePackingList(tripName? = active/next)`, `submitExpenses(provider: email/expensify/ramp/brex/divvy, tripName?)`.

**Staging semantics (must match exactly):**

- Staged tools perform **no repository writes at dispatch**; they park an `IrisPendingAction` `{id: UUID, kind, summary, commit: suspend () -> String}` in the `ActionRouter` (single `StateFlow` slot). The chat renders a confirmation card (icon + summary + Cancel/Confirm + progress); the commit's returned string is appended to the transcript.
- The staged tool's `tool_result` begins **`"Staged for user confirmation: "`** (+ summary and an instruction to narrate), so the model says *"I've prepared…"*, never *"I've logged…"*, until confirmed. If a required field is missing, the model asks before calling the tool.
- **Single-slot rule:** a second staged tool in the same turn is **rejected** with the fixed `tool_result` string *"Another action is already awaiting the user's confirmation — ask them to confirm or cancel it first."*
- **`IrisPendingAction.Kind` enum (verbatim for cross-platform parity):** `LOG_EXPENSE`, `CHECK_IN`, `ADD_TRIP`, `TRACK_FLIGHT`, `GENERATE_PACKING_LIST`, `SUBMIT_EXPENSES`. `TRACK_FLIGHT` is present for **enum parity with iOS** even though the `trackFlight` tool executes immediately (navigation is not a data change).
- Expense categories enum: `food, transport, accommodation, entertainment, business, shopping, medical, mileage, other` — the on-device categorizer (§14 note) never emits `mileage`.

## 11. Intentional divergences (recorded so neither side "fixes" them)

- **(a) On-device tool calling — Android has none.** The ML Kit GenAI Prompt API cannot call tools, so Android's Nano tier is **chat-only**; when the last user turn plausibly needs a tool (lightweight keyword heuristic, `core.ai.IrisToolIntent`) and Claude is configured, **that turn diverts to Claude** even though Nano is available. iOS FoundationModels **does** run tools on-device. Same observable behavior (tools always work when any live tier is configured), different tier that executes them.
- **(b) Claude session is stateless full-resend (both platforms' remote fallback — documented, not a bug).** Every turn resends the full message array. "Session recreation" (`core.ai.ConversationSession`) = when the **system-prompt hash changes** or history exceeds **~16k chars** (~4k est. tokens): keep the **last 6 turns** verbatim (re-aligned to start with a `user` turn) and prepend one **synthetic user/assistant summary pair** built by deterministic truncation of the dropped turns.
- **(c) Next-flight regex matches RAW titles.** `[A-Z]{2,3}\d{1,4}` (`core.model.TripQueries`) is applied to itinerary item titles **as stored — no whitespace stripping**. Consequence for both platforms: flight itinerary titles must use **unspaced idents** (`DL1423`, not `DL 1423`) or `nextFlight` will not resolve them. Android demo/seed data is normalized accordingly.

## 12. Proactive suggestions — 12 kinds, priority order (shared contract)

`IrisSuggestion{kind, title, body, promptToIris, dismissalKey}`; pure engine
`core.intelligence.IrisSuggestionEngine`, surfaced as Home alerts (tap deep-links into IRIS chat
with `promptToIris`). **Declaration order = priority order** — keep it identical:

1. `checkInWindow` — flight <24 h, not checked in — **never suppressed**
2. `seatPreferenceNudge` — <36 h, learned seat conf ≥0.6 & ≥2 samples — preference nudge
3. `preferredCabinNudge` — <36 h, learned premium cabin — preference nudge
4. `tierAtRisk` — loyalty expiring ≤7 d
5. `rideToAirport` — <12 h, no `uber_booked` flag
6. `rideOnLanding` — arriving ~≤90 min, no flag; body uses the bag estimator (§13)
7. `packingNudge` — 14–28 d out, empty packing list
8. `visaCheck` — 0–7 d, eVisa/visa destination — **never suppressed**
9. `weatherWatch` — 0–3 d — **never suppressed**
10. `dailyBriefing` — active trip, date-keyed `dismissalKey` (re-surfaces daily)
11. `budgetPacingNudge` — category spend ≥1.3× learned average — preference nudge
12. `welcomeHome` — <24 h after trip end

**Suppression:** never-suppressed set = `{checkInWindow, visaCheck, weatherWatch}` (safety/operational — bypass dismissal filtering entirely). **Preference nudges back off permanently after 3 dismissals** of that kind (`suggestionFeedback` signals → `dismissedCount`). Other kinds suppress per `dismissalKey`. Evaluation runs on Home-open and a 15-min timer tick.

## 13. Shared local keys, SMS templates, bag estimator, provider picks

- **Local store keys (`core.data.prefs.PrefKeys` — exact strings, never rename):** `jetsetter_loved_ones`, `jetsetter_travel_signals`, `jetsetter_travel_persona`, `iris_memory`; boolean flags `uber_booked`, `ride_on_landing_booked` (read by the ride nudges in §12).
- **Loved Ones (§3.3 of the spec):** `LovedOne{id, name, phoneNumber, notifyOnTakeoff=true, notifyOnLanding=true}` under `jetsetter_loved_ones`; management UI in `feature.lovedones`; SMS always via the **native composer prefill** (no silent send).
- **SMS templates (verbatim — emoji, em dash, punctuation are the contract; `core.util.SmsTemplates`):**
  - Takeoff: `✈️ Wheels up on {flight} — I'll text you when I land.`
  - Landing: `🛬 Just landed safely in {city}. Talk soon!`
- **Bag-claim estimator (`core.travel.BagClaimEstimator`):** no checked bag → **0**; tier-1 mega-hubs (ATL DFW ORD LAX JFK DEN SFO LAS SEA MIA EWR BOS MCO CLT IAH LHR CDG FRA AMS DXB HND NRT SIN HKG ICN PEK PVG) → **20–35 min**; everywhere else → **12–25 min**; returns `{minMinutes, maxMinutes, expectedMinutes (midpoint), basis, display}`.
- **Provider picks — 📣 proposed shared contract, iOS to adopt or counter-propose:**
  - **Weather = Open-Meteo** (keyless; `core.weather.OpenMeteoWeatherService`) resolved via a static IATA→coordinates table (`core.travel.AirportCoordinates`) — only coordinates leave the device.
  - **FX = frankfurter.dev `v1`** (keyless, ECB provenance; `core.data.remote.fx.FrankfurterApi`) with a last-good cache and static-table fallback.

## 14. Known Phase-1 limitations (Android) & Phase-2 plan

These are **input-plumbing gaps — the engine logic is spec-correct** and unit-tested; the listed
inputs just aren't produced yet, so iOS should not mirror the gaps:

- `tierAtRisk` never fires: `LoyaltyVaultAccount` has no expiration fields, so `loyaltyExpirations` is always empty.
- `hasCheckedBag` is hardcoded `true` on Home, so `rideOnLanding` always assumes a checked bag.
- Check-in `seatChosen`/`flightFlown` signals are silent in practice: the seed-data guard (correctly) skips demo flights, and **all** check-in flights are currently demo seeds.
- `receiptScanned` signals await a receipt-scan UI — the `core.ocr` pipeline (ML Kit Text Recognition + `ReceiptParser`) exists but is unconsumed.

**Phase 2 will add (heads-up for iOS):** a `CloudBackend` seam over Supabase; new tables `travel_signals` / `wallet_items` / `disruption_events` (schema migration `0002`); the spec's `packingLists/{tripId}` collection **mapped onto the existing `trips.packing_list` jsonb column** (doc id = tripId — physical storage unchanged, §2 stays authoritative); an account-deletion edge function; anonymous→email account linking; and Keystore-encrypted session persistence.
