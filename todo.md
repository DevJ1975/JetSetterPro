# TODO — 2026-07-17

Snapshot after Phase 1 iOS-parity landed (`08eed32`) + nav/voice fixes (`7a64958`) on
`feat/phase-a-supabase-backend`. 236 unit tests green; emulator E2E 8/8; Google Maps tiles fixed
(live key swapped into `local.properties`, both builds carry it).

## Jamil — blocked on you (in order)

1. **Regenerate release keystore** (placeholder password) + back up off-machine — recipe in
   `docs/PLAY_RELEASE_HANDOFF.md`. Do this BEFORE any Play upload; current AAB is placeholder-signed.
2. **Play Console (UI-only):** Create app "JetSetter Pro" (`com.trainovate.jetsetterpro`), then
   upload the first AAB (internal testing) — rebuilt AAB needed after step 1 (ask Claude or run
   `./gradlew bundleRelease` with JBR JAVA_HOME). This registers the package; API works after.
   SA invite is already done.
3. **Supabase dashboard:** enable Anonymous Sign-Ins on `bmlbbdyytbdmhizdqwnh` (cloud sync is a
   silent no-op until then); note the "Confirm email" setting state (affects email-link flow).
4. **claude.ai connectors:** re-auth the Supabase MCP to the org that owns `bmlbbdyytbdmhizdqwnh`
   (currently scoped to the Soteria org) — lets Claude apply migration 0002 + deploy the
   delete-account edge function; otherwise those are manual dashboard steps.
5. **`API_ANTHROPIC` in `local.properties`** — IRIS runs demo-mode without it; needed for live
   chat-staging E2E and any build you want live AI in.
6. **Before store promotion:** privacy-policy URL + data-safety declarations (location, camera,
   mic, calendar per manifest); restrict the Maps key (release + debug SHA-1s) after first upload.

## Claude — in flight (Phase 2, code-side; doesn't need the above)

- B4 CloudBackend seam (`trips/expenses/travelSignals/walletItems/packingLists/disruptionEvents`
  + auth surface) with SupabaseBackend impl; repos swap to the seam; contract test vs fake.
- B5 `supabase/migrations/0002_signals_wallet_disruptions.sql` (file only — applying blocked on #3/#4),
  new sync classes, EncryptedSessionManager, email sign-in/link, `delete-account` edge function
  (file only — deploy blocked on #4), account section in More/settings.
- B6 FlightAware live wiring behind isConfigured + DisruptionMonitorWorker (HiltWorker + app
  Configuration.Provider), travel-signal cloud sync hookup, walletItems producer (R8).

## Later (Phase 3 + parked)

- Keyed third-party APIs behind isConfigured: Expedia, Uber, Lyft, Google Vision OCR fallback,
  SITA WorldTracer, rental deep links.
- Receipt-scan UI consuming `core/ocr` (enables RECEIPT_SCANNED learning signals).
- Loyalty expiration fields (unblocks tierAtRisk trigger); real bag flag (rideOnLanding estimator).
- Branch: 3+ unpushed commits on `feat/phase-a-supabase-backend`; `origin/main` diverged onto a
  Firebase/demo line — land via PR when ready, never direct-push main.
