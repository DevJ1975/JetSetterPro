# iOS ⇄ Android Parity Notes

> **Audience: the iOS agent.** This file records decisions made on the **Android** port that
> the **iOS** app must match (or consciously diverge from) so the two stay identical in **look
> and function**. When you change a shared contract on iOS, update this file and the Android
> agent will mirror it. Android source of truth for these decisions: `AGENT_GUIDE.md`.

Last updated by the Android port: IRIS moved to Firebase AI Logic; Firestore + Auth wired.

---

## 1. Firebase project & app registrations

- **Shared project:** `jetsetter-pro` (project number `857695467541`). Both platforms use the **same** project, so they share Firestore, Auth users, and AI Logic.
- **Android app ids:** `com.trainovate.jetsetterpro` (release) and `com.trainovate.jetsetterpro.debug` (debug — separate Firebase Android app, so debug builds don't pollute prod).
- **iOS action:** ensure the iOS app is registered in the **same** `jetsetter-pro` project with its own bundle id and `GoogleService-Info.plist`. Do **not** create a second Firebase project — cross-device sync depends on one project.

## 2. Firestore schema — THE cross-device contract (must match exactly)

Trips are mirrored to:

```
users/{uid}/trips/{tripId}
```

Document fields (Android `core.sync.TripSyncRepository`):

| Field | Type | Notes |
|---|---|---|
| `id` | string | == `tripId` (the doc id) |
| `name` | string | |
| `destination` | string | |
| `startDate` | string | ISO-8601 date, e.g. `2026-07-14` |
| `endDate` | string | ISO-8601 date |
| `items` | **string (JSON)** | JSON-encoded array of itinerary items (see below) |
| `packingList` | **string (JSON)** | JSON-encoded array of packing items |

> ⚠️ **`items` and `packingList` are stored as JSON *strings*, not native Firestore arrays** — this mirrors how Android Room stores them. iOS must encode/decode the same way (e.g. `JSONEncoder`/`JSONDecoder` into a `String` field) or the two clients won't read each other's trips.

**`items[]` element** (`ItineraryItem`): `id` (string), `title` (string), `type` (string enum — one of `FLIGHT`, `HOTEL`, `ACTIVITY`, `TRANSPORT`, `RESTAURANT`), `startDate` (ISO-8601 timestamp string), `endDate` (string?, optional), `location` (string?, optional), `notes` (string?, optional).

**`packingList[]` element** (`PackingItem`): `id` (string), `name` (string), `isPacked` (bool).

Field names are **camelCase** (`startDate`, `endDate`, `packingList`, `isPacked`); the `type` enum uses **UPPERCASE** case names. Keep iOS `CodingKeys` aligned.

> **Candidate to revisit (both platforms together):** native nested Firestore maps/arrays would be more idiomatic than JSON-in-a-string. If we switch, switch both clients at once and bump a schema version.

## 3. Auth

- Android signs in **anonymously** on first run (`core.auth.AuthRepository.ensureSignedIn()`) so sync works without forcing a login; an anonymous account can later be upgraded to email/Google.
- **iOS action:** use the same anonymous-first model so the `uid` namespace matches and an upgraded account links rather than forks.

## 4. IRIS assistant — provider divergence (functional parity, different backend)

- **Android:** IRIS runs on **Firebase AI Logic (Gemini)**, `googleAI()` backend (free tier), model `gemini-2.5-flash`, with a canned demo fallback. (Anthropic Claude is no longer used on Android.)
- **iOS:** still Apple Intelligence → Claude → demo.
- **Parity rule:** keep the **system prompt, tone, and the demo/canned replies word-for-word identical** across platforms so IRIS *behaves* the same. The Android system prompt + demo replies live in `core.di.FirebaseModule` and `core.data.repository.IrisRepository`. If we want true provider parity (same model both sides), that's a product decision — record it here first.
- **Roles:** Gemini uses `user` / `model`; history must start with a `user` turn (the opening greeting is dropped before sending). iOS's provider has its own role names — just keep the conversation content identical.

## 5. Design system (already aligned — keep it that way)

- Android tokens (`ui/theme/`) are a faithful port of iOS `JetsetterTheme`: same colors (deep navy + sky-blue accent), spacing scale (4/8/16/24/32), card radius 18dp / padding 16dp.
- **Naming difference to know:** the iOS `GoldTag` is named **`AccentTag`** on Android (the historical "gold" naming was dropped; it renders blue on both). Same component, different name — no visual difference intended.
- The app is **100% icon-drawn** (no image assets) on both platforms; Android maps SF Symbols → Material Symbols per `docs/SF_SYMBOL_MAP.md`. Keep any new iconography in sync via that map.

## 6. iOS agent checklist (to reach parity with current Android state)

- [ ] iOS app registered in `jetsetter-pro` (same project) with `GoogleService-Info.plist`.
- [ ] Firestore read/write uses `users/{uid}/trips/{tripId}` with the **exact** field shapes in §2 (incl. JSON-string `items`/`packingList`).
- [ ] Anonymous sign-in on first launch (§3).
- [ ] IRIS system prompt + demo replies match Android verbatim (§4).
- [ ] Confirm `AccentTag`/`GoldTag` and other renamed components are visually identical (§5).
