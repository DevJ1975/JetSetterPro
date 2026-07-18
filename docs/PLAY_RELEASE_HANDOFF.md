# Play Store Release — Handoff

State as of 2026-07-17. Google-side API access is DONE and verified; app-side release prep is NOT
finished. Contains no secrets — all credentials live in git-ignored files or GCP IAM.

## App identity

| Field | Value |
|---|---|
| applicationId (release) | `com.trainovate.jetsetterpro` |
| namespace | `com.jetsetter.pro` |
| versionCode / versionName | `1` / `0.1.0` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Debug variant | applicationId suffix `.debug` |
| R8/minify | OFF for release (intentional — Moshi/Hilt reflection; enabling later needs keep rules) |
| 16KB page compliance | Fixed (MediaPipe .so, commit ce9fdb2) |

## Google Play Developer API access (DONE, verified)

- GCP project: **`jetsetter-pro`** (number 857695467541). Do NOT use `jetsetter-pro-1784243215`
  (deleted; was stale in gcloud config) or `jetsetterpro-ff501` (old Firebase project).
- `androidpublisher.googleapis.com` is enabled on the project.
- Service account: **`play-publisher@jetsetter-pro.iam.gserviceaccount.com`**.
- Auth is **keyless impersonation** — the Workspace org policy `iam.disableServiceAccountKeyCreation`
  blocks key files. `jamil@trainovations.com` holds `roles/iam.serviceAccountTokenCreator` on the SA.
  Mint a token with:
  ```bash
  gcloud auth print-access-token \
    --impersonate-service-account=play-publisher@jetsetter-pro.iam.gserviceaccount.com \
    --scopes=https://www.googleapis.com/auth/androidpublisher
  ```
- Helper script: **`~/.config/jsp/play-api.sh`** — `play-api.sh token`, or
  `play-api.sh POST applications/com.trainovate.jetsetterpro/edits` etc. (androidpublisher v3).
- Play Console: the SA has been invited under Users and permissions (done by Jamil in the UI).
- Current API status: `404 Package not found` — **expected**, because the app does not exist in
  Play Console yet. The API cannot create an app or accept the first upload; both are Console-UI steps.
- gcloud login is `jamil@trainovations.com`; Workspace session policy expires it periodically.
  Headless re-login: `gcloud auth login --no-launch-browser` fed via a FIFO (user clicks URL,
  pastes verification code back).

## Release signing (NOT DONE — blocker)

- Keystore: repo-root `jetsetter-release.jks`, alias `jetsetter`; credentials in repo-root
  `keystore.properties` (both git-ignored; `.gitignore` covers `*.jks` and `keystore.properties`).
- ⚠️ **The current keystore password is a generated placeholder. It must be regenerated with a
  strong password and backed up off-machine BEFORE the first Play upload** — the first upload
  permanently locks the signing identity (Play App Signing will treat it as the upload key).
- Old (placeholder) keystore SHA-1: `CA:BC:74:69:FC:93:09:7D:7F:85:BE:B3:CF:68:91:C4:C0:E4:CF:92` —
  becomes irrelevant once regenerated.
- `app/build.gradle.kts` behavior: reads `keystore.properties`; if absent the release variant is
  left **UNSIGNED** (no silent debug fallback). `-PallowDebugSigning` opts into debug signing for
  local-only builds.
- Regeneration recipe (keytool from Android Studio JBR):
  ```bash
  KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"
  "$KEYTOOL" -genkeypair -keystore jetsetter-release.jks -alias jetsetter \
    -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=JetSetter Pro, O=Trainovations, C=US"
  # then write storeFile/storePassword/keyAlias/keyPassword into keystore.properties (chmod 600)
  # and back up both files outside the repo (e.g. ~/.config/jsp/keystore-backup/ + password manager).
  ```

## Build

No system Java on PATH — use Android Studio's JBR:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
```
Android SDK: `/Users/jamiljones/Library/Android/sdk`.

## Remaining checklist (in order)

1. **Regenerate keystore** with a strong password; update `keystore.properties`; back up keystore +
   password off-machine. (Was about to run; deferred by Jamil for handoff.)
2. `./gradlew bundleRelease`; verify signature (`jarsigner -verify` or `apksigner`).
3. In Play Console (UI, as jamil@trainovations.com): **Create app** — name "JetSetter Pro",
   App / Free — then upload the first AAB through the Console (internal testing track recommended).
   This registers the package name; only after this does the API see the app.
4. Verify API access end-to-end: `~/.config/jsp/play-api.sh POST applications/com.trainovate.jetsetterpro/edits`
   should return an edit id instead of 404. From here releases/listings/reviews are API-manageable.
5. Store-listing prerequisites the manifest implies: Data safety declarations for FINE/COARSE
   location, camera, microphone (RECORD_AUDIO), calendar read/write; privacy policy URL required.
6. Post-first-upload hardening: restrict the Maps API key (currently unrestricted) to the release
   package + new SHA-1; confirm Play App Signing enrollment (default for new apps).

## Environment gotchas

- Local branch: `feat/phase-a-supabase-backend`. `origin/main` diverged onto a Firebase/demo line —
  land work via PR, never push main directly. Commit ce9fdb2 (DB migration + 16KB fix) is unpushed.
- Secrets (API keys, `SUPABASE_URL`/`SUPABASE_ANON_KEY`, `MAPS_API_KEY`) come from `local.properties`
  or env at build time; missing keys resolve to "" and services fall back to mocks.
- Supabase anon-auth toggle is still OFF in the Supabase project (open item from the backend migration).
