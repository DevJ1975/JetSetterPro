# Sharing the app with testers via Firebase App Distribution

This is the gentle way to get JetSetter Pro onto your testers' phones: they get an **email invite**,
tap a link, and install through Google's **App Tester** app — no hunting for a downloaded file, and
you can push a new build to everyone with one click.

> **This does not add Firebase to the app.** App Distribution is purely a delivery pipeline — CI
> uploads the finished APK as a plain file. The app still runs 100% offline on seed data with no
> Firebase SDK, no `google-services.json`, and no keys (Firebase was retired in favor of Supabase).

There's a one-time setup (~15 minutes, mostly clicking in the Firebase console). After that,
shipping a build to testers is: **GitHub → Actions → Firebase App Distribution → Run workflow.**

---

## One-time setup

### 1. Create a Firebase project (free)
1. Go to <https://console.firebase.google.com> and **Add project** (any name, e.g. "JetSetter Pro").
2. You can **disable Google Analytics** — it isn't needed.

### 2. Register the Android app
1. In the project, click the **Android** icon ("Add app").
2. **Android package name:** `com.trainovate.jetsetterpro.debug`
   (This must match exactly — the demo build is the debug variant, whose id has the `.debug`
   suffix. If you later distribute a release build, register a second app with
   `com.trainovate.jetsetterpro`.)
3. App nickname: anything (e.g. "JetSetter Demo").
4. **Skip** the "download google-services.json" and SDK steps — we don't use them.
5. After it's created, open the app's **Settings** (gear icon → Project settings → Your apps) and
   copy the **App ID**. It looks like `1:1234567890:android:abcdef0123456789`.

### 3. Turn on App Distribution and make a tester group
1. Left sidebar → **Release & Monitor → App Distribution → Get started**.
2. Open the **Testers & Groups** tab → **Add group** → name it `testers`
   (the workflow defaults to this alias; use another name and pass it when you run the workflow).
3. Add your testers' email addresses to the group. Each tester gets a one-time invite; on their
   phone they accept it, install the **App Tester** app when prompted, and from then on new builds
   appear there automatically.

### 4. Create a service account for CI
This lets GitHub upload builds without your personal login.
1. Go to <https://console.cloud.google.com/iam-admin/serviceaccounts> and pick your Firebase
   project (same project, it's a linked Google Cloud project).
2. **Create service account** → name it e.g. `github-app-distribution` → **Create and continue**.
3. Grant the role **Firebase App Distribution Admin** → **Done**.
4. Open the new service account → **Keys** tab → **Add key → Create new key → JSON**. A `.json`
   file downloads. Keep it safe; you'll paste its contents into a GitHub secret next (and you can
   delete the local file afterward).

### 5. Add the secrets to GitHub
In the repo: **Settings → Secrets and variables → Actions → New repository secret**:

| Name | Value |
|---|---|
| `FIREBASE_APP_ID` | the App ID from step 2 (e.g. `1:1234567890:android:abcdef0123456789`) |
| `FIREBASE_SERVICE_ACCOUNT` | the **entire contents** of the service-account JSON file from step 4 |

Optional — if you named your tester group something other than `testers`, add a **repository
variable** (the *Variables* tab, not Secrets) `FIREBASE_TESTER_GROUPS` with the group alias(es),
comma-separated.

---

## Shipping a build to testers

1. GitHub → **Actions** → **Firebase App Distribution** → **Run workflow**.
2. (Optional) type release notes and/or a tester-group alias, then **Run workflow**.
3. CI builds the APK and uploads it. Testers in the group get an email / an App Tester notification
   within a minute or two, and install with a tap.

That's it — no APK files to send, and every future build reaches the same testers automatically.

---

## Notes & limits
- **First install on a device** still shows Android's one-time "allow this source" prompt (true of
  any non-Play install); after that, updates are frictionless through App Tester. For a fully
  prompt-free "install from the Play Store" experience, use Play Internal Testing instead.
- The distributed APK is **debug-signed** and runs the full demo offline. It is not a Play release.
- The service-account JSON is written to a temp file during the run and deleted afterward; it never
  lands in the repo. Rotate the key (step 4) if it's ever exposed.
- Nothing here touches `app/build.gradle.kts` or the app code — remove the workflow and the app is
  byte-for-byte unchanged.
