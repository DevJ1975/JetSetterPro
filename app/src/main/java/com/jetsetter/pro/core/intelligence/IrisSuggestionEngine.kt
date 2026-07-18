package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.TripQueries
import com.jetsetter.pro.core.travel.BagClaimEstimator
import com.jetsetter.pro.core.util.IsoDates
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The pure proactive-suggestion engine (spec §1.9, plan A5): evaluates all 12 trigger windows over
 * a [SuggestionInputs] snapshot and returns the firing [IrisSuggestion]s in priority order
 * (= [IrisSuggestionKind] ordinal). No clocks, no I/O — the caller supplies now/today and every
 * data slice, so each window is unit-testable at its boundary.
 *
 * Suppression happens INSIDE evaluate: preference nudges ([IrisSuggestionKind.isPreferenceNudge])
 * are dropped once their kind was dismissed [DISMISSAL_BACKOFF] times; never-suppressed
 * safety/operational kinds are always emitted regardless of dismissal counts. Everything else is
 * left to the caller's per-key dismissal handling (via [IrisSuggestion.dismissalKey]).
 */
object IrisSuggestionEngine {

    /** Preference nudges back off after this many dismissals (spec §1.9). */
    const val DISMISSAL_BACKOFF = 3

    /** Cabin values counted as premium for the preferredCabinNudge. */
    internal val PREMIUM_CABINS = listOf("business", "first", "premium")

    /** All firing suggestions for [inputs], suppression applied, sorted by kind priority. */
    fun evaluate(inputs: SuggestionInputs): List<IrisSuggestion> =
        listOfNotNull(
            checkInWindow(inputs),
            seatPreferenceNudge(inputs),
            preferredCabinNudge(inputs),
            tierAtRisk(inputs),
            rideToAirport(inputs),
            rideOnLanding(inputs),
            packingNudge(inputs),
            visaCheck(inputs),
            weatherWatch(inputs),
            dailyBriefing(inputs),
            budgetPacingNudge(inputs),
            welcomeHome(inputs),
        )
            .filter { suggestion ->
                suggestion.kind.neverSuppressed ||
                    !(
                        suggestion.kind.isPreferenceNudge &&
                            (inputs.dismissedCounts[suggestion.kind.name] ?: 0) >= DISMISSAL_BACKOFF
                        )
            }
            .sortedBy { it.kind.ordinal }

    // ── Flight-window triggers ──────────────────────────────────────────────────────────────────

    /** <24h to departure && not checked in. Never suppressed. */
    private fun checkInWindow(inputs: SuggestionInputs): IrisSuggestion? {
        if (inputs.checkedIn) return null
        val until = untilDeparture(inputs, maxHours = 24) ?: return null
        val ident = nextFlightIdent(inputs)
        return IrisSuggestion(
            kind = IrisSuggestionKind.CHECK_IN_WINDOW,
            title = "Check-in is open",
            body = "Your flight${ident.spaced()} departs in ${eta(until)} — check in now to lock in your seat.",
            promptToIris = ident?.let { "Check me in for flight $it" } ?: "Check me in for my next flight",
            dismissalKey = key(IrisSuggestionKind.CHECK_IN_WINDOW, ident ?: inputs.nextFlightDeparture.toString()),
        )
    }

    /** <36h && learned seat with confidence ≥0.6 over ≥2 samples. */
    private fun seatPreferenceNudge(inputs: SuggestionInputs): IrisSuggestion? {
        untilDeparture(inputs, maxHours = 36) ?: return null
        val seat = inputs.profile.typicalSeat ?: return null
        if (seat.confidence < 0.6 || seat.sampleSize < 2) return null
        val ident = nextFlightIdent(inputs)
        return IrisSuggestion(
            kind = IrisSuggestionKind.SEAT_PREFERENCE_NUDGE,
            title = "Your usual ${seat.position} seat",
            body = "You usually pick a ${seat.position} seat in the ${seat.zone} of the cabin — " +
                "worth grabbing one on this flight before they're gone.",
            promptToIris = "Help me get a ${seat.position} seat on" +
                (ident?.let { " flight $it" } ?: " my next flight"),
            dismissalKey = key(IrisSuggestionKind.SEAT_PREFERENCE_NUDGE, ident ?: inputs.nextFlightDeparture.toString()),
        )
    }

