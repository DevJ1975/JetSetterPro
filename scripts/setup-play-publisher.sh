#!/usr/bin/env bash
#
# setup-play-publisher.sh — one-time Google Play publishing credentials for JetSetter Pro.
#
# Runs the three steps that CAN be automated, then prints the one manual step that Google
# provides no API for (inviting the service account in the Play Console):
#
#   1. Enable the Google Play Android Developer API (androidpublisher.googleapis.com)
#      on the GCP project.
#   2. Create the `play-publisher` service account (no project IAM roles — its authority
#      comes entirely from the Play Console invite in step 3, so we keep it least-privilege).
#   3. Create a JSON key and write it OUTSIDE the repo, into ~/.config/jsp/, locked down
#      (dir 700, file 600). The key is never committed.
#
# It is idempotent: enabling an already-enabled API is a no-op, an existing service account
# is reused, and an existing key file is preserved (never silently overwritten).
#
# Prerequisites:
#   • gcloud CLI installed and authenticated as a principal that can enable services and
#     create service accounts on the project (Owner, or Editor + Service Account Admin, or
#     the discrete serviceusage.services.enable + iam.serviceAccounts.create permissions).
#       gcloud auth login          # human, or
#       gcloud auth activate-service-account --key-file=bootstrap.json
#   • Permission to create service-account keys (org policy
#     iam.disableServiceAccountKeyCreation must NOT be enforced on the project).
#
# Usage:
#   ./scripts/setup-play-publisher.sh
#
# Overridable via environment (defaults shown):
#   JSP_PROJECT_ID=jetsetter-pro-1784243215
#   JSP_SA_NAME=play-publisher
#   JSP_KEY_DIR=$HOME/.config/jsp
#   JSP_KEY_PATH=$JSP_KEY_DIR/play-publisher.json
#
# See docs/GOOGLE_PLAY_API_SETUP.md for the full walkthrough, the manual Play Console step,
# and verification (scripts/verify-play-publisher.sh).

set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
PROJECT_ID="${JSP_PROJECT_ID:-jetsetter-pro-1784243215}"
SA_NAME="${JSP_SA_NAME:-play-publisher}"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
KEY_DIR="${JSP_KEY_DIR:-$HOME/.config/jsp}"
KEY_PATH="${JSP_KEY_PATH:-$KEY_DIR/play-publisher.json}"
API_SERVICE="androidpublisher.googleapis.com"
API_TITLE="Google Play Android Developer API"

# ── Pretty output (honours NO_COLOR) ─────────────────────────────────────────
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; C=$'\033[36m'; X=$'\033[0m'
else
  B=""; G=""; Y=""; R=""; C=""; X=""
fi
say()  { printf '%s\n' "$*"; }
step() { printf '\n%s▶ %s%s\n' "$B" "$*" "$X"; }
ok()   { printf '%s✓%s %s\n' "$G" "$X" "$*"; }
warn() { printf '%s!%s %s\n' "$Y" "$X" "$*"; }
die()  { printf '%s✗ %s%s\n' "$R" "$*" "$X" >&2; exit 1; }

# ── Preflight ────────────────────────────────────────────────────────────────
step "Preflight checks"

command -v gcloud >/dev/null 2>&1 || die "gcloud CLI not found. Install it: https://cloud.google.com/sdk/docs/install"

ACTIVE_ACCT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null || true)"
[ -n "$ACTIVE_ACCT" ] || die "No active gcloud credentials. Run: gcloud auth login"
ok "Authenticated as ${C}${ACTIVE_ACCT}${X}"

gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1 \
  || die "Cannot access project '$PROJECT_ID' as $ACTIVE_ACCT (wrong project, or missing permission)."
ok "Project ${C}${PROJECT_ID}${X} is reachable"

