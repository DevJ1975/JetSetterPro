# Booking Imagery Plan — researched 2026-07-17

Verified against live pricing/ToS pages (4-agent research pass; sources cited inline in the
research transcript). Coil 2.7.0 is already a dependency — every source below is a URL into
`AsyncImage`. Add keys via the existing `Secrets` pipeline.

## Recommendations by surface

| Surface | Primary | Fallback | Avoid |
|---|---|---|---|
| Hotel cards/detail | **Expedia Rapid Content API** — 29M property + 21M room photos, pre-sized 70/350/1000px CDN URLs, hero flag; $0 once Rapid partnership is live; license covers exactly our use (photos on Rapid-booked cards only) | Google Places Photos (New) — $7/1k after 1k free/mo; requires Google logo + author attribution + **no photo caching** | Amadeus (no photos in Self-Service — their own admission); Booking.com (closed managed-affiliate + license conflicts with an Expedia flow) |
| Rental-car offers | **Owned SIPP/ACRISS class illustrations** bundled in APK (what OTAs ship first; legally bulletproof, offline, never misrepresents the class) | Expedia **Rapid Car** supplier images (join beta waitlist under our Rapid relationship; GA ~early 2027). IMAGIN.studio if photorealistic renders needed sooner (quote-based, hotlink-only, no server caching) | Manufacturer press photos (editorial-only); stock car photos on offer cards (trademark + misrepresentation) |
| Airline branding | **Bundled vector logos for top ~75 carriers** keyed by IATA (Wikimedia PD-textlogo SVGs / press kits; nominative fair use — render unaltered, no implied endorsement, add a trademarks notice; works in airplane mode) | Coil fallback chain: logostream API (free 20k/mo) → generated IATA-monogram tile. AirHex if we ever want a paid formal license (only vendor selling one) | Clearbit (dead Dec 2025); Google gstatic icons (private); Kiwi CDN (tolerated but unlicensed — don't ship on it) |
| Hotel/car BRAND logos | **Brandfetch Logo API** — free 500k/mo, no attribution, by domain (small chain→domain map) | logo.dev | — (Rapid property photos mostly obviate hotel chain logos) |
| Destination/trip cards | **Pexels API** — free, commercial, modification allowed, **caching/offline explicitly permitted** (fits offline trip cards); add "Photos provided by Pexels" + photographer credit | Wikimedia lead-images for landmark-accurate headers (per-file CC attribution in an info sheet); Unsplash later (nicer curation but hotlink-only + production approval) | Hotlinking any unofficial CDN; Places Photos for anything cached |

## Cross-cutting rules

- **Caching:** Pexels + bundled assets are the only sources safe to persist. Places Photos and
  IMAGIN.studio prohibit storing bytes — disable long-TTL Coil disk cache per-request for those.
- **Attribution UI:** one reusable `ImageCreditRow` composable (photographer/source/logo)
  satisfies Pexels, Places, and Wikimedia obligations.
- **Trademark posture:** carrier/brand marks rendered unaltered solely to identify the
  carrier/brand of a real itinerary/offer + a trademarks notice in Settings/About.

## Implementation sketch (Phase 3 adjunct, ~small)

1. `core/images/` package: `AirlineLogoProvider` (bundled SVG map → logostream → monogram),
   `DestinationImages` (Pexels client + ModuleStateStore/disk cache), `ImageCreditRow`.
2. New secret: `API_PEXELS` (free key). Brandfetch key optional.
3. Bundle assets: ~75 airline SVGs (curation ~a day), ~8 SIPP-class car illustrations.
4. Hotel photos arrive with the Phase-3 Expedia Rapid wiring (same auth stack; join
   content→availability by property_id). Rapid Car: join the beta waitlist now.
5. Wire into: flight cards (airline logos), trip/briefing cards (Pexels destination),
   booking cards (Rapid photos), rentalcar offers (SIPP assets), luggage/wallet brand rows
   (Brandfetch).
