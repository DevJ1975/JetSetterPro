# Feature Parity — iOS → Android

Master checklist tracking the port of all **34 iOS feature modules** (SwiftUI +
MVVM) to **Android** (Kotlin + Jetpack Compose + Material 3, MVVM + Repository,
Hilt, Room, DataStore, Retrofit/OkHttp/Moshi, Coroutines/Flow).

- **iOS source root:** `JetSetter Pro/Features/<Name>/`
- **Android target:** `com.jetsetter.pro.feature.<name>`
- **Shared services:** `JetSetter Pro/Core/Services/` → `com.jetsetter.pro.core.*`
  (see §3)

> **📲 iOS / Xcode hand-off:** Android landed a batch of demo-mode + trip-day changes that the iOS
> app should converge to (unified seeded persona, presentation-safe IRIS copy, cabin-chime alerts,
> in-app-only navigation, seat map, GPS + weather departure loop). The **converge list and exact
> values live in `IOS_PARITY_NOTES.md` §7** (with a checklist in §6). Start there for the port.

### Legend

- **Status:** ✅ wired (UI + data flowing) · 🟡 stubbed (screen/scaffold exists,
  mock or partial data) · ⬜ not started
- **Priority:** **P0** wire now · **P1** next · **P2** later
- **APIs/services:** the Core service or external API (see `API_REFERENCE.md`)
  the module depends on. "Mock" = `MockDataService` fallback when the secret is
  unset (mirrors iOS `AppSecrets.isConfigured`).

> The iOS `Features/` directory has **35 folders**. Two of them — `PackingList`
> and `Settings` — are sub-surfaces of the 34 logical modules below
> (PackingList lives under **Itinerary**; Settings is the **More/Settings**
> module). The 34 rows map to the 34 logical feature modules in the spec.

---

## 1. Feature modules (34)

