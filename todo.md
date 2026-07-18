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

## Claude — Phase 2 DONE (commit `a4674e1`, 297 tests green)

CloudBackend seam + SupabaseBackend, migration 0002 file, three new sync classes,
EncryptedSessionManager, email sign-in/link, delete-account edge function file, Account settings
section, FlightAware live wiring + DisruptionMonitorWorker, wallet/disruption/signal cloud
write-through. All cloud paths stay silently best-effort until items #3/#4 above are done, then:
apply `supabase/migrations/0002_*.sql` + deploy `supabase/functions/delete-account` (Claude can do
both via MCP once re-authed).

## Claude — next up (Phase 3, needs keys as they arrive)

- Keyed APIs behind isConfigured: Expedia Rapid (+ hotel photos per BOOKING_IMAGERY_PLAN),
  Uber, Lyft, Google Vision OCR fallback, SITA WorldTracer, rental deep links.
- Booking imagery implementation (`core/images/`, airline SVG pack, Pexels, SIPP car assets).
- Receipt-scan UI consuming `core/ocr`.

## Step-by-step: Google Play (do these in order)

### A. Regenerate the release keystore (BEFORE anything touches Play)
1. Open Terminal in the repo root (`/Users/jamiljones/AndroidStudioProjects/JSP`).
2. Move the placeholder keystore aside: `mv jetsetter-release.jks jetsetter-release.jks.old`
3. Generate the new one (you'll be prompted for a NEW strong password — create it in your
   password manager first, then paste it):
   ```bash
   KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"
   "$KEYTOOL" -genkeypair -keystore jetsetter-release.jks -alias jetsetter \
     -keyalg RSA -keysize 4096 -validity 10950 \
     -dname "CN=JetSetter Pro, O=Trainovations, C=US"
   ```
4. Edit `keystore.properties` (repo root, git-ignored) — set `storePassword=` and `keyPassword=`
   to the new password (keep `storeFile=jetsetter-release.jks`, `keyAlias=jetsetter`).
5. Back up BOTH files off this machine: copy `jetsetter-release.jks` + the password to your
   password manager / secure cloud storage. Losing them permanently blocks app updates.
6. Tell Claude "keystore done" → a fresh signed AAB gets built and verified for you.

### B. Create the app in Play Console (one-time, UI-only)
1. Go to https://play.google.com/console and sign in as jamil@trainovations.com.
2. All apps → **Create app**: App name "JetSetter Pro", default language English (US),
   type **App**, **Free**, accept declarations → Create app.
3. You'll land on the app dashboard. Ignore the long setup checklist for now — internal testing
   doesn't need most of it.

### C. Upload the first AAB (registers the package name — one-time, UI-only)
1. Left nav: **Test and release → Testing → Internal testing** → **Create new release**.
2. If prompted about signing, accept **Play App Signing** (default — Google holds the app
   signing key; your keystore becomes the upload key, which is recoverable if ever lost).
3. Upload `app/build/outputs/bundle/release/app-release.aab` (the one built AFTER step A —
   ask Claude if unsure which is current).
4. Release name auto-fills (`1 (0.1.0)`); release notes: anything, e.g. "First internal build."
5. **Next → Save and publish** (or Save, then Review release → Start rollout to Internal testing).
6. Testers tab → create an email list with your own address → copy the opt-in link to install.

### D. Confirm API control (so Claude can do all future releases)
1. Left nav: **Users and permissions** — verify `play-publisher@jetsetter-pro.iam.gserviceaccount.com`
   is listed with app access to JetSetter Pro and "Release to testing tracks" (Admin also fine).
2. Tell Claude "app created" → verification runs:
   `~/.config/jsp/play-api.sh POST applications/com.trainovate.jetsetterpro/edits` should now
   return an edit id instead of 404. From here on, uploads/promotions happen from this session.

### E. Before promoting beyond internal testing (can wait)
1. **Policy → App content**: fill Data safety (declares location, camera, microphone, calendar —
   all in the manifest), Content rating questionnaire, target audience, and a **privacy policy URL**.
2. **Grow → Store presence → Main store listing**: short/full description, screenshots
   (phone min. 2), 512×512 icon, 1024×500 feature graphic.
3. In Google Cloud (console.cloud.google.com, project `jetsetter-pro`): APIs & Services →
   Credentials → "JetSetter Pro Android Maps (EAS)" key → Application restrictions → Android apps →
   add `com.trainovate.jetsetterpro` + the NEW keystore SHA-1 (from step A; Claude can print it)
   and `com.trainovate.jetsetterpro.debug` + the debug SHA-1. Delete the unused "API key 4" /
   "New API key 4" keys while there.

## Later (Phase 3 + parked)

- Keyed third-party APIs behind isConfigured: Expedia, Uber, Lyft, Google Vision OCR fallback,
  SITA WorldTracer, rental deep links.
- Receipt-scan UI consuming `core/ocr` (enables RECEIPT_SCANNED learning signals).
- Loyalty expiration fields (unblocks tierAtRisk trigger); real bag flag (rideOnLanding estimator).
- Branch: 3+ unpushed commits on `feat/phase-a-supabase-backend`; `origin/main` diverged onto a
  Firebase/demo line — land via PR when ready, never direct-push main.

- Booking imagery: see docs/BOOKING_IMAGERY_PLAN.md (researched 2026-07-17) — Rapid photos for hotels, SIPP illustrations + Rapid Car waitlist for cars, bundled airline SVGs + logostream, Pexels destinations. Needs: API_PEXELS key (free), Rapid Car beta signup.
