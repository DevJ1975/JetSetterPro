package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import java.time.Instant
import java.time.LocalDate

/**
 * The 12 proactive suggestion kinds (spec §1.9), declared in EXACT priority order — declaration
 * order IS the priority ([IrisSuggestionEngine] sorts by ordinal). The iOS-shared contract:
 * never rename or reorder without changing both platforms.
 *
 * [neverSuppressed] marks safety/operational kinds that always surface regardless of dismissal
 * history; [isPreferenceNudge] marks the learned-preference nudges that back off after 3
 * dismissals (spec: "preference nudges back off after 3 dismissals").
 */
enum class IrisSuggestionKind(
    val neverSuppressed: Boolean = false,
    val isPreferenceNudge: Boolean = false,
) {
    CHECK_IN_WINDOW(neverSuppressed = true),
    SEAT_PREFERENCE_NUDGE(isPreferenceNudge = true),
    PREFERRED_CABIN_NUDGE(isPreferenceNudge = true),
    TIER_AT_RISK,
    RIDE_TO_AIRPORT,
    RIDE_ON_LANDING,
    PACKING_NUDGE,
    VISA_CHECK(neverSuppressed = true),
    WEATHER_WATCH(neverSuppressed = true),
    DAILY_BRIEFING,
    BUDGET_PACING_NUDGE(isPreferenceNudge = true),
    WELCOME_HOME,
}

/**
 * One proactive suggestion (spec §1.9 `IRISSuggestion`). [promptToIris] is the natural-language
 * request sent into IRIS chat when the user taps the card (e.g. "Check me in for flight DL1423");
 * [dismissalKey] is the stable identity used for dismissal tracking — `kind.name` plus a stable
 * qualifier (flight ident / trip id / date), so the same nudge isn't re-shown after dismissal but
 * a new flight or day mints a fresh key.
 */
data class IrisSuggestion(
    val kind: IrisSuggestionKind,
    val title: String,
    val body: String,
    val promptToIris: String,
    val dismissalKey: String,
)

/** A loyalty program's status/points expiration — input to the tierAtRisk trigger. */
data class LoyaltyExpiration(
    val program: String,
    val expiresOn: LocalDate,
)

/**
 * Everything [IrisSuggestionEngine.evaluate] looks at, gathered by the caller (HomeViewModel) so
 * the engine stays pure: explicit clock, no I/O, fully unit-testable.
 */
data class SuggestionInputs(
    val now: Instant,
    val today: LocalDate,
    val trips: List<Trip> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    /** Scheduled departure of the next upcoming flight, when known. */
    val nextFlightDeparture: Instant? = null,
    /** Whether the traveler is already checked in for that next flight. */
    val checkedIn: Boolean = false,
    val loyaltyExpirations: List<LoyaltyExpiration> = emptyList(),
    val profile: TravelProfileData = TravelProfileData(),
    /** `uber_booked` flag (spec §2.5) — suppresses rideToAirport. */
    val uberBooked: Boolean = false,
    /** `ride_on_landing_booked` flag (spec §2.5) — suppresses rideOnLanding. */
    val rideOnLandingBooked: Boolean = false,
    /** Arrival airport of the inbound flight, for the bag-claim estimate. */
    val arrivalIata: String? = null,
    val hasCheckedBag: Boolean = false,
    /** Human visa-requirement string for the imminent trip's destination (e.g. "eVisa"). */
    val visaRequirement: String? = null,
    /** Optional KB enrichment for the visaCheck body (plan R10b). */
    val kbVisaNote: String? = null,
    /** Optional KB enrichment for the weatherWatch body (plan R10b). */
    val kbWeatherNote: String? = null,
    /** Per-kind dismissal counts (key = [IrisSuggestionKind.name]) from suggestion feedback. */
    val dismissedCounts: Map<String, Int> = emptyMap(),
    /** The date the daily briefing was last shown, or null — gates dailyBriefing to once a day. */
    val dailyBriefingShownFor: LocalDate? = null,
)
