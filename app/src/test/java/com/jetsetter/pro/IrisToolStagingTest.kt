package com.jetsetter.pro

import com.jetsetter.pro.core.ai.ActionRouter
import com.jetsetter.pro.core.ai.CheckinStateSource
import com.jetsetter.pro.core.ai.DepartureSource
import com.jetsetter.pro.core.ai.ExpenseCategorizer
import com.jetsetter.pro.core.ai.ExpenseToolPort
import com.jetsetter.pro.core.ai.IrisPendingAction
import com.jetsetter.pro.core.ai.IrisToolDataSources
import com.jetsetter.pro.core.ai.IrisToolDispatcher
import com.jetsetter.pro.core.ai.LovedOnesToolPort
import com.jetsetter.pro.core.ai.LuggageStatusSource
import com.jetsetter.pro.core.ai.MemoryToolPort
import com.jetsetter.pro.core.ai.NoopExpenseCategorizer
import com.jetsetter.pro.core.ai.TravelProfileSummaryProvider
import com.jetsetter.pro.core.ai.TripToolPort
import com.jetsetter.pro.core.ai.VisaEssentialsSource
import com.jetsetter.pro.core.data.lovedones.LovedOne
import com.jetsetter.pro.core.intelligence.IrisPreferenceCategory
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.weather.WeatherReport
import com.jetsetter.pro.core.weather.WeatherService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pins the confirm-before-commit staging contract (spec §1.4, plan R4) through the real
 * [ActionRouter]: staged tools write NOTHING at dispatch time, produce a pending action of the
 * right kind, and only the confirmed commit performs the repository write; a second staged tool
 * while one is pending is rejected without replacing the slot. Also pins `getUserTrips`
 * (filter + 6-item cap + packing count, R10c) and the `logExpense` category fallback.
 */
class IrisToolStagingTest {

    private val fixedToday: LocalDate = LocalDate.of(2026, 7, 17)

    // ── Fakes ────────────────────────────────────────────────────────────────────────────────

    private class FakeTrips : TripToolPort {
        val stored = mutableListOf<Trip>()
        var upserts = 0
        override suspend fun trips(): List<Trip> = stored.toList()
        override suspend fun upsert(trip: Trip) {
            upserts++
            val index = stored.indexOfFirst { it.id == trip.id }
            if (index >= 0) stored[index] = trip else stored += trip
        }
    }

    private class FakeExpenses : ExpenseToolPort {
        val stored = mutableListOf<Expense>()
        var adds = 0
        override suspend fun expenses(): List<Expense> = stored.toList()
        override suspend fun add(expense: Expense) {
            adds++
            stored += expense
        }
    }

    private class FakeMemory : MemoryToolPort {
        val remembered = mutableListOf<Pair<IrisPreferenceCategory, String>>()
        override suspend fun remember(category: IrisPreferenceCategory, value: String) {
            remembered += category to value
        }
    }

    private class FakeCheckin : CheckinStateSource {
        var nextIdent: String? = "DL1423"
        var checkedIn = mutableListOf<String>()
        override suspend fun nextFlightIdent(): String? = nextIdent
        override suspend fun markCheckedIn(flightIdent: String): String {
            checkedIn += flightIdent
            return "Checked in for $flightIdent."
        }
    }

    private class Harness(weather: WeatherReport? = null) {
        val trips = FakeTrips()
        val expenses = FakeExpenses()
        val memory = FakeMemory()
        val checkin = FakeCheckin()
        val router = ActionRouter()
        var categorizer: ExpenseCategorizer = NoopExpenseCategorizer

        private val weatherService = object : WeatherService {
            override suspend fun current(query: String): WeatherReport? = weather
        }