    /** <36h && learned cabin is premium (business/first/premium). */
    private fun preferredCabinNudge(inputs: SuggestionInputs): IrisSuggestion? {
        untilDeparture(inputs, maxHours = 36) ?: return null
        val cabin = inputs.profile.preferredCabin ?: return null
        if (PREMIUM_CABINS.none { cabin.contains(it, ignoreCase = true) }) return null
        val ident = nextFlightIdent(inputs)
        return IrisSuggestion(
            kind = IrisSuggestionKind.PREFERRED_CABIN_NUDGE,
            title = "Upgrade to ${cabin.lowercase(Locale.US)}?",
            body = "You usually fly ${cabin.lowercase(Locale.US)} — check upgrade availability before departure.",
            promptToIris = "Check upgrade options for" + (ident?.let { " flight $it" } ?: " my next flight"),
            dismissalKey = key(IrisSuggestionKind.PREFERRED_CABIN_NUDGE, ident ?: inputs.nextFlightDeparture.toString()),
        )
    }

    /** <12h to departure && no ride booked. */
    private fun rideToAirport(inputs: SuggestionInputs): IrisSuggestion? {
        if (inputs.uberBooked) return null
        val until = untilDeparture(inputs, maxHours = 12) ?: return null
        val ident = nextFlightIdent(inputs)
        return IrisSuggestion(
            kind = IrisSuggestionKind.RIDE_TO_AIRPORT,
            title = "Ride to the airport",
            body = "Your flight${ident.spaced()} leaves in ${eta(until)} and no ride is booked yet.",
            promptToIris = "Book me a ride to the airport",
            dismissalKey = key(IrisSuggestionKind.RIDE_TO_AIRPORT, ident ?: inputs.nextFlightDeparture.toString()),
        )
    }

    /** A flight arriving within 90 minutes && no landing ride booked; body carries the bag estimate. */
    private fun rideOnLanding(inputs: SuggestionInputs): IrisSuggestion? {
        if (inputs.rideOnLandingBooked) return null
        val arriving = inputs.trips
            .asSequence()
            .flatMap { it.items.asSequence() }
            .mapNotNull { item ->
                val ident = TripQueries.FLIGHT_IDENT_REGEX.find(item.title)?.value ?: return@mapNotNull null
                val arrival = item.endDate?.let(IsoDates::parseDateTime) ?: return@mapNotNull null
                ident to arrival
            }
            .filter { (_, arrival) ->
                arrival.isAfter(inputs.now) &&
                    Duration.between(inputs.now, arrival) <= Duration.ofMinutes(90)
            }
            .minByOrNull { it.second }
            ?: return null
        val bagLine = inputs.arrivalIata
            ?.let { BagClaimEstimator.estimate(it, inputs.hasCheckedBag).display }
        return IrisSuggestion(
            kind = IrisSuggestionKind.RIDE_ON_LANDING,
            title = "Ride on landing",
            body = listOfNotNull(
                "Landing soon at ${inputs.arrivalIata ?: "your destination"} — want a ride lined up?",
                bagLine,
            ).joinToString(" "),
            promptToIris = "Book me a ride from the airport when I land",
            dismissalKey = key(IrisSuggestionKind.RIDE_ON_LANDING, arriving.first),
        )
    }

    // ── Trip-window triggers ────────────────────────────────────────────────────────────────────

    /** Loyalty status expiring within 7 days. */
    private fun tierAtRisk(inputs: SuggestionInputs): IrisSuggestion? {
        val (expiration, days) = inputs.loyaltyExpirations
            .mapNotNull { e ->
                val days = ChronoUnit.DAYS.between(inputs.today, e.expiresOn)
                if (days in 0..7) e to days else null
            }
            .minByOrNull { it.second }
            ?: return null
        return IrisSuggestion(
            kind = IrisSuggestionKind.TIER_AT_RISK,
            title = "${expiration.program} status at risk",
            body = "Your ${expiration.program} status expires in ${days.days()} — " +
                "a qualifying activity could keep it alive.",
            promptToIris = "How can I keep my ${expiration.program} status from expiring?",
            dismissalKey = key(IrisSuggestionKind.TIER_AT_RISK, expiration.program),
        )
    }

