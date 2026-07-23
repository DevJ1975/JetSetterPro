# Google Play publishing API — service account setup

This wires up a **service account** so tooling and CI can talk to the **Google Play Developer API**
(`androidpublisher.googleapis.com`) for **JetSetter Pro** (`com.trainovate.jetsetterpro`) — the
API behind uploading builds, rolling out to tracks, and reading release info without anyone's
personal Play login.

It is a sibling of [Firebase App Distribution](./FIREBASE_DISTRIBUTION.md): that ships *demo* APKs to
testers; **this** is the credential for the real **Google Play** store pipeline (internal testing →
production). The two are independent — you can use either, both, or neither.

Almost all of it is scripted. There is exactly **one** manual step, because Google provides no API
for it: inviting the service account inside the Play Console.

---

## TL;DR

```bash
# In a shell where gcloud is authenticated (see Prerequisites):
./scripts/setup-play-publisher.sh          # enables the API, makes the SA, writes the key
# → then do the one manual step it prints (Play Console → Users and permissions), then:
./scripts/verify-play-publisher.sh         # live API call proving it all works
```

The two scripts are idempotent — safe to re-run.

---

## What you get

| Thing | Value |
|---|---|
| GCP project | `jetsetter-pro-1784243215` |
| API enabled | `androidpublisher.googleapis.com` (Google Play Android Developer API) |
| Service account | `play-publisher@jetsetter-pro-1784243215.iam.gserviceaccount.com` |
| JSON key (kept **outside** the repo) | `~/.config/jsp/play-publisher.json` — dir `700`, file `600` |
| Project IAM roles on the SA | **none** — a Play publisher's authority comes from the Play Console invite, not Cloud IAM (least privilege) |

> The key never enters the repository. It is written to `~/.config/jsp/`, the directory and file are
> locked to your user only, and `.gitignore` additionally blocks common key filenames as a backstop.

---

## Prerequisites

1. **gcloud CLI**, authenticated as a principal that can enable services and create service
   accounts on the project:
   ```bash
   gcloud --version                                   # installed?  https://cloud.google.com/sdk/docs/install
   gcloud auth login                                  # a human with Owner/Editor, or…
   gcloud auth activate-service-account --key-file=bootstrap.json   # …a bootstrap SA
   gcloud config set project jetsetter-pro-1784243215
   ```
   Needed permissions: `serviceusage.services.enable` + `iam.serviceAccounts.create` +
   `iam.serviceAccountKeys.create` (all covered by **Owner**, or **Editor + Service Account Admin**).
2. **Play Console access** to JetSetter Pro with the *Admin (account)* right, or any role that can
   **Invite new users** (needed only for the one manual step).