        val dispatcher by lazy {
            IrisToolDispatcher(
                trips = trips,
                expenses = expenses,
                actionRouter = router,
                memory = memory,
                profileProvider = object : TravelProfileSummaryProvider {
                    override suspend fun summaryForPrompt(): String = ""
                },
                weatherService = weatherService,
                dataSources = IrisToolDataSources(
                    visaEssentials = object : VisaEssentialsSource {
                        override suspend fun essentials(country: String): String = "essentials for $country"
                    },
                    departure = object : DepartureSource {
                        override suspend fun recommendation(
                            originIata: String,
                            scheduledDepartureIso: String,
                            lane: String?,
                            lat: Double?,
                            lng: Double?,
                        ): String = "departure plan for $originIata"
                    },
                    luggage = object : LuggageStatusSource {
                        override suspend fun luggageStatus(): String = "bags fine"
                    },
                    checkin = checkin,
                ),
                lovedOnes = object : LovedOnesToolPort {
                    override suspend fun all(): List<LovedOne> = emptyList()
                },
                categorizer = categorizer,
                today = { LocalDate.of(2026, 7, 17) },
                now = { Instant.parse("2026-07-17T12:00:00Z") },
            )
        }
    }

    private fun stagedPhrasing(result: String) {
        assertTrue("expected staged phrasing, got: $result", result.startsWith("Staged for user confirmation:"))
        assertTrue("expected 'prepared' instruction, got: $result", result.contains("prepared"))
    }

    // ── logExpense ───────────────────────────────────────────────────────────────────────────

    @Test
    fun logExpense_stagesWithoutWriting_thenCommitWrites_withOtherFallback() = runBlocking {
        val h = Harness()

        val result = h.dispatcher.execute(
            "logExpense",
            JSONObject().put("amount", 40.0).put("merchant", "Nobu"),
        )

        stagedPhrasing(result)
        val pending = h.router.pending.value
        assertNotNull(pending)
        assertEquals(IrisPendingAction.Kind.LOG_EXPENSE, pending!!.kind)
        // Categorizer is the null-returning Noop → category falls back to OTHER.
        assertTrue("summary should carry the OTHER fallback: ${pending.summary}", pending.summary.contains("Other"))
        assertEquals("no write before confirmation", 0, h.expenses.adds)

        val committed = h.router.confirm()

        assertEquals("Logged USD 40.00 at Nobu (Other).", committed)
        assertEquals(1, h.expenses.adds)
        val expense = h.expenses.stored.single()
        assertEquals(40.0, expense.amount, 0.0001)
        assertEquals("USD", expense.currency)
        assertEquals(ExpenseCategory.OTHER, expense.category)
        assertEquals("Nobu", expense.merchant)
        assertEquals(fixedToday.toString(), expense.date)
        assertNull("slot cleared after confirm", h.router.pending.value)
    }

    @Test
    fun logExpense_explicitCategory_winsOverCategorizer() = runBlocking {
        val h = Harness()
        h.categorizer = object : ExpenseCategorizer {
            override suspend fun categorize(merchant: String, notes: String?, receiptText: String?) =
                ExpenseCategory.FOOD
        }

        h.dispatcher.execute(
            "logExpense",
            JSONObject().put("amount", 12.5).put("merchant", "Airport Taxi").put("category", "transport"),
        )

        assertTrue(h.router.pending.value!!.summary.contains("Transport"))
    }

    @Test
    fun logExpense_missingAmount_asksInsteadOfStaging() = runBlocking {
        val h = Harness()

        val result = h.dispatcher.execute("logExpense", JSONObject().put("merchant", "Nobu"))

        assertTrue("expected instructive error, got: $result", result.contains("amount"))
        assertNull(h.router.pending.value)
        assertEquals(0, h.expenses.adds)
    }

    // ── R4: single pending slot ──────────────────────────────────────────────────────────────

    @Test
    fun secondStagedTool_whileOnePending_isRejected_andPendingUnchanged() = runBlocking {
        val h = Harness()
        h.dispatcher.execute("logExpense", JSONObject().put("amount", 40.0).put("merchant", "Nobu"))
        val first = h.router.pending.value
        assertNotNull(first)

        val rejection = h.dispatcher.execute(
            "addTrip",
            JSONObject().put("destination", "Lisbon").put("startDate", "2026-09-01").put("endDate", "2026-09-05"),
        )

        assertEquals(IrisToolDispatcher.STAGE_REJECTED, rejection)
        assertEquals("pending action must be untouched", first!!.id, h.router.pending.value!!.id)
        assertEquals(IrisPendingAction.Kind.LOG_EXPENSE, h.router.pending.value!!.kind)
        assertEquals("the rejected trip must not be written", 0, h.trips.upserts)
    }

