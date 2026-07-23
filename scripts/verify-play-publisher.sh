#!/usr/bin/env bash
#
# verify-play-publisher.sh — prove the Play publishing credentials work end-to-end.
#
# Makes a LIVE Google Play Developer API call against the app, using the service-account
# JSON key created by setup-play-publisher.sh:
#
#   1. Mints an androidpublisher-scoped OAuth token from the key (JWT-bearer grant —
#      python3 + openssl + curl; no gcloud required).
#   2. Calls edits.insert on the app (a transactional "app edit" that changes nothing
#      until committed), which only succeeds if the service account is a recognised
#      publisher for THIS package.
#   3. Deletes the throwaway edit again, so nothing is left behind.
#
# A green result means: key is valid, the API is enabled, and the Play Console invite
# (Users & permissions) has propagated — the whole chain is live.
#
# Usage:
#   ./scripts/verify-play-publisher.sh
#
# Overridable via environment (defaults shown):
#   JSP_KEY_PATH=$HOME/.config/jsp/play-publisher.json
#   JSP_PACKAGE=com.trainovate.jetsetterpro

set -euo pipefail

KEY_PATH="${JSP_KEY_PATH:-${JSP_KEY_DIR:-$HOME/.config/jsp}/play-publisher.json}"
PACKAGE="${JSP_PACKAGE:-com.trainovate.jetsetterpro}"
TOKEN_URI="https://oauth2.googleapis.com/token"
SCOPE="https://www.googleapis.com/auth/androidpublisher"
API_BASE="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}"

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; C=$'\033[36m'; X=$'\033[0m'
else
  B=""; G=""; Y=""; R=""; C=""; X=""
fi
ok()   { printf '%s✓%s %s\n' "$G" "$X" "$*"; }
warn() { printf '%s!%s %s\n' "$Y" "$X" "$*"; }
die()  { printf '%s✗ %s%s\n' "$R" "$*" "$X" >&2; exit 1; }

for bin in python3 openssl curl; do
  command -v "$bin" >/dev/null 2>&1 || die "Required tool '$bin' not found on PATH."
done
[ -f "$KEY_PATH" ] || die "Key not found: $KEY_PATH — run ./scripts/setup-play-publisher.sh first."

TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT
( umask 077; : )   # temp files below are created under a tight umask

printf '%s▶ Verifying Play publisher access for %s%s%s\n' "$B" "$C" "$PACKAGE" "$X"

# ── 1. Build and sign a JWT assertion, then exchange it for an access token ───
umask 077
CLIENT_EMAIL="$(python3 - "$KEY_PATH" "$TMP" <<'PY'
import base64, json, sys, time
key_path, tmp = sys.argv[1], sys.argv[2]
with open(key_path) as f:
    d = json.load(f)
for field in ("client_email", "private_key"):
    if not d.get(field):
        sys.exit(f"Key file is missing '{field}' — is this a service-account JSON key?")
with open(tmp + "/sa_key.pem", "w") as f:
    f.write(d["private_key"])
def b64u(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
header = {"alg": "RS256", "typ": "JWT"}
claims = {
    "iss": d["client_email"],
    "scope": "https://www.googleapis.com/auth/androidpublisher",
    "aud": "https://oauth2.googleapis.com/token",
    "iat": now,
    "exp": now + 3600,
}
signing_input = (
    b64u(json.dumps(header, separators=(",", ":")).encode())
    + "."
    + b64u(json.dumps(claims, separators=(",", ":")).encode())
)
with open(tmp + "/jwt_input", "w") as f:
    f.write(signing_input)
print(d["client_email"])
PY
)" || die "Could not parse the service-account key at $KEY_PATH."
ok "Loaded key for ${C}${CLIENT_EMAIL}${X}"

openssl dgst -sha256 -sign "$TMP/sa_key.pem" -out "$TMP/sig.bin" "$TMP/jwt_input" \
  || die "Failed to sign the JWT assertion with the key's private key."
SIG="$(python3 -c 'import base64,sys; print(base64.urlsafe_b64encode(open(sys.argv[1],"rb").read()).rstrip(b"=").decode())' "$TMP/sig.bin")"
JWT="$(cat "$TMP/jwt_input").${SIG}"

TOKEN_RESP="$(curl -sS --max-time 30 -X POST "$TOKEN_URI" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
  --data-urlencode "assertion=${JWT}" 2>&1)" || die "Network error contacting $TOKEN_URI"
ACCESS_TOKEN="$(printf '%s' "$TOKEN_RESP" | python3 -c 'import json,sys;
try: print(json.load(sys.stdin).get("access_token",""))
except Exception: print("")')"
if [ -z "$ACCESS_TOKEN" ]; then
  printf '%s\n' "$TOKEN_RESP" >&2
  die "Token exchange failed (see above). Common causes: corrupt/rotated key, or > 5 min clock skew."
fi
ok "Minted an androidpublisher-scoped access token"

# ── 2. Live capability check: open a throwaway app edit ──────────────────────
RESP="$(curl -sS --max-time 30 -w $'\n%{http_code}' -X POST "${API_BASE}/edits" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{}' 2>&1)" || die "Network error contacting the Play Developer API."
HTTP_CODE="$(printf '%s' "$RESP" | tail -n1)"
BODY="$(printf '%s' "$RESP" | sed '$d')"

case "$HTTP_CODE" in
  200|201)
    EDIT_ID="$(printf '%s' "$BODY" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
    ok "Play Developer API reachable — opened app edit ${C}${EDIT_ID:-<none>}${X}"
    # 3. Clean up the throwaway edit (never committed, but delete it anyway).
    if [ -n "$EDIT_ID" ]; then
      if curl -sS --max-time 30 -o /dev/null -w '%{http_code}' -X DELETE "${API_BASE}/edits/${EDIT_ID}" \
           -H "Authorization: Bearer ${ACCESS_TOKEN}" 2>/dev/null | grep -qE '^(200|204)$'; then
        ok "Cleaned up the throwaway edit"
      else
        warn "Could not delete edit ${EDIT_ID} (it expires on its own and was never committed)."
      fi
    fi
    printf '\n%s✓ SUCCESS%s — %s%s%s is fully wired up for Play publishing.\n' "$G$B" "$X" "$C" "$PACKAGE" "$X"
    printf '  Service account %s%s%s can act as a publisher for this app.\n' "$C" "$CLIENT_EMAIL" "$X"
    exit 0
    ;;
  401)
    printf '%s\n' "$BODY" >&2
    die "401 Unauthorized — the access token was rejected. Re-check the key (rotate via setup script) and system clock."
    ;;
  403)
    printf '%s\n' "$BODY" >&2
    die "403 Forbidden — the service account is authenticated but not (yet) an authorized publisher for '${PACKAGE}'.
    → Complete the manual step: Play Console → Users and permissions → invite ${CLIENT_EMAIL}
      and grant it access to this app (see docs/GOOGLE_PLAY_API_SETUP.md).
    → If you just invited it, wait a few minutes for the grant to propagate and re-run."
    ;;
  404)
    printf '%s\n' "$BODY" >&2
    die "404 Not Found — no app with package '${PACKAGE}' is visible to this account.
    → Confirm the package name, and that the app exists in the Play Console (a brand-new app must be
      created there — and typically have one build uploaded — before the edits API will accept it)."
    ;;
  *)
    printf '%s\n' "$BODY" >&2
    die "Unexpected HTTP ${HTTP_CODE} from the Play Developer API (body above)."
    ;;
esac