# Refuse to write the key anywhere inside a git working tree — it must live outside the repo.
mkdir -p "$KEY_DIR"
KEY_DIR_ABS="$(cd "$KEY_DIR" && pwd -P)"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
SELF_REPO="$(git -C "$SELF_DIR" rev-parse --show-toplevel 2>/dev/null || echo "")"
if [ -n "$SELF_REPO" ]; then
  case "$KEY_DIR_ABS/" in
    "$SELF_REPO"/*) die "Key dir '$KEY_DIR_ABS' is inside the repo ($SELF_REPO). Keys must never be committed — point JSP_KEY_DIR outside the repo (default: ~/.config/jsp).";;
  esac
fi
ok "Key destination ${C}${KEY_DIR_ABS}${X} is outside the repo"

# ── Step 1: enable the API ───────────────────────────────────────────────────
step "Step 1/3 — Enable the ${API_TITLE}"
if gcloud services list --enabled --project="$PROJECT_ID" --filter="config.name=$API_SERVICE" --format='value(config.name)' 2>/dev/null | grep -q "$API_SERVICE"; then
  ok "$API_SERVICE already enabled"
else
  gcloud services enable "$API_SERVICE" --project="$PROJECT_ID" \
    || die "Failed to enable $API_SERVICE. Need the serviceusage.services.enable permission."
  ok "Enabled $API_SERVICE"
fi

# ── Step 2: create the service account ───────────────────────────────────────
step "Step 2/3 — Create the '${SA_NAME}' service account"
if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
  ok "Service account already exists: ${C}${SA_EMAIL}${X}"
else
  gcloud iam service-accounts create "$SA_NAME" \
    --project="$PROJECT_ID" \
    --display-name="Play Publisher (Android Publisher API)" \
    --description="Publishes JetSetter Pro to Google Play via the Android Publisher API. Authorized through the Play Console (Users & permissions), not project IAM." \
    || die "Failed to create service account. Need the iam.serviceAccounts.create permission."
  ok "Created ${C}${SA_EMAIL}${X}"
fi
# Intentionally NO project IAM role binding: a Play publisher's authority comes from the
# Play Console invite (step 3 below), not from Google Cloud IAM. Keep it least-privilege.

# ── Step 3: create the JSON key (outside the repo, locked down) ───────────────
step "Step 3/3 — Create and lock down the JSON key"
umask 077
if [ -f "$KEY_PATH" ]; then
  warn "Key already exists: $KEY_PATH — leaving it untouched (not overwriting)."
  warn "To rotate, delete it and re-run, or: gcloud iam service-accounts keys create '$KEY_PATH' --iam-account='$SA_EMAIL'"
else
  gcloud iam service-accounts keys create "$KEY_PATH" \
    --iam-account="$SA_EMAIL" \
    --project="$PROJECT_ID" \
    || die "Failed to create key. If org policy iam.disableServiceAccountKeyCreation is enforced, ask an admin to allow it for this project (or use Workload Identity Federation instead)."
  ok "Wrote key to ${C}${KEY_PATH}${X}"
fi
chmod 700 "$KEY_DIR_ABS"
chmod 600 "$KEY_PATH"
ok "Permissions: dir 700, file 600 (owner-only)"

# ── The one manual step Google provides no API for ───────────────────────────
printf '\n%s────────────────────────────────────────────────────────────────────────%s\n' "$B" "$X"
printf '%sMANUAL STEP (required) — invite the service account in the Play Console%s\n' "$B$Y" "$X"
printf '%s────────────────────────────────────────────────────────────────────────%s\n' "$B" "$X"
cat <<EOF
There is no Google API to grant a service account access to a Play developer
account, so this one step is done by hand, once:

  1. Open the Play Console:  https://play.google.com/console
  2. Left sidebar → Users and permissions → Invite new users.
  3. Email address:
         ${C}${SA_EMAIL}${X}
  4. Grant access to the JetSetter Pro app (com.trainovate.jetsetterpro).
     Recommended least-privilege permissions for CI publishing:
         • Releases → "Release to testing tracks" (and "Release to production"
           only if this identity ships production builds)
         • "View app information and download bulk reports"
     (Or "Admin (all permissions)" if you want a single all-purpose key — less safe.)
  5. Send invite. A service account auto-accepts; access is usually usable within
     a few minutes.

Then verify end-to-end:

     ${C}./scripts/verify-play-publisher.sh${X}
EOF

printf '\n%sDone.%s Service account: %s%s%s   Key: %s%s%s\n' "$G" "$X" "$C" "$SA_EMAIL" "$X" "$C" "$KEY_PATH" "$X"