    // ── addTrip ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun addTrip_endBeforeStart_errorsWithoutStaging() = runBlocking {
        val h = Harness()

        val result = h.dispatcher.execute(
            "addTrip",
            JSONObject().put("destination", "Lisbon").put("startDate", "2026-09-05").put("endDate", "2026-09-01"),
        )

        assertTrue("expected a date-order error, got: $result", result.contains("before startDate"))
        assertNull(h.router.pending.value)
        assertEquals(0, h.trips.upserts)
    }

    @Test
    fun addTrip_stages_thenCommitWritesTrip() = runBlocking {
        val h = Harness()

        val result = h.dispatcher.execute(
            "addTrip",
            JSONObject().put("destination", "Lisbon").put("startDate", "2026-09-01").put("endDate", "2026-09-05"),
        )

        stagedPhrasing(result)
        assertEquals(IrisPendingAction.Kind.ADD_TRIP, h.router.pending.value!!.kind)
        assertEquals("no write before confirmation", 0, h.trips.upserts)

        val committed = h.router.confirm()

        assertNotNull(committed)
        assertTrue(committed!!.contains("Lisbon"))
        assertEquals(1, h.trips.upserts)
        val trip = h.trips.stored.single()
        assertEquals("Lisbon", trip.destination)
        assertEquals("2026-09-01", trip.startDate)
        assertEquals("2026-09-05", trip.endDate)
        assertEquals("Trip to Lisbon", trip.name) // default name when omitted
    }

    // ── checkInForFlight ─────────────────────────────────────────────────────────────────────

    @Test
    fun checkInForFlight_defaultsToNextFlight_andCommitsThroughSource() = runBlocking {
        val h = Harness()

        val result = h.dispatcher.execute("checkInForFlight", JSONObject())

        stagedPhrasing(result)
        val pending = h.router.pending.value!!
        assertEquals(IrisPendingAction.Kind.CHECK_IN, pending.kind)
        assertTrue("summary should name the default flight: ${pending.summary}", pending.summary.contains("DL1423"))
        assertTrue("no check-in before confirmation", h.checkin.checkedIn.isEmpty())

        assertEquals("Checked in for DL1423.", h.router.confirm())
        assertEquals(listOf("DL1423"), h.checkin.checkedIn)
    }

    @Test
    fun checkInForFlight_noFlightAnywhere_asksInsteadOfStaging() = runBlocking {
        val h = Harness()
        h.checkin.nextIdent = null

        val result = h.dispatcher.execute("checkInForFlight", JSONObject())

        assertTrue("expected instructive error, got: $result", result.contains("No upcoming flight"))
        assertNull(h.router.pending.value)
    }

    // ── generatePackingList ──────────────────────────────────────────────────────────────────

    @Test
    fun generatePackingList_stages_thenCommitWritesPackingList() = runBlocking {
        val h = Harness()
        h.trips.stored += Trip(
            name = "Vegas Run",
            destination = "Las Vegas",
            startDate = "2026-07-15",
            endDate = "2026-07-20", // active: fixedToday (07-17) is inside the range
        )

        val result = h.dispatcher.execute("generatePackingList", JSONObject())

        stagedPhrasing(result)
        assertEquals(IrisPendingAction.Kind.GENERATE_PACKING_LIST, h.router.pending.value!!.kind)
        assertEquals("no write before confirmation", 0, h.trips.upserts)

        val committed = h.router.confirm()

        assertNotNull(committed)
        assertEquals(1, h.trips.upserts)
        val packingList = h.trips.stored.single().packingList
        assertTrue("commit must write a non-empty packing list", packingList.isNotEmpty())
        assertTrue(packingList.none { it.isPacked })
        assertTrue(committed!!.contains("${packingList.size}-item"))
    }

    // ── submitExpenses ───────────────────────────────────────────────────────────────────────

    @Test
    fun submitExpenses_stagesWithProvider_thenCommitReportsTotals() = runBlocking {
        val h = Harness()
        h.expenses.stored += Expense(
            amount = 40.0,
            category = ExpenseCategory.FOOD,
            merchant = "Nobu",
            date = "2026-07-16",
        )

        val result = h.dispatcher.execute("submitExpenses", JSONObject().put("provider", "expensify"))

        stagedPhrasing(result)
        val pending = h.router.pending.value!!
        assertEquals(IrisPendingAction.Kind.SUBMIT_EXPENSES, pending.kind)
        assertTrue(pending.summary.contains("Expensify"))

        val committed = h.router.confirm()
        assertTrue("commit should report the total: $committed", committed!!.contains("USD 40.00"))
    }

    @Test
    fun submitExpenses_unknownProvider_errorsWithoutStaging() = runBlocking {
        val h = Harness()
        h.expenses.stored += Expense(
            amount = 1.0,
            category = ExpenseCategory.FOOD,
            merchant = "x",
            date = "2026-07-16",
        )

        val result = h.dispatcher.execute("submitExpenses", JSONObject().put("provider", "paypal"))

        assertTrue(result.contains("email, expensify, ramp, brex, divvy"))
        assertNull(h.router.pending.value)
    }

    // ── getUserTrips (R10c) ──────────────────────────────────────────────────────────────────

    @Test
    fun getUserTrips_filters_capsItemsAtSix_andShowsPackingCount() = runBlocking {
        val h = Harness()
        h.trips.stored += Trip(
            name = "Lisbon Recap",
            destination = "Lisbon",
            startDate = "2026-06-01",
            endDate = "2026-06-05", // ended before fixedToday → past
        )
        h.trips.stored += Trip(
            name = "Tokyo Sprint",
            destination = "Tokyo",
            startDate = "2026-08-01",
            endDate = "2026-08-09",
            items = (1..8).map { day ->
                ItineraryItem(
                    title = "Stop $day",
                    type = ItineraryItemType.ACTIVITY,
                    startDate = "2026-08-0${day}T10:00:00Z",
                )
            },
            packingList = listOf(
                PackingItem(name = "Passport", isPacked = true),
                PackingItem(name = "Charger"),
                PackingItem(name = "Adapter"),
            ),
        )

        val upcoming = h.dispatcher.execute("getUserTrips", JSONObject().put("filter", "upcoming"))

        assertTrue(upcoming.contains("Tokyo Sprint"))
        assertFalse("past trip must be filtered out", upcoming.contains("Lisbon Recap"))
        assertTrue("packing count missing: $upcoming", upcoming.contains("3 item(s), 1 packed"))
        assertEquals("itinerary items must cap at 6", 6, Regex("\\n  - ").findAll(upcoming).count())
        assertTrue(upcoming.contains("2 more itinerary item(s)"))

        val past = h.dispatcher.execute("getUserTrips", JSONObject().put("filter", "past"))
        assertTrue(past.contains("Lisbon Recap"))
        assertFalse(past.contains("Tokyo Sprint"))

        val all = h.dispatcher.execute("getUserTrips", JSONObject())
        assertTrue(all.contains("Lisbon Recap"))
        assertTrue(all.contains("Tokyo Sprint"))
    }

    // ── ActionRouter odds and ends the staging contract relies on ────────────────────────────

    @Test
    fun confirm_withNothingPending_returnsNull() = runBlocking {
        assertNull(ActionRouter().confirm())
    }

    @Test
    fun confirm_whenCommitThrows_clearsSlot_andReturnsErrorLine() = runBlocking {
        val router = ActionRouter()
        router.stage(
            IrisPendingAction(
                kind = IrisPendingAction.Kind.ADD_TRIP,
                summary = "Add trip \"Boom\"",
                commit = { throw IllegalStateException("db unavailable") },
            ),
        )

        val result = router.confirm()

        assertNotNull(result)
        assertTrue(result!!.contains("db unavailable"))
        assertNull(router.pending.value)
    }

    @Test
    fun clear_cancelsThePendingAction() = runBlocking {
        val h = Harness()
        h.dispatcher.execute("logExpense", JSONObject().put("amount", 5.0).put("merchant", "Cafe"))
        assertNotNull(h.router.pending.value)

        h.router.clear()

        assertNull(h.router.pending.value)
        assertEquals("cancel must not write", 0, h.expenses.adds)
    }
}