    /** A trip starting in 14–28 days whose packing list is still empty. */
    private fun packingNudge(inputs: SuggestionInputs): IrisSuggestion? {
        val (trip, days) = tripStartingIn(inputs, 14L..28L) { it.packingList.isEmpty() } ?: return null
        return IrisSuggestion(
            kind = IrisSuggestionKind.PACKING_NUDGE,
            title = "Start packing for ${trip.name}",
            body = "${trip.name} starts in ${days.days()} and its packing list is still empty.",
            promptToIris = "Generate a packing list for ${trip.name}",
            dismissalKey = key(IrisSuggestionKind.PACKING_NUDGE, trip.id),
        )
    }

    /** A trip starting within 7 days to a destination whose entry rules require a visa/eVisa. */
    private fun visaCheck(inputs: SuggestionInputs): IrisSuggestion? {
        val requirement = inputs.visaRequirement ?: return null
        if (!requiresVisa(requirement)) return null
        val (trip, days) = tripStartingIn(inputs, 0L..7L) ?: return null
        val body = buildString {
            append("${trip.destination} entry rules: $requirement. ")
            append("Your trip starts in ${days.days()} — make sure your paperwork is in order.")
            inputs.kbVisaNote?.let { append(" $it") }
        }
        return IrisSuggestion(
            kind = IrisSuggestionKind.VISA_CHECK,
            title = "Visa check for ${trip.destination}",
            body = body,
            promptToIris = "What are the visa requirements for ${trip.destination}?",
            dismissalKey = key(IrisSuggestionKind.VISA_CHECK, trip.id),
        )
    }

    /** A trip starting within 3 days — surface the destination forecast. */
    private fun weatherWatch(inputs: SuggestionInputs): IrisSuggestion? {
        val (trip, days) = tripStartingIn(inputs, 0L..3L) ?: return null
        val body = buildString {
            append("${trip.name} starts in ${days.days()} — check the ${trip.destination} forecast before you pack.")
            inputs.kbWeatherNote?.let { append(" $it") }
        }
        return IrisSuggestion(
            kind = IrisSuggestionKind.WEATHER_WATCH,
            title = "Weather for ${trip.destination}",
            body = body,
            promptToIris = "What's the weather forecast for ${trip.destination}?",
            dismissalKey = key(IrisSuggestionKind.WEATHER_WATCH, trip.id),
        )
    }

    /** Once per day while a trip is ongoing; the dismissal key embeds the date. */
    private fun dailyBriefing(inputs: SuggestionInputs): IrisSuggestion? {
        val trip = ongoingTrip(inputs) ?: return null
        if (inputs.dailyBriefingShownFor == inputs.today) return null
        return IrisSuggestion(
            kind = IrisSuggestionKind.DAILY_BRIEFING,
            title = "Today's briefing",
            body = "Your daily briefing for ${trip.name} is ready — itinerary, weather, and spend at a glance.",
            promptToIris = "Give me today's travel briefing",
            dismissalKey = key(IrisSuggestionKind.DAILY_BRIEFING, inputs.today.toString()),
        )
    }