| # | Feature | iOS source folder | Description | Android package | Key APIs / services | Priority | Status |
|---|---|---|---|---|---|---|---|
| 1 | **Home** | `Features/Home/` | Dashboard: next trip, live flight, quick actions, alerts | `feature.home` | HomeViewModel aggregates Trip/Flight/Wallet repos; FlightAware | P0 | ✅ |
| 2 | **Itinerary** | `Features/Itinerary/` (+`PackingList/`) | Trip list & detail; add/edit items; packing list; share text | `feature.itinerary` | Room (Trip/ItineraryItem/PackingItem), CalendarService, Supabase sync | P0 | ✅ |
| 3 | **IRIS Chat** | `Features/IRIS/` | AI travel assistant chat + memory + suggestion cards | `feature.iris` | Anthropic Claude (`claude-sonnet-4-6`, streaming SSE) + demo fallback; on-device Nano = Phase C. See `IOS_PARITY_NOTES.md` §4 | P0 | ✅ |
| 4 | **Expenses (ExpenseTracker)** | `Features/ExpenseTracker/` | Expense list, manual entry, receipt scan, mileage (IRS rate) | `feature.expenses` | Room (Expense) + Supabase sync ✅, VisionOCRService → Google Vision (OCR pending) | P0 | 🟡 |
| 5 | **More / Settings** | `Features/More/` (+`Settings/`) | Settings hub, profile, preferences, feature index, sign-out | `feature.more` | DataStore (UserPreferences), FirebaseService (auth) | P0 | 🟡 |
| 6 | **FlightTracker** | `Features/FlightTracker/` | Live flight status by ident; gates, delays, progress | `feature.flighttracker` | FlightAware AeroAPI; APIClient | P1 | ⬜ |
| 7 | **FlightBoard** | `Features/FlightBoard/` | Airport departures/arrivals board | `feature.flightboard` | FlightAware AeroAPI; Mock | P1 | ⬜ |
| 8 | **CheckIn** | `Features/CheckIn/` | Airline check-in flow + state store | `feature.checkin` | CheckInService, CheckInStateStore (Room/DataStore) | P1 | ⬜ |
| 9 | **DepartureOptimizer** | `Features/DepartureOptimizer/` | "Leave by" time using TSA wait + drive time | `feature.departureoptimizer` | DepartureOptimizerService, TSAWaitEstimator, LocationService | P1 | ⬜ |
| 10 | **Disruption** | `Features/Disruption/` | AI disruption dashboard; alternatives; 5-step auto-response | `feature.disruption` | DisruptionMonitorService, DisruptionResponseEngine, Amadeus, Duffel, FlightAware | P1 | ⬜ |
| 11 | **Booking** | `Features/Booking/` | Hotel search + detail (Expedia Rapid) | `feature.booking` | ExpediaAuthService (OAuth2), Expedia Rapid; Mock | P2 | ⬜ |
| 12 | **VisaLookup** | `Features/VisaLookup/` | Entry/visa requirements by destination | `feature.visalookup` | Bundled `EntryRequirement` table (no network) | P1 | ⬜ |
| 13 | **TravelEssentials** | `Features/TravelEssentials/` | Destination essentials (power, currency, emergency #s) | `feature.travelessentials` | Bundled data; WeatherService | P2 | ⬜ |
| 14 | **LocalExperience** | `Features/LocalExperience/` | Curated local activities/experiences | `feature.localexperience` | LocalExperienceViewModel, CityPhotoService; Mock | P2 | ⬜ |
| 15 | **Translator** | `Features/Translator/` | Phrase translation | `feature.translator` | AIService → Claude; (ML Kit on-device optional) | P2 | ⬜ |
| 16 | **TravelJournal** | `Features/TripJournal/` | Trip journal/notes + photos | `feature.traveljournal` | Room, PhotoLibraryService | P2 | ⬜ |
| 17 | **GroundTransport** | `Features/GroundTransport/` | Uber/Lyft price+time estimates to/from airport | `feature.groundtransport` | Uber API, Lyft API (OAuth2), LocationService | P1 | ⬜ |
| 18 | **RentalCar** | `Features/RentalCar/` | Rental car search/detail; deep links | `feature.rentalcar` | RentalCarService; Enterprise/Hertz/National; Mock | P2 | ⬜ |
| 19 | **LuggageTracker** | `Features/LuggageTracker/` | Bag registry; WorldTracer status; AirTag; scan history | `feature.luggagetracker` | SITAWorldTracerService, Room (Bag/BagScanEvent) | P1 | ⬜ |
| 20 | **AirportMap** | `Features/AirportMap/` | Airport terminal map / amenities | `feature.airportmap` | AirportMapViewModel; bundled/Mock | P2 | ⬜ |
| 21 | **TravelWallet** | `Features/TravelWallet/` | Boarding passes, hotel/car/event/insurance docs | `feature.travelwallet` | PassKitService → (Google Wallet), Room (WalletItem), Supabase | P1 | ⬜ |
| 22 | **LoyaltyVault** | `Features/LoyaltyVault/` | Frequent-flyer/hotel accounts + balances | `feature.loyaltyvault` | Room (LoyaltyAccount), program catalog, EncryptedSharedPrefs | P1 | ⬜ |
| 23 | **IdentityVault** | `Features/IdentityVault/` | Secured personal identity info | `feature.identityvault` | VaultCrypto (Tink/AES-GCM), BiometricPrompt | P2 | ⬜ |
| 24 | **DocumentVault** | `Features/DocumentVault/` | Encrypted travel docs (passport/visa/etc.) + expiry alerts | `feature.documentvault` | VaultCrypto, DocumentVaultStore, BiometricPrompt, Supabase | P1 | ⬜ |
| 25 | **CurrencyTracker** | `Features/CurrencyTracker/` | FX rates + currency-aware expense conversion | `feature.currencytracker` | ExchangeRateService; Room cache | P2 | ⬜ |
| 26 | **CarbonTracker** | `Features/Carbon/` | Flight carbon footprint estimate | `feature.carbontracker` | Local calculator; flight data | P2 | ⬜ |
| 27 | **Subscription** | `Features/Subscription/` | Premium paywall + gating | `feature.subscription` | SubscriptionManager → Google Play Billing | P1 | ⬜ |
| 28 | **ExpenseExport** | `Features/ExpenseExport/` | Export expenses (PDF) + provider connections | `feature.expenseexport` | PDFExpenseReportRenderer, Expensify/Ramp/Brex/Divvy | P2 | ⬜ |
| 29 | **Onboarding** | `Features/Onboarding/` | First-run onboarding flow | `feature.onboarding` | DataStore (`hasCompletedOnboarding`) | P1 | ✅ |
| 30 | **Assistant** | `Features/Assistant/` | General assistant surface (distinct from IRIS chat) | `feature.assistant` | AssistantViewModel, AIService → Claude | P2 | ⬜ |
| 31 | **Intelligence** | `Features/Intelligence/` | Proactive travel intelligence cards + history | `feature.intelligence` | TravelIntelligenceViewModel, AIService, multiple repos | P2 | ⬜ |
| 32 | **OfflineKit** | `Features/OfflineKit/` | Offline data bundle / cache management | `feature.offlinekit` | OfflineKitService, Room, WorkManager | P2 | ⬜ |
| 33 | **InFlight** | `Features/InFlight/` | In-flight live tracking view | `feature.inflight` | InFlightTrackingService, FlightAware | P2 | ⬜ |
| 34 | **Translator → (see #15)** *(reserved)* | — | — | — | — | — | — |

> Row 34 is intentionally a placeholder: the spec lists 34 modules but
> `Translator` and `Intelligence`/`OfflineKit`/`InFlight` already cover the tail.
> The canonical 34 are rows **1–33 plus the Core/Services checklist (§3)**, which
> the iOS app treats as a first-class porting unit. Adjust numbering once the
> Android module list is frozen.

### Current status (updated as modules land)

- **✅ wired:** Home, Itinerary, **Onboarding** (first-run gate via DataStore +
  splash hold; `feature.onboarding`)
- **🟡 partially wired / stubbed:**
  - **IRIS Chat** — live **Claude `claude-sonnet-4-6` streaming** (`core.ai.ClaudeClient`) + demo
    fallback; tokens render live into the chat bubble. Needs `API_ANTHROPIC` set (else demo
    replies). On-device Nano tier + dynamic suggestion cards pending (Phases C/F). Keep prompt +
    demo replies identical to iOS (`IOS_PARITY_NOTES.md` §4).
  - **More/Settings** — appearance (theme) + profile persist via DataStore; now also hosts the
    **Features menu** (`ui/navigation/FeatureCatalog.kt`) that routes to every module below.
    Account/auth UI still pending.
  - **Expenses (ExpenseTracker)** — Room-backed ledger with Supabase cross-device sync (anonymous
    Auth, RLS, Realtime), seeded from mock; receipt-scan OCR still pending.
- **🟢 wired mock-first (beta):** all **27 remaining feature modules** are scaffolded end-to-end
  (Screen + ViewModel + UiState + Repository + Models per `feature.<name>`), reachable from
  **More → Features**, and fully interactive with realistic in-memory sample data — built for
  field beta. Flight Tracker, Flight Board, Check-In, Departure Optimizer, Disruption Monitor,
  In-Flight, Ground Transport, Hotel Booking, Rental Cars, Visa & Entry, Travel Essentials,
  Local Experiences, Translator, Airport Guide, Currency, Carbon Footprint, Travel Wallet,
  Loyalty Vault, Document Vault, Identity Vault, Trip Journal, Luggage Tracker, Assistant,
  Travel Intelligence, Offline Kit, Expense Export, Subscription (paywall — **unlocked for beta**).
  Live partner APIs (FlightAware, Expedia, Amadeus/Duffel, Uber/Lyft, WorldTracer, Google
  Vision, Maps, Play Billing, etc.) drop in behind each `Secrets`/key per the mock-first rule.
- **⬜ live-integration TODO:** swap each module's mock repository for its real API as keys
  arrive; add Room persistence + crypto/biometric for the vaults; real Play Billing for Pro.

### Demo mode (investor/field presentations)

- **Demo mode switch** — More → Presentation. Enabling it runs `core.data.demo.DemoSeeder`:
  wipes `ModuleStateStore`, resets Room trips/expenses to `MockData`, fills profile blanks with
  the demo persona, and arms a **scripted disruption push** (~25s later, via the now-implemented
  `DisruptionMonitorWorker` + `core.notifications.JetNotifier`). "Reset demo data" restores the
  pristine dataset any time.
- **Check-In grew an interactive seat map** (`feature.checkin.SeatMap` + `SeatMapSheet`):
  check-in flows through seat selection, issued passes support "Change seat" while the window is
  open, and the chosen seat persists (`PersistedCheckIn.seat`).
- **Disruption Monitor posts a real notification** at its "Traveler notified" timeline step
  (permission requested in-context on the screen; silently skipped when denied). Every alert on
  the disruption channel — delay, gate change, cancellation, rebooking — plays the bundled cabin
  **"fasten seatbelt" chime** (`res/raw/cabin_chime.wav`, wired as the channel sound in
  `JetNotifier`; channel id bumped to `disruption_alerts_v2` because Android locks a channel's
  sound at creation).
- **Demo mode is also flippable from the Home header** — an alpha-only `DEMO` chip
  (`feature.home.HomeScreen`) mirrors the More → Presentation switch (same `DemoSeeder` path,
  same in-context notification-permission request) so a presenter never leaves the dashboard.
- **Departure Optimizer grew the navigation + conditions loop:** a **Navigate** button opens
  **in-app route guidance** (`feature.departureoptimizer.RouteMapSheet`) — the seeded
  Summerlin → LAS route on a real Google Map when `MAPS_API_KEY` is set (code-drawn map
  otherwise, and as the cover until tiles load), with a simulated "Start drive" run that moves
  the position marker while remaining time/distance/ETA count down. The live estimate also rolls
  **weather conditions** (label + °F + risk) shown in the LIVE CONDITIONS card next to traffic
  and TSA.
- **📌 Product rule — every experience stays in-app.** No feature may hand the user off to an
  external app or browser (no `ACTION_VIEW`/maps/dialer/browser intents); maps, navigation,
  booking, and export flows all render inside JetSetter Pro. The only exception is the system
  notification shade, whose taps deep-link back into the app. Audited clean as of this change —
  keep it that way when porting the remaining modules (rental-car "deep links", ground
  transport, expense-provider connections must become in-app surfaces).
- **IRIS gives the departure briefing:** demo-tier replies for "when should I leave / traffic /
  navigate / weather" are rendered from `DepartureoptimizerRepository`'s **live snapshot** (so a
  re-rolled drive/TSA/weather can never contradict her; defaults read leave by 5:19 AM, 34-min
  drive, TSA 22m, clear 74°F), a "When should I leave?" suggestion chip was added, and the live
  Claude tier has a `get_departure_briefing` tool over the same repository.
- **Persona consistency:** seed data across Check-In, Travel Wallet, Disruption, Flight Tracker,
  Home, Departure Optimizer, and the IRIS demo replies all describe the same traveler — DL 1423
  LAS→ATL, First cabin seat 3A, gate C22, the Atlanta board-meeting trip, $1,812.75 expenses,
  a 7:00 AM departure with a 5:19 AM leave-by. Keep new seed data on this persona. IRIS demo
  replies were reworded (presentation-safe, no setup hints) — this deviates from the older iOS
  copy on purpose; converge iOS onto the new wording.

---

## 2. Per-module Android scaffolding convention

Each `feature.<name>` package should contain, mirroring the iOS `*View` /
`*ViewModel` / `*Model` split:

```
feature/<name>/
├── <Name>Screen.kt          // Composable screen  (iOS <Name>View.swift)
├── <Name>ViewModel.kt       // @HiltViewModel, exposes StateFlow<UiState>
├── <Name>UiState.kt         // sealed/data UI state
└── (models live in core.model; repos in core.data — shared, not per-feature)
```

- ViewModels receive **repositories** (not services) via Hilt constructor
  injection. Repositories decide live-API-vs-Room-vs-Mock (see `API_REFERENCE.md`).
- Navigation via a single `NavHost` with type-safe routes
  (`com.jetsetter.pro.navigation`).

---

## 3. Core / Services porting checklist

iOS `Core/Services/*` → Android. Most become **repositories** or **data sources**
behind interfaces so ViewModels stay testable.

| iOS service | Android home | Notes |
|---|---|---|
| `APIClient` / `Endpoints` | `core.network` (Retrofit + OkHttp + Moshi) | See APIClient/error model in `API_REFERENCE.md` |
| `AppSecrets` | `core.secrets.Secrets` | Reads `BuildConfig` fields; empty ⇒ Mock |
| `AIService` | `core.data.repository.IrisRepository` (+ `core.ai.ClaudeClient`, `core.ai.IrisPersona`) | **Anthropic Claude** (`claude-sonnet-4-6`, streaming `/v1/messages`); persona in `IrisPersona`; demo fallback. On-device Nano tier = Phase C. |
| `FirebaseService` | `core.auth.AuthRepository` (Supabase Auth) + `core.sync.SupabaseTripSync` (Postgrest + Realtime) | **Supabase** is the shared data + auth backend (Firestore retired). Anonymous sign-in; trips sync to `public.trips` (RLS on `auth.uid()`). Schema: `supabase/migrations/` + `IOS_PARITY_NOTES.md` §2. |
| `MockDataService` / `DemoSeeder` | `core.data.mock.MockData` | Sample data parity with iOS `*.sample` |
| `VaultCrypto` | `core.crypto.VaultCrypto` | Tink (AES-GCM) + Android Keystore; EncryptedSharedPreferences |
| `VisionOCRService` | `core.ocr.OcrRepository` | Google Vision REST; or ML Kit Text Recognition on-device |
| `PassKitService` | `core.wallet.WalletRepository` | Google Wallet API (replaces Apple PassKit) |
| `NotificationManager` | `core.notifications` | `NotificationManagerCompat` + channels; FCM for push |
| `LocationService` | `core.location` | FusedLocationProviderClient |
| `CalendarService` | `core.calendar` | `CalendarContract` provider |
| `WeatherService` | `core.weather` | Repo; same upstream as iOS |
| `ExchangeRateService` | `core.fx` | Repo + Room cache |
| `SubscriptionManager` | `core.billing` | Google Play Billing Library |
| `ExpediaAuthService` / OAuth services | `core.network.auth` | OkHttp `Authenticator` for OAuth2 client-credentials token caching |
| `SITAWorldTracerService` | `core.network.worldtracer` | Retrofit service |
| `RentalCarService` | `core.network.rentalcar` | Enterprise/Hertz/National + deep links |
| `DepartureOptimizerService` / `TSAWaitEstimator` | `core.departure` | Pure-Kotlin calculators |
| `DisruptionMonitorService` / `DisruptionResponseEngine` | `core.disruption` | WorkManager periodic monitor + response orchestration |
| `InFlightTrackingService` | `core.inflight` | Polling tracker over FlightAware |
| `OfflineKitService` | `core.offline` | Room + WorkManager sync |
| `AudioAlertService` | `core.audio` | `SoundPool` / `Ringtone` |
| `PhotoLibraryService` | `core.media` | Photo Picker (`ActivityResultContracts.PickVisualMedia`) |
| `CityPhotoService` | `core.media.CityPhotoRepository` | Image fetch + Coil cache |
| `WatchConnectivityService` | `core.wearable` *(optional)* | Wear OS Data Layer (defer; low priority) |
| `FlightLiveActivityService` | `core.liveactivity` | Live Activities → ongoing notification + (optional) Live Updates |

---

## 4. Recommended porting roadmap

### Phase 0 — Foundation (in progress)
Theme/design system, `JetTheme`, navigation shell, Hilt graph, Room DB +
DataStore, `core.network` (APIClient + interceptors + error model),
`core.secrets`, `MockData`. Land **Home** and **Itinerary** end-to-end (✅).

### Phase 1 — Core daily-driver loop (P0 finish + early P1)
Complete the three stubs to wired: **IRIS Chat** (Claude streaming),
**Expenses** (Room + Vision OCR + mileage), **More/Settings** (DataStore +
Firebase auth). Then **Onboarding**, **FlightTracker**, **TravelWallet**,
**Subscription** (gating must exist before premium features ship).

### Phase 2 — Trip-day intelligence (P1)
**Disruption** (+ DisruptionMonitor/ResponseEngine, Amadeus, Duffel),
**DepartureOptimizer** (+ TSA/Location), **CheckIn**, **FlightBoard**,
**GroundTransport** (Uber/Lyft), **LuggageTracker** (WorldTracer),
**VisaLookup**, **LoyaltyVault**, **DocumentVault** (+ VaultCrypto + Biometric).

### Phase 3 — Vaults, sustainability & enrichment (P2)
**IdentityVault**, **CurrencyTracker**, **CarbonTracker**, **Booking** (Expedia),
**RentalCar**, **LocalExperience**, **TravelEssentials**, **AirportMap**,
**TravelJournal**, **Translator**, **Assistant**, **Intelligence**,
**ExpenseExport**, **InFlight**, **OfflineKit**.

### Cross-cutting (every phase)
Supabase cross-device sync wired as each model's Room layer lands (Firestore is
retired — see `IOS_PARITY_NOTES.md` §1); offline-first (Room is source of truth,
sync reconciles); secrets→Mock fallback verified per feature; accessibility + RTL
(`AutoMirrored` icons) checked as screens are built.
