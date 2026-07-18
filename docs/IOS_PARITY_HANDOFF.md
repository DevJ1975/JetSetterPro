# Android → iOS Parity Handoff — 2026-07-17

Android just landed **Phase 1 of the bidirectional parity spec** on `feat/phase-a-supabase-backend`.
This is the handoff for whoever works the iOS side next (human or agent). The **contract of record
is `docs/IOS_PARITY_NOTES.md`** (§ references below point there) — this file is the ordered
work list + context you'd otherwise have to reverse-engineer.

## What Android now ships (verified: 236 unit tests, emulator E2E 8/8)

- **IRIS system prompt**: the full sectioned persona (IDENTITY/PERSONA/VOICE/CAPABILITIES/ACTIONS/
  MEMORY/PRINCIPLES/FORMAT) in `core/ai/IrisPersona.kt` `BASE_PROMPT` — this supersedes the old
  3-line lockstep rule. Demo replies unchanged (still word-for-word shared). §4.
- **Dynamic prompt sections on BOTH tiers** (on-device and Claude): stored-preferences summary,
  traveler persona, learned-profile summary, live context snapshot. Privacy invariant is now:
  *third-party data APIs* (FlightAware/weather/FX/etc.) never receive profile data; the AI tiers do. §4.
- **14 camelCase tools with staged confirm-before-commit** (single pending slot, exact staging +
  rejection strings, `IRISPendingAction` kinds incl. `trackFlight` for enum parity). §10.
- **Learning stack**: `TravelSignal` (8 camelCase wire kinds) → 365-day-half-life engine (top-5
  weighted rankings, seat column/zone parsing, spend by category+currency excl. mileage, trip
  rhythm) → consent-gated store (master + 3 per-source flags, FIFO 2000, silent no-op), persona
  generation, suggestion-feedback counting. Keys `jetsetter_travel_signals` / `jetsetter_travel_persona`. §8.
- **IRIS memory**: `iris_memory` key, 8 categories, confidence 0.7 / +0.1 / cap 1.0, inspect +
  forget-everything UI, consent switches. §8.
- **12-kind proactive engine** in priority order; never-suppressed = checkInWindow/visaCheck/
  weatherWatch; preference nudges back off after 3 dismissals; suggestions deep-link into IRIS
  with `promptToIRIS`. §12.
- **Hands-free voice loop** (idle→listening→thinking→speaking→listening, 1.2s EOU, mic teardown
  while speaking, audio focus + BT). §9.
- **Supporting**: Loved Ones (`jetsetter_loved_ones` + verbatim SMS templates), bag estimator
  (0 / hub 20–35 / else 12–25), Nano expense categorizer (never mileage, null when unavailable),
  ML Kit receipt OCR pipeline (UI pending), flags `uber_booked` / `ride_on_landing_booked`. §13.
- **Providers chosen (proposal — adopt or counter-propose)**: weather = Open-Meteo (keyless),
  FX = frankfurter.dev v1 (keyless, ECB). §13.
- **In flight (Phase 2, Android)**: `CloudBackend` seam over Supabase; migration
  `0002_signals_wallet_disruptions.sql` (travel_signals / wallet_items / disruption_events —
  file exists, NOT yet applied to the live project); packingLists intentionally maps onto
  `trips.packing_list` jsonb (no separate table); encrypted session storage; email link;
  `delete-account` edge function. §14 + `supabase/migrations/`.

## iOS ordered work list

1. **Point iOS at the shared Supabase project** (`bmlbbdyytbdmhizdqwnh`) — same URL + anon key as
   Android's `local.properties`; schema v1 per §2 (snake_case columns, camelCase jsonb element
   keys, matching document IDs). Do NOT create a second project. Anonymous-first auth (§3).
   NOTE: Anonymous Sign-Ins is still OFF in the dashboard — enable once, either platform.
2. **Adopt the full persona text verbatim** from `IrisPersona.BASE_PROMPT` (or confirm iOS already
   ships identical text — Android treated the pasted iOS spec as source of truth; if iOS's actual
   text differs, reconcile in §4 and the winning text goes in both).
3. **Mirror the tool roster + staging contract** (§10): names, args, staged-vs-immediate split,
   single-slot pending action, "Staged for user confirmation:" / rejection strings, end≥start
   validation, category enum.
4. **Mirror the learning/memory contracts** (§8): exact keys, math constants, consent mapping,
   FIFO cap, 8+8 enums with camelCase wire names.
5. **Mirror the 12 proactive kinds** (§12) with windows/priority/suppression semantics.
6. **Confirm or counter the provider picks** (Open-Meteo / frankfurter.dev) so both platforms
   fetch the same data (§13).
7. **When Android's Phase 2 lands**: apply migration 0002 (once, either platform), mirror the new
   collections, the packingLists-on-jsonb mapping, and the delete-account edge function flow.

## Recorded divergences — do NOT "fix" these on one side only (§11)

- **On-device tools**: iOS FoundationModels does tool calling on-device; Android's Gemini Nano
  tier is chat-only — tool-intent turns divert to Claude (`IrisToolIntent` heuristic).
- **Session**: both platforms resend full history to Claude; recreation = prompt-hash change or
  >16k chars → last-6-turns + synthetic summary pair (Android implementation; iOS should match
  the observable behavior).
- **Next-flight regex** `[A-Z]{2,3}\d{1,4}` matches RAW itinerary titles — titles must use
  unspaced idents ("DL1423" not "DL 1423").

## Known Android limitations (input plumbing — engine logic is correct; don't mirror these) (§14)

tierAtRisk never fires (no loyalty expiration field yet); `hasCheckedBag` hardcoded true;
check-in signals silent while check-in data is demo-seeded; RECEIPT_SCANNED pending a receipt-scan UI.

## Paste-ready prompt for an iOS session

> Android has completed Phase 1 of the JetSetter Pro parity spec. Read
> `docs/IOS_PARITY_NOTES.md` (the shared contract — §4, §8–§14 are new) and
> `docs/IOS_PARITY_HANDOFF.md`, then bring the iOS app to parity following the handoff's
> "iOS ordered work list". Contracts are byte-exact unless §11 records a divergence. Update
> IOS_PARITY_NOTES.md in the same change for anything you alter, and tick §6's checklist.
