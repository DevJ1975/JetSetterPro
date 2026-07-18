// JetSetter Pro — `delete-account` edge function (plan B5b).
//
// ⚠️  NOT DEPLOYED YET. This file is the repo copy of the function only; deploy it manually via
// the Supabase dashboard (Edge Functions → Deploy new function) or the Supabase MCP/CLI
// (`supabase functions deploy delete-account`) once dashboard access exists — the same manual
// step as applying migrations 0001/0002. Until it is deployed, the Android client's
// SupabaseBackend.deleteAccount() invoke fails fast (404) and deliberately performs NO local
// wipe, surfacing "couldn't reach the account-deletion service" instead.
//
// WHAT IT DOES
//   Clients can never delete their own auth user (that requires the service-role key, which must
//   never ship in an app). So the client calls this function with its own JWT; we validate the
//   JWT against Auth, then admin-delete exactly that user. Deleting the auth user is the whole
//   cleanup: every owned row in trips / expenses / travel_signals / wallet_items /
//   disruption_events references auth.users(id) with `on delete cascade` (migrations 0001/0002),
//   so Postgres wipes the user's data in the same transaction.
//
// ENVIRONMENT
//   SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected automatically into every deployed
//   edge function by the platform — no manual secret setup is required.
//
// CONTRACT (mirrored by the iOS client)
//   POST with `Authorization: Bearer <user JWT>`  →  200 { "deleted": true }
//   Missing/invalid JWT                           →  401 { "error": ... }
//   Admin deletion failure                        →  500 { "error": ... }

import { createClient } from "npm:@supabase/supabase-js@2";

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json(405, { error: "Method not allowed — POST with a Bearer JWT" });
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!jwt) {
    return json(401, { error: "Missing Authorization Bearer JWT" });
  }

  // Service-role admin client. Never expose this key to callers; it exists only inside the
  // function's environment.
  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { autoRefreshToken: false, persistSession: false } },
  );

  // Validate the caller's JWT by resolving it to a user — this is the authorization check:
  // a caller can only ever delete the account the presented JWT belongs to.
  const { data: userData, error: userError } = await admin.auth.getUser(jwt);
  const uid = userData?.user?.id;
  if (userError || !uid) {
    return json(401, { error: "Invalid or expired JWT" });
  }

  // Admin deletion of exactly that user. FK cascades (0001/0002) wipe all owned rows.
  const { error: deleteError } = await admin.auth.admin.deleteUser(uid);
  if (deleteError) {
    return json(500, { error: `Deletion failed: ${deleteError.message}` });
  }

  return json(200, { deleted: true });
});
