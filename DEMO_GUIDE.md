# JetSetter Pro — Investor Demo Guide

How to install the app on any Android phone (no Play Store), put it in **Demo Mode**, and walk an
investor through every feature bug-free. Written for the July 6 meeting; nothing here needs an
API key, a network connection, or an account — the whole demo runs on the bundled dataset.

---

## 1. Get the APK onto a phone (outside the Play Store)

### Build it

Two ways, both from GitHub Actions (no local Android Studio needed):

1. **Demo APK workflow (recommended)** — GitHub → **Actions → Demo APK → Run workflow** (pick the
   branch). When it finishes it:
   - attaches `JetSetterPro-demo-N.apk` to a **`demo-build-N` pre-release** on the Releases page
     (direct download link you can open from the phone's browser), and
   - uploads the same APK as a workflow artifact.
2. **Android CI** — every pull-request build already uploads a `jetsetter-pro-debug-apk` artifact
   (Actions → the run → Artifacts). Artifacts download as a `.zip` containing the APK.

> The APK is the **debug-signed** build: perfect for sideloading and demos, not valid for Play
> Store upload. Android 8.0+ (API 26), any phone or tablet.

### Install it (sideload)

On the demo phone:

1. Get the APK onto the phone — any of:
   - open the **Releases** page in the phone's browser and download the `.apk` directly;
   - email / Google Drive / WhatsApp the file to the phone;
   - `adb install JetSetterPro-demo-N.apk` from a laptop with USB debugging.
2. Tap the downloaded file. Android will ask to allow installs from that app
   (**Settings → "Install unknown apps" → allow** for Chrome/Files/Gmail — whichever you used).
   This prompt appears once per source app.
3. If Play Protect interjects ("app from an unknown developer"), choose **Install anyway** /
   **More details → Install anyway**. Expected for any non-Play build.
4. The app installs as **JetSetter Pro** (`com.trainovate.jetsetterpro.debug`).

### Prep before the meeting (5 minutes)

1. Open the app once; breeze through onboarding.
2. **Turn Demo mode ON** — tap the **DEMO** chip in the Home header (alpha convenience), or use
   **More → Presentation → Demo mode**. Accept the notification permission prompt. Either switch
   resets every module to the curated dataset and arms the scripted disruption alert.
3. Do a full dry run of the script below, then toggle Demo mode off/on (or tap
   **Reset demo data**) so the investor sees a pristine state.
4. Phone hygiene: Do Not Disturb *off* (the disruption push must be visible), brightness up,
   dark theme (default) reads best on a projector.

---

## 2. The demo dataset (one consistent traveler)

Everything describes the same executive traveler, so no screen contradicts another:

| Fact | Value |
|---|---|
| Next flight | **DL 1423 · Las Vegas (LAS) → Atlanta (ATL)**, gate C22, First cabin, **seat 3A** |
| Trip | "Atlanta Board Meeting" — Ritz-Carlton, board dinner at Bacchanalia, packing list |
| Second trip | "Tokyo Product Summit" in September |
| Expenses | **$1,812.75** across 4 items (Delta airfare $1,290 the largest) |
| Disruption story | DL 1423 delayed 7:00 → 8:35 AM (weather hold), 3 rebooking options |
| Wallet | Boarding pass (3A · Zone 1), hotel, rental car, event ticket, travel insurance |

---

## 3. Investor walkthrough script (~10 minutes)

**Beat 0 — the hook (starts automatically).** ~25 seconds after Demo mode is switched on, a real
push notification lands — announced by the cabin **"fasten seatbelt" chime** (every delay or gate
change on the disruption channel plays it): *"DL 1423 delayed 1h 35m — IRIS found 3 rebooking
options."* Start the pitch on the lock screen: this is the product — it watches the trip so the
traveler doesn't. (Have media/notification volume up.)

**Beat 1 — Home.** Open the app. The dashboard shows the next flight, the upcoming Atlanta trip,
the expense rollup, and proactive alerts. Point out this is a live dashboard, not a mock screen.

**Beat 2 — Trip Disruption (the wow).** More → Features → **Trip Disruption**. The 5-step
auto-response timeline animates live: disruption detected → 3 alternatives found → backup hotel
held → traveler notified (that was the push!) → awaiting confirmation. Sort alternatives by
best value / cheapest / earliest, pick one, and tap **Confirm** — one-tap rebooking, and the
decision persists (kill the app, come back: still rebooked).

**Beat 3 — Check-In + seat selection.** Features → **Check-In**. DL 1423's window is open.
Tap **Check in · choose seat** → the interactive seat map opens (First cabin, 2-2). Move from 3A
to another open seat, confirm → boarding pass issued with zone, boarding position, and the new
seat. Show **Change seat** on the issued pass, and the other flights' windows (one opens later,
one closed) to show the logic is real, not canned buttons.

**Beat 4 — Departure Optimizer + in-app navigation.** Features → **Departure Optimizer**. The
leave-by math is transparent: 7:00 AM departure minus drive, parking, TSA, and gate buffers =
**leave by 5:19 AM**, with LIVE CONDITIONS showing traffic, security, *and weather* risk. Tap
**Re-roll** to show the numbers move like a live feed — then tap **Navigate to Harry Reid Intl**:
the route map opens **inside the app** (never a handoff to another app), and **Start drive** plays
a ~20-second simulated run down the Summerlin → LAS route — remaining time and distance count down
live against a steady ETA, just like a real navigator. Works fully offline; with a Maps key it
renders on real Google Map tiles. Ask IRIS *"When should I leave?"* right after a Re-roll: her
answer reads the optimizer's live numbers, so they always agree.

**Beat 5 — IRIS, the AI concierge.** IRIS tab. Tap the *"When should I leave?"* chip → she gives
the departure briefing: leave-by time, drive + traffic, TSA wait, and weather, matching the
optimizer screen. Then *"Is my flight delayed?"* → the exact DL 1423 delay story pointing at
rebooking; *"What should I pack?"*, *"How are my expenses?"* — every answer matches the other
screens. (With an Anthropic key configured she's a live LLM with tool-calling into
trips/expenses/departure plan; the demo replies work offline.)

**Beat 6 — the breadth sweep (pick 3–4).** More → Features:
- **Travel Wallet** — every pass in one place (note the boarding pass matches Check-In).
- **Itinerary** — the full Atlanta trip with packing list; add an item live.
- **Expenses** — the ledger behind the $1,812.75; add an expense live.
- **Flight Tracker / Flight Board**, **Visa & Entry** (real rules engine), **Currency**,
  **Travel Intelligence**, **Carbon** — one line each: 30+ modules, one design system.

**Beat 7 — close.** Toggle **Reset demo data** in front of them: the whole app snaps back to
pristine. Everything they saw ran offline on-device; every module goes live by dropping in an
API key (the mock-first architecture slide).

### If something looks off mid-demo
**More → Presentation → Reset demo data** → relaunch the app. 10 seconds to pristine.

---

## 4. Sending it to someone else's phone

Send the `.apk` file (or the Releases link) by email/Drive/WhatsApp with these two lines:

> 1. Download the file and tap it; allow "install unknown apps" when Android asks.
> 2. Open JetSetter Pro → More → Presentation → turn on **Demo mode**.

That's the entire setup — no account, no keys, no network needed.