    /** Current-trip spend in some category+currency ≥ 1.3× the learned average for it. */
    private fun budgetPacingNudge(inputs: SuggestionInputs): IrisSuggestion? {
        val trip = ongoingTrip(inputs) ?: return null
        val start = IsoDates.parseDate(trip.startDate) ?: return null
        val end = IsoDates.parseDate(trip.endDate) ?: return null
        val tripSpend = inputs.expenses
            .filter { expense ->
                val date = IsoDates.parseDate(expense.date) ?: return@filter false
                !date.isBefore(start) && !date.isAfter(end)
            }
            .groupBy { it.category.name.lowercase(Locale.US) to it.currency.uppercase(Locale.US) }
            .mapValues { (_, group) -> group.sumOf { it.amount } }
        val over = inputs.profile.spendByCategory.firstNotNullOfOrNull { stat ->
            val spend = tripSpend[stat.category to stat.currency] ?: return@firstNotNullOfOrNull null
            if (stat.average > 0 && spend >= BUDGET_PACING_FACTOR * stat.average) stat to spend else null
        } ?: return null
        val (stat, spend) = over
        return IrisSuggestion(
            kind = IrisSuggestionKind.BUDGET_PACING_NUDGE,
            title = "Watch your ${stat.category} spend",
            body = "You've logged ${money(spend)} ${stat.currency} on ${stat.category} this trip — " +
                "above your usual ${money(stat.average)} ${stat.currency}.",
            promptToIris = "How is my ${stat.category} spending tracking on this trip?",
            dismissalKey = key(IrisSuggestionKind.BUDGET_PACING_NUDGE, "${trip.id}:${stat.category}"),
        )
    }

    /** A trip that ended within the past 24 hours. */
    private fun welcomeHome(inputs: SuggestionInputs): IrisSuggestion? {
        val trip = inputs.trips.firstOrNull { t ->
            val end = IsoDates.parseDate(t.endDate) ?: return@firstOrNull false
            end.isBefore(inputs.today) && !end.isBefore(inputs.today.minusDays(1))
        } ?: return null
        return IrisSuggestion(
            kind = IrisSuggestionKind.WELCOME_HOME,
            title = "Welcome home",
            body = "Welcome back from ${trip.destination}! Want a quick wrap-up of the trip?",
            promptToIris = "Summarize my spending from the ${trip.name} trip",
            dismissalKey = key(IrisSuggestionKind.WELCOME_HOME, trip.id),
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private const val BUDGET_PACING_FACTOR = 1.3

    /** Time until the next departure when it is in the future and strictly under [maxHours]. */
    private fun untilDeparture(inputs: SuggestionInputs, maxHours: Long): Duration? {
        val departure = inputs.nextFlightDeparture ?: return null
        val until = Duration.between(inputs.now, departure)
        return until.takeIf { !it.isNegative && !it.isZero && it < Duration.ofHours(maxHours) }
    }

    private fun nextFlightIdent(inputs: SuggestionInputs): String? =
        TripQueries.nextFlight(inputs.trips, inputs.now)?.ident

    /** The soonest trip whose start is [range] days from today (and passes [filter]), with the days. */
    private fun tripStartingIn(
        inputs: SuggestionInputs,
        range: LongRange,
        filter: (Trip) -> Boolean = { true },
    ): Pair<Trip, Long>? =
        inputs.trips
            .mapNotNull { trip ->
                val start = IsoDates.parseDate(trip.startDate) ?: return@mapNotNull null
                val days = ChronoUnit.DAYS.between(inputs.today, start)
                if (days in range && filter(trip)) trip to days else null
            }
            .minByOrNull { it.second }

    /** The trip whose date range contains today (ongoing — not merely upcoming). */
    private fun ongoingTrip(inputs: SuggestionInputs): Trip? =
        inputs.trips.firstOrNull { trip ->
            val start = IsoDates.parseDate(trip.startDate) ?: return@firstOrNull false
            val end = IsoDates.parseDate(trip.endDate) ?: return@firstOrNull false
            !inputs.today.isBefore(start) && !inputs.today.isAfter(end)
        }

    /** True when [requirement] demands a visa/eVisa (but not for "visa-free"-style values). */
    private fun requiresVisa(requirement: String): Boolean {
        val r = requirement.lowercase(Locale.US)
        if (r.contains("visa-free") || r.contains("visa free") || r.contains("no visa")) return false
        return r.contains("visa") // covers "visa required", "evisa", "visa on arrival"
    }

    private fun key(kind: IrisSuggestionKind, qualifier: String): String = "${kind.name}:$qualifier"

    private fun eta(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun Long.days(): String = if (this == 1L) "1 day" else "$this days"

    private fun String?.spaced(): String = this?.let { " $it" } ?: ""

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
}
