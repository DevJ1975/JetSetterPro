package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.IrisSuggestionEngine
import com.jetsetter.pro.core.intelligence.IrisSuggestionKind
import com.jetsetter.pro.core.intelligence.LoyaltyExpiration
import com.jetsetter.pro.core.intelligence.SeatPreference
import com.jetsetter.pro.core.intelligence.SpendStat
import com.jetsetter.pro.core.intelligence.SuggestionInputs
import com.jetsetter.pro.core.intelligence.TravelProfileData
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.travel.BagClaimEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pins the pure proactive-suggestion engine (spec §1.9, plan A5): every kind's trigger window at
 * its boundary, priority = enum declaration order, preference-nudge backoff at 3 dismissals, the
 * never-suppressed bypass, the date-keyed daily briefing, and the rideOnLanding bag-claim line.
 * Pure JVM — explicit clock via [SuggestionInputs].
 */
class IrisSuggestionEngineTest {

    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 7, 17)

    private fun inputs(
        trips: List<Trip> = emptyList(),
        expenses: List<Expense> = emptyList(),
        nextFlightDeparture: Instant? = null,
        checkedIn: Boolean = false,
        loyaltyExpirations: List<LoyaltyExpiration> = emptyList(),
        profile: TravelProfileData = TravelProfileData(),
        uberBooked: Boolean = false,
        rideOnLandingBooked: Boolean = false,
        arrivalIata: String? = null,
        hasCheckedBag: Boolean = false,
        visaRequirement: String? = null,
        kbVisaNote: String? = null,
        kbWeatherNote: String? = null,
        dismissedCounts: Map<String, Int> = emptyMap(),
        dailyBriefingShownFor: LocalDate? = null,
    ) = SuggestionInputs(
        now = now,
        today = today,
        trips = trips,
        expenses = expenses,
        nextFlightDeparture = nextFlightDeparture,
        checkedIn = checkedIn,
        loyaltyExpirations = loyaltyExpirations,
        profile = profile,
        uberBooked = uberBooked,
        rideOnLandingBooked = rideOnLandingBooked,
        arrivalIata = arrivalIata,
        hasCheckedBag = hasCheckedBag,
        visaRequirement = visaRequirement,
        kbVisaNote = kbVisaNote,
        kbWeatherNote = kbWeatherNote,
        dismissedCounts = dismissedCounts,
        dailyBriefingShownFor = dailyBriefingShownFor,
    )

    private fun trip(
        id: String = "trip-1",
        startDate: LocalDate,
        endDate: LocalDate,
        name: String = "Test Trip",
        destination: String = "Tokyo, Japan",
        items: List<ItineraryItem> = emptyList(),
        packingList: List<PackingItem> = emptyList(),
    ) = Trip(
        id = id,
        name = name,
        destination = destination,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        items = items,
        packingList = packingList,
    )

    private fun kinds(inputs: SuggestionInputs): List<IrisSuggestionKind> =
        IrisSuggestionEngine.evaluate(inputs).map { it.kind }

    private fun hoursFromNow(hours: Long, minutes: Long = 0): Instant =
        now.plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES)

    private val learnedSeat = TravelProfileData(
        typicalSeat = SeatPreference(position = "window", zone = "forward", confidence = 0.6, sampleSize = 2),
    )

    // ── checkInWindow ────────────────────────────────────────────────────────────────────────────

    @Test
    fun checkInWindow_firesStrictlyUnder24h_notWhenCheckedInOrAt24h() {
        assertTrue(
            IrisSuggestionKind.CHECK_IN_WINDOW in
                kinds(inputs(nextFlightDeparture = hoursFromNow(23, 59))),
        )
        assertFalse(
            IrisSuggestionKind.CHECK_IN_WINDOW in
                kinds(inputs(nextFlightDeparture = hoursFromNow(24))),
        )
        assertFalse(
            IrisSuggestionKind.CHECK_IN_WINDOW in
                kinds(inputs(nextFlightDeparture = hoursFromNow(23), checkedIn = true)),
        )
        assertFalse(
            IrisSuggestionKind.CHECK_IN_WINDOW in
                kinds(inputs(nextFlightDeparture = hoursFromNow(-1))),
        )
    }

    @Test
    fun checkInWindow_promptUsesFlightIdentFromTrips() {
        val flightTrip = trip(
            startDate = today,
            endDate = today.plusDays(3),
            items = listOf(
                ItineraryItem(
                    title = "DL1423 · LAS → ATL",
                    type = ItineraryItemType.FLIGHT,
                    startDate = hoursFromNow(20).toString(),
                ),
            ),
        )
        val suggestion = IrisSuggestionEngine
            .evaluate(inputs(trips = listOf(flightTrip), nextFlightDeparture = hoursFromNow(20)))
            .first { it.kind == IrisSuggestionKind.CHECK_IN_WINDOW }
        assertEquals("Check me in for flight DL1423", suggestion.promptToIris)
        assertTrue(suggestion.dismissalKey.startsWith("CHECK_IN_WINDOW:"))
        assertTrue(suggestion.dismissalKey.contains("DL1423"))
    }

    // ── seatPreferenceNudge ──────────────────────────────────────────────────────────────────────

    @Test
    fun seatPreferenceNudge_windowConfidenceAndSampleBoundaries() {
        val base = inputs(nextFlightDeparture = hoursFromNow(35, 59), profile = learnedSeat)
        assertTrue(IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in kinds(base))
        // At exactly 36h the window is closed.
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(base.copy(nextFlightDeparture = hoursFromNow(36))),
        )
        // Confidence below 0.6 → no nudge.
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(
                    base.copy(
                        profile = TravelProfileData(
                            typicalSeat = SeatPreference("window", "forward", confidence = 0.59, sampleSize = 5),
                        ),
                    ),
                ),
        )
        // Fewer than 2 samples → no nudge.
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(
                    base.copy(
                        profile = TravelProfileData(
                            typicalSeat = SeatPreference("window", "forward", confidence = 0.9, sampleSize = 1),
                        ),
                    ),
                ),
        )
        // No learned seat → no nudge.
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in kinds(base.copy(profile = TravelProfileData())),
        )
    }

    // ── preferredCabinNudge ──────────────────────────────────────────────────────────────────────

    @Test
    fun preferredCabinNudge_firesForPremiumCabinsOnly() {
        val base = inputs(nextFlightDeparture = hoursFromNow(35))
        for (premium in listOf("business", "first", "Premium Economy")) {
            assertTrue(
                "expected $premium to nudge",
                IrisSuggestionKind.PREFERRED_CABIN_NUDGE in
                    kinds(base.copy(profile = TravelProfileData(preferredCabin = premium))),
            )
        }
        assertFalse(
            IrisSuggestionKind.PREFERRED_CABIN_NUDGE in
                kinds(base.copy(profile = TravelProfileData(preferredCabin = "economy"))),
        )
        assertFalse(
            IrisSuggestionKind.PREFERRED_CABIN_NUDGE in
                kinds(
                    base.copy(
                        nextFlightDeparture = hoursFromNow(36),
                        profile = TravelProfileData(preferredCabin = "business"),
                    ),
                ),
        )
    }

    // ── tierAtRisk ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun tierAtRisk_firesWithinSevenDays() {
        assertTrue(
            IrisSuggestionKind.TIER_AT_RISK in
                kinds(inputs(loyaltyExpirations = listOf(LoyaltyExpiration("Delta SkyMiles", today.plusDays(7))))),
        )
        assertTrue(
            IrisSuggestionKind.TIER_AT_RISK in
                kinds(inputs(loyaltyExpirations = listOf(LoyaltyExpiration("Delta SkyMiles", today)))),
        )
        assertFalse(
            IrisSuggestionKind.TIER_AT_RISK in
                kinds(inputs(loyaltyExpirations = listOf(LoyaltyExpiration("Delta SkyMiles", today.plusDays(8))))),
        )
        // Already expired (yesterday) → no longer at risk, nothing to save.
        assertFalse(
            IrisSuggestionKind.TIER_AT_RISK in
                kinds(inputs(loyaltyExpirations = listOf(LoyaltyExpiration("Delta SkyMiles", today.minusDays(1))))),
        )
    }

    // ── rideToAirport ────────────────────────────────────────────────────────────────────────────

    @Test
    fun rideToAirport_firesUnder12hUnlessBooked() {
        assertTrue(
            IrisSuggestionKind.RIDE_TO_AIRPORT in kinds(inputs(nextFlightDeparture = hoursFromNow(11, 59))),
        )
        assertFalse(
            IrisSuggestionKind.RIDE_TO_AIRPORT in kinds(inputs(nextFlightDeparture = hoursFromNow(12))),
        )
        assertFalse(
            IrisSuggestionKind.RIDE_TO_AIRPORT in
                kinds(inputs(nextFlightDeparture = hoursFromNow(11), uberBooked = true)),
        )
    }

    // ── rideOnLanding ────────────────────────────────────────────────────────────────────────────

    private fun landingTrip(arrivalInMinutes: Long) = trip(
        startDate = today,
        endDate = today.plusDays(3),
        items = listOf(
            ItineraryItem(
                title = "DL1423 · LAS → ATL",
                type = ItineraryItemType.FLIGHT,
                startDate = now.minus(3, ChronoUnit.HOURS).toString(),
                endDate = now.plus(arrivalInMinutes, ChronoUnit.MINUTES).toString(),
            ),
        ),
    )

    @Test
    fun rideOnLanding_firesWithin90m_bodyCarriesBagEstimate() {
        val fired = IrisSuggestionEngine.evaluate(
            inputs(trips = listOf(landingTrip(60)), arrivalIata = "ATL", hasCheckedBag = true),
        ).first { it.kind == IrisSuggestionKind.RIDE_ON_LANDING }
        assertTrue(
            "body should carry the estimator display",
            fired.body.contains(BagClaimEstimator.estimate("ATL", hasCheckedBag = true).display),
        )
        assertTrue(fired.dismissalKey.startsWith("RIDE_ON_LANDING:"))
    }

    @Test
    fun rideOnLanding_windowAndFlagBoundaries() {
        assertFalse(
            IrisSuggestionKind.RIDE_ON_LANDING in
                kinds(inputs(trips = listOf(landingTrip(91)), arrivalIata = "ATL", hasCheckedBag = true)),
        )
        assertFalse(
            IrisSuggestionKind.RIDE_ON_LANDING in
                kinds(
                    inputs(
                        trips = listOf(landingTrip(60)),
                        arrivalIata = "ATL",
                        hasCheckedBag = true,
                        rideOnLandingBooked = true,
                    ),
                ),
        )
        // Already landed → nothing to line up.
        assertFalse(
            IrisSuggestionKind.RIDE_ON_LANDING in
                kinds(inputs(trips = listOf(landingTrip(-5)), arrivalIata = "ATL", hasCheckedBag = true)),
        )
    }

    // ── packingNudge ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun packingNudge_firesFor14To28DaysWithEmptyList() {
        fun tripStartingIn(days: Long, packing: List<PackingItem> = emptyList()) =
            trip(startDate = today.plusDays(days), endDate = today.plusDays(days + 4), packingList = packing)

        assertTrue(IrisSuggestionKind.PACKING_NUDGE in kinds(inputs(trips = listOf(tripStartingIn(14)))))
        assertTrue(IrisSuggestionKind.PACKING_NUDGE in kinds(inputs(trips = listOf(tripStartingIn(28)))))
        assertFalse(IrisSuggestionKind.PACKING_NUDGE in kinds(inputs(trips = listOf(tripStartingIn(13)))))
        assertFalse(IrisSuggestionKind.PACKING_NUDGE in kinds(inputs(trips = listOf(tripStartingIn(29)))))
        assertFalse(
            IrisSuggestionKind.PACKING_NUDGE in
                kinds(inputs(trips = listOf(tripStartingIn(20, packing = listOf(PackingItem(name = "Passport")))))),
        )
    }

    // ── visaCheck ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun visaCheck_firesZeroToSevenDaysForVisaRequirements_enrichedWithKbNote() {
        fun visaInputs(startInDays: Long, requirement: String?) = inputs(
            trips = listOf(trip(startDate = today.plusDays(startInDays), endDate = today.plusDays(startInDays + 5))),
            visaRequirement = requirement,
            kbVisaNote = "KB: eVisa processing takes 3 business days.",
        )

        val fired = IrisSuggestionEngine.evaluate(visaInputs(7, "eVisa required"))
            .first { it.kind == IrisSuggestionKind.VISA_CHECK }
        assertTrue(fired.body.contains("KB: eVisa processing takes 3 business days."))

        assertTrue(IrisSuggestionKind.VISA_CHECK in kinds(visaInputs(0, "Visa required")))
        assertFalse(IrisSuggestionKind.VISA_CHECK in kinds(visaInputs(8, "Visa required")))
        assertFalse(IrisSuggestionKind.VISA_CHECK in kinds(visaInputs(3, "Visa-free")))
        assertFalse(IrisSuggestionKind.VISA_CHECK in kinds(visaInputs(3, null)))
    }

    // ── weatherWatch ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun weatherWatch_firesZeroToThreeDays_enrichedWithKbNote() {
        fun weatherInputs(startInDays: Long) = inputs(
            trips = listOf(trip(startDate = today.plusDays(startInDays), endDate = today.plusDays(startInDays + 5))),
            kbWeatherNote = "KB: rainy season peaks in July.",
        )

        val fired = IrisSuggestionEngine.evaluate(weatherInputs(3))
            .first { it.kind == IrisSuggestionKind.WEATHER_WATCH }
        assertTrue(fired.body.contains("KB: rainy season peaks in July."))

        assertTrue(IrisSuggestionKind.WEATHER_WATCH in kinds(weatherInputs(0)))
        assertFalse(IrisSuggestionKind.WEATHER_WATCH in kinds(weatherInputs(4)))
    }

    // ── dailyBriefing ────────────────────────────────────────────────────────────────────────────

    @Test
    fun dailyBriefing_oncePerDayDuringOngoingTrip_keyIncludesDate() {
        val ongoing = trip(startDate = today.minusDays(1), endDate = today.plusDays(2))

        val fired = IrisSuggestionEngine.evaluate(inputs(trips = listOf(ongoing)))
            .first { it.kind == IrisSuggestionKind.DAILY_BRIEFING }
        assertTrue("daily key must embed the date", fired.dismissalKey.contains(today.toString()))

        assertFalse(
            IrisSuggestionKind.DAILY_BRIEFING in
                kinds(inputs(trips = listOf(ongoing), dailyBriefingShownFor = today)),
        )
        // Shown yesterday → fires again today.
        assertTrue(
            IrisSuggestionKind.DAILY_BRIEFING in
                kinds(inputs(trips = listOf(ongoing), dailyBriefingShownFor = today.minusDays(1))),
        )
        // No ongoing trip (starts tomorrow) → no briefing.
        assertFalse(
            IrisSuggestionKind.DAILY_BRIEFING in
                kinds(inputs(trips = listOf(trip(startDate = today.plusDays(1), endDate = today.plusDays(3))))),
        )
    }

    // ── budgetPacingNudge ────────────────────────────────────────────────────────────────────────

    @Test
    fun budgetPacing_firesAtOnePointThreeTimesLearnedAverage() {
        val ongoing = trip(startDate = today.minusDays(2), endDate = today.plusDays(2))
        val profile = TravelProfileData(
            spendByCategory = listOf(SpendStat(category = "food", currency = "USD", average = 100.0, count = 5)),
        )

        fun withSpend(amount: Double) = inputs(
            trips = listOf(ongoing),
            expenses = listOf(
                Expense(
                    amount = amount,
                    category = ExpenseCategory.FOOD,
                    merchant = "Nobu",
                    date = today.minusDays(1).toString(),
                ),
            ),
            profile = profile,
        )

        assertTrue(IrisSuggestionKind.BUDGET_PACING_NUDGE in kinds(withSpend(130.0)))
        assertFalse(IrisSuggestionKind.BUDGET_PACING_NUDGE in kinds(withSpend(129.0)))
        // Spend outside the trip's date range doesn't count.
        assertFalse(
            IrisSuggestionKind.BUDGET_PACING_NUDGE in
                kinds(
                    withSpend(130.0).copy(
                        expenses = listOf(
                            Expense(
                                amount = 500.0,
                                category = ExpenseCategory.FOOD,
                                merchant = "Nobu",
                                date = today.minusDays(30).toString(),
                            ),
                        ),
                    ),
                ),
        )
    }

    // ── welcomeHome ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun welcomeHome_firesForTripEndedWithinPastDay() {
        fun endedInputs(endedDaysAgo: Long) = inputs(
            trips = listOf(trip(startDate = today.minusDays(endedDaysAgo + 5), endDate = today.minusDays(endedDaysAgo))),
        )
        assertTrue(IrisSuggestionKind.WELCOME_HOME in kinds(endedInputs(1)))
        assertFalse(IrisSuggestionKind.WELCOME_HOME in kinds(endedInputs(2)))
        // Ends today → still ongoing, not "home".
        assertFalse(IrisSuggestionKind.WELCOME_HOME in kinds(endedInputs(0)))
    }

    // ── priority, backoff, bypass ────────────────────────────────────────────────────────────────

    @Test
    fun evaluate_sortsByEnumPriorityOrder() {
        val everything = inputs(
            nextFlightDeparture = hoursFromNow(11),   // checkIn + seat + cabin + rideToAirport
            profile = TravelProfileData(
                typicalSeat = SeatPreference("window", "forward", confidence = 0.9, sampleSize = 4),
                preferredCabin = "business",
            ),
            loyaltyExpirations = listOf(LoyaltyExpiration("Delta SkyMiles", today.plusDays(3))),
            trips = listOf(
                trip(id = "soon", startDate = today.plusDays(2), endDate = today.plusDays(6)),   // visa + weather
                trip(id = "later", startDate = today.plusDays(20), endDate = today.plusDays(24)), // packing
            ),
            visaRequirement = "eVisa required",
        )
        val result = IrisSuggestionEngine.evaluate(everything)
        val ordinals = result.map { it.kind.ordinal }
        assertEquals("priority order must be the enum order", ordinals.sorted(), ordinals)
        assertEquals(IrisSuggestionKind.CHECK_IN_WINDOW, result.first().kind)
        assertTrue(result.size >= 6)
    }

    @Test
    fun preferenceNudges_backOffAtThreeDismissals() {
        val base = inputs(nextFlightDeparture = hoursFromNow(30), profile = learnedSeat)
        assertTrue(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(base.copy(dismissedCounts = mapOf("SEAT_PREFERENCE_NUDGE" to 2))),
        )
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(base.copy(dismissedCounts = mapOf("SEAT_PREFERENCE_NUDGE" to 3))),
        )
        assertFalse(
            IrisSuggestionKind.SEAT_PREFERENCE_NUDGE in
                kinds(base.copy(dismissedCounts = mapOf("SEAT_PREFERENCE_NUDGE" to 7))),
        )
    }

    @Test
    fun neverSuppressedKinds_bypassDismissalCounts() {
        val heavyDismissals = mapOf(
            "CHECK_IN_WINDOW" to 99,
            "VISA_CHECK" to 99,
            "WEATHER_WATCH" to 99,
        )
        val checkIn = inputs(nextFlightDeparture = hoursFromNow(5), dismissedCounts = heavyDismissals)
        assertTrue(IrisSuggestionKind.CHECK_IN_WINDOW in kinds(checkIn))

        val visaAndWeather = inputs(
            trips = listOf(trip(startDate = today.plusDays(2), endDate = today.plusDays(6))),
            visaRequirement = "Visa required",
            dismissedCounts = heavyDismissals,
        )
        assertTrue(IrisSuggestionKind.VISA_CHECK in kinds(visaAndWeather))
        assertTrue(IrisSuggestionKind.WEATHER_WATCH in kinds(visaAndWeather))
    }
}