3. The org policy `iam.disableServiceAccountKeyCreation` must **not** be enforced on the project (it
   blocks JSON key creation). If it is, either have an admin exempt the project or switch to Workload
   Identity Federation — see [Alternatives](#alternative-no-json-key-workload-identity-federation).

> **Why can't this run inside the Claude Code web sandbox?** That environment has no Google Cloud
> credentials (its `gcloud` token is a placeholder), `gcloud` isn't installed, and the installer host
> is blocked by egress policy — so the live GCP mutations can't happen there. Run the scripts on your
> laptop, in Cloud Shell, or in CI where `gcloud` is logged in.

---

## Step by step

### 1–3. Run the setup script

```bash
./scripts/setup-play-publisher.sh
```

It performs — idempotently — the three automatable steps and then prints step 4 (the manual one):

1. **Enables** `androidpublisher.googleapis.com` on the project.
2. **Creates** the `play-publisher` service account (no IAM roles attached).
3. **Creates a JSON key** and writes it to `~/.config/jsp/play-publisher.json`, `chmod 600`
   (dir `700`). It refuses to write anywhere inside a git working tree, and won't overwrite an
   existing key (rotate deliberately instead).

Override any default with env vars, e.g. `JSP_KEY_DIR=/secure/keys ./scripts/setup-play-publisher.sh`
(`JSP_PROJECT_ID`, `JSP_SA_NAME`, `JSP_KEY_DIR`, `JSP_KEY_PATH`).

### 4. Manual step — invite the service account in the Play Console

**There is no Google API for this**, so do it once by hand:

1. Open <https://play.google.com/console> → **Users and permissions** → **Invite new users**.
2. **Email address:** `play-publisher@jetsetter-pro-1784243215.iam.gserviceaccount.com`
   (the setup script prints the exact address in case you customised it).
3. Grant access to **JetSetter Pro** (`com.trainovate.jetsetterpro`) with **least-privilege**
   permissions for what this key will actually do:
   - **Releases → Release to testing tracks** (internal/closed/open testing)
   - **Releases → Release to production** — only if this identity ships production builds
   - **View app information and download bulk reports**

   Prefer scoped permissions over **Admin (all permissions)**: if the key ever leaks, the blast
   radius is limited to release actions, not full account control.
4. **Send invite.** A service account auto-accepts; the grant is usually usable within a few minutes.

### 5. Verify end-to-end

```bash
./scripts/verify-play-publisher.sh
```

This mints an `androidpublisher`-scoped token from the key and makes a **live** `edits.insert` call
against `com.trainovate.jetsetterpro` (a transactional "app edit" that changes nothing until
committed), then deletes the throwaway edit. A green **SUCCESS** means the key, the enabled API, and
the Play Console grant are all live and talking to each other.

---

## Troubleshooting

| Symptom | Meaning | Fix |
|---|---|---|
| `403 Forbidden` from verify | SA authenticates but isn't a publisher for this app **yet** | Do the manual invite (step 4); if just done, wait a few minutes for propagation and re-run |
| `404 Not Found` from verify | No app with this package is visible to the account | Confirm the package name; ensure the app exists in the Play Console (a new app usually needs one uploaded build before the edits API accepts it) |
| `401 Unauthorized` from verify | Token rejected | Key is corrupt/rotated, or system clock is off by > 5 min — rotate the key and check the clock |
| `invalid_grant: account not found` at token exchange | The key doesn't correspond to a live SA | The SA/key was deleted — re-run the setup script |
| Setup: "Failed to create key … disableServiceAccountKeyCreation" | Org policy blocks JSON keys | See [Alternatives](#alternative-no-json-key-workload-identity-federation) |
| Setup: "Cannot access project" | Wrong project or missing permission | `gcloud config set project jetsetter-pro-1784243215`; check your IAM role |

---

## Security & lifecycle

- **The key is a credential.** Anyone with `play-publisher.json` can publish as this identity (within
  the granted Play permissions). Keep it in `~/.config/jsp/` (owner-only) and never commit it.
- **Rotate** by deleting the old key and creating a new one:
  ```bash
  gcloud iam service-accounts keys list  --iam-account=play-publisher@jetsetter-pro-1784243215.iam.gserviceaccount.com
  gcloud iam service-accounts keys delete <KEY_ID> --iam-account=play-publisher@jetsetter-pro-1784243215.iam.gserviceaccount.com
  rm ~/.config/jsp/play-publisher.json && ./scripts/setup-play-publisher.sh
  ```
- **Revoke** entirely by removing the user in Play Console → Users and permissions, and/or deleting
  the service account: `gcloud iam service-accounts delete play-publisher@jetsetter-pro-1784243215.iam.gserviceaccount.com`.

---

## Using the key in CI (next step)

To publish from GitHub Actions, put the key's contents in a repo secret rather than on a runner disk:

```bash
# add the whole JSON as a secret named PLAY_PUBLISHER_SA_JSON
gh secret set PLAY_PUBLISHER_SA_JSON < ~/.config/jsp/play-publisher.json
```

A publish workflow (e.g. `r0adkll/upload-google-play` or Gradle Play Publisher) then writes
`${{ secrets.PLAY_PUBLISHER_SA_JSON }}` to a temp file and points the uploader at it — the same
temp-file-then-delete pattern used by [`firebase-distribution.yml`](../.github/workflows/firebase-distribution.yml).
Building that workflow is out of scope for this credential setup.

---

## Alternative: no JSON key (Workload Identity Federation)

If your org forbids service-account keys, skip step 3 and have CI mint short-lived tokens via
[Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation) instead
(e.g. `google-github-actions/auth` with a workload identity provider). The service account and the
Play Console invite (steps 2 and 4) are identical; only how the token is obtained changes.

---

## Reference — exactly what the setup script runs

For auditing, or to run by hand:

```bash
PROJECT=jetsetter-pro-1784243215
SA=play-publisher@$PROJECT.iam.gserviceaccount.com

gcloud services enable androidpublisher.googleapis.com --project="$PROJECT"

gcloud iam service-accounts create play-publisher --project="$PROJECT" \
  --display-name="Play Publisher (Android Publisher API)"

mkdir -p ~/.config/jsp && chmod 700 ~/.config/jsp
gcloud iam service-accounts keys create ~/.config/jsp/play-publisher.json \
  --iam-account="$SA" --project="$PROJECT"
chmod 600 ~/.config/jsp/play-publisher.json
# → then the manual Play Console invite, then ./scripts/verify-play-publisher.sh
```
