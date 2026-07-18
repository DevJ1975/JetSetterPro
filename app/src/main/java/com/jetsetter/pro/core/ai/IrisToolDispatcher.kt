package com.jetsetter.pro.core.ai

import com.jetsetter.pro.core.data.lovedones.LovedOne
import com.jetsetter.pro.core.data.lovedones.LovedOnesRepository
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.IrisMemory
import com.jetsetter.pro.core.intelligence.IrisPreferenceCategory
import com.jetsetter.pro.core.intelligence.TravelProfileStore
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.TripQueries
import com.jetsetter.pro.core.util.IsoDates
import com.jetsetter.pro.core.util.SmsTemplates
import com.jetsetter.pro.core.weather.WeatherReport
import com.jetsetter.pro.core.weather.WeatherService
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

// ── Testability ports ────────────────────────────────────────────────────────────────────────
// The repositories below are concrete classes whose transitive dependencies (Room DAOs, DataStore,
// Supabase) can't be constructed in a plain-JUnit JVM test, so the dispatcher's primary
// constructor takes these narrow internal ports instead. Production still injects the real
// classes via the @Inject secondary constructor, which adapts them inline.

/** The slice of [TripRepository] the tools need. */
internal interface TripToolPort {
    suspend fun trips(): List<Trip>
    suspend fun upsert(trip: Trip)
}

/** The slice of [ExpenseRepository] the tools need. */
internal interface ExpenseToolPort {
    suspend fun expenses(): List<Expense>
    suspend fun add(expense: Expense)
}

/** The slice of [IrisMemory] the tools need. */
internal interface MemoryToolPort {
    suspend fun remember(category: IrisPreferenceCategory, value: String)
}

/** The slice of [LovedOnesRepository] the tools need. */
internal interface LovedOnesToolPort {
    suspend fun all(): List<LovedOne>
}

/**
 * The slice of [TravelProfileStore] the staged commits need (plan A4 learning seam). Recording is
 * consent-gated and silent inside the store; commit lambdas additionally wrap calls in
 * `runCatching` so a signal failure can never fail a confirmed action.
 */
internal interface SignalToolPort {
    /** Append one travel signal (e.g. expenseLogged from a confirmed logExpense). */
    suspend fun record(signal: TravelSignal)

    /** A trip was added via IRIS — records tripCompleted for retroactively-added past trips. */
    suspend fun tripAdded(trip: Trip)
}

/** Default no-op port so tests (and the primary constructor) don't need a real store. */
internal object NoopSignalToolPort : SignalToolPort {
    override suspend fun record(signal: TravelSignal) = Unit
    override suspend fun tripAdded(trip: Trip) = Unit
}

/**
 * The tools IRIS can call to drive the app (Anthropic `tool_use`) — the camelCase 14-tool roster
 * shared with iOS (spec §1.3). [schema] is the tool-definition array sent on every Claude request;
 * [execute] runs one tool call and returns the string that goes back as its `tool_result`.
 *
 * Confirm-before-commit (spec §1.4, plan R4): the data-changing tools — `logExpense`, `addTrip`,
 * `checkInForFlight`, `generatePackingList`, `submitExpenses` — never write at dispatch time.
 * They stage an [IrisPendingAction] on [ActionRouter] whose `commit` lambda performs the real
 * repository write when the user confirms; the tool_result tells the model the action is
 * *prepared*, not done. A second staged tool while one is pending is rejected (single slot).
 * Navigation tools (`openScreen`, `trackFlight`, the SMS composer) execute immediately — opening
 * a screen or a prefilled composer changes no data.
 *
 * Tools only touch on-device stores; nothing here sends user data to a third party beyond what
 * the model already sees in the conversation.
 */
@Singleton
class IrisToolDispatcher internal constructor(
    private val trips: TripToolPort,
    private val expenses: ExpenseToolPort,
    private val actionRouter: ActionRouter,
    private val memory: MemoryToolPort,
    private val profileProvider: TravelProfileSummaryProvider,
    private val weatherService: WeatherService,
    private val dataSources: IrisToolDataSources,
    private val lovedOnes: LovedOnesToolPort,
    private val categorizer: ExpenseCategorizer,
    private val signals: SignalToolPort = NoopSignalToolPort,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Instant = { Instant.now() },
) {

    @Inject
    constructor(
        tripRepository: TripRepository,
        expenseRepository: ExpenseRepository,
        actionRouter: ActionRouter,
        irisMemory: IrisMemory,
        profileProvider: TravelProfileSummaryProvider,
        weatherService: WeatherService,
        dataSources: IrisToolDataSources,
        lovedOnesRepository: LovedOnesRepository,
        expenseCategorizer: ExpenseCategorizer,
        travelProfileStore: TravelProfileStore,
    ) : this(
        trips = object : TripToolPort {
            override suspend fun trips(): List<Trip> = tripRepository.observeTrips().first()
            override suspend fun upsert(trip: Trip) = tripRepository.upsert(trip)
        },
        expenses = object : ExpenseToolPort {
            override suspend fun expenses(): List<Expense> = expenseRepository.observeExpenses().first()
            override suspend fun add(expense: Expense) = expenseRepository.add(expense)
        },
        actionRouter = actionRouter,
        memory = object : MemoryToolPort {
            override suspend fun remember(category: IrisPreferenceCategory, value: String) =
                irisMemory.remember(category, value)
        },
        profileProvider = profileProvider,
        weatherService = weatherService,
        dataSources = dataSources,
        lovedOnes = object : LovedOnesToolPort {
            override suspend fun all(): List<LovedOne> = lovedOnesRepository.observe().first()
        },
        categorizer = expenseCategorizer,
        signals = object : SignalToolPort {
            override suspend fun record(signal: TravelSignal) = travelProfileStore.record(signal)
            override suspend fun tripAdded(trip: Trip) =
                travelProfileStore.recordCompletedTrip(trip, source = "iris")
        },
    )

    suspend fun execute(name: String, input: JSONObject): String = when (name) {
        "getUserTrips" -> getUserTrips(input)
        "getWeather" -> getWeather(input)
        "getVisaAndCountryEssentials" -> getVisaAndCountryEssentials(input)
        "rememberUserPreference" -> rememberUserPreference(input)
        "getLearnedTravelProfile" -> getLearnedTravelProfile()
        "getDepartureRecommendation" -> getDepartureRecommendation(input)
        "submitExpenses" -> submitExpenses(input)
        "openScreen" -> openScreen(input)
        "trackFlight" -> trackFlight(input)
        "logExpense" -> logExpense(input)
        "addTrip" -> addTrip(input)
        "checkInForFlight" -> checkInForFlight(input)
        "generatePackingList" -> generatePackingList(input)
        "flightActions" -> flightActions(input)
        else -> "Unknown tool: $name"
    }

    // ── Staging ──────────────────────────────────────────────────────────────────────────────

    /**
     * Stages `commit` behind the user's confirmation (R4 single slot). The returned string is the
     * tool_result — it instructs the model to narrate "prepared", never "done".
     */
    private fun stageForConfirmation(
        kind: IrisPendingAction.Kind,
        summary: String,
        commit: suspend () -> String,
    ): String {
        val staged = actionRouter.stage(IrisPendingAction(kind = kind, summary = summary, commit = commit))
        return if (staged) {
            "Staged for user confirmation: $summary. Tell the user you've prepared it and ask them to confirm."
        } else {
            STAGE_REJECTED
        }
    }

    // ── Read-only tools ──────────────────────────────────────────────────────────────────────

    private suspend fun getUserTrips(input: JSONObject): String {
        val filter = input.optString("filter").trim().lowercase(Locale.US).ifEmpty { "all" }
        val todayDate = today()
        val selected = trips.trips()
            .filter { trip ->
                val ended = IsoDates.parseDate(trip.endDate)?.isBefore(todayDate) == true
                when (filter) {
                    "upcoming" -> !ended
                    "past" -> ended
                    else -> true
                }
            }
            .sortedBy { IsoDates.parseDate(it.startDate) ?: LocalDate.MAX }
        if (selected.isEmpty()) {
            return when (filter) {
                "upcoming" -> "No upcoming trips on the itinerary."
                "past" -> "No past trips on file."
                else -> "No trips on the itinerary yet."
            }
        }
        return buildString {
            append(if (filter == "all") "Your trips:" else "Your $filter trips:")
            selected.forEach { trip ->
                append("\n• ${trip.name} — ${trip.destination} (${trip.startDate} → ${trip.endDate})")
                val packed = trip.packingList.count { it.isPacked }
                append("\n  Packing list: ")
                append(
                    if (trip.packingList.isEmpty()) "empty"
                    else "${trip.packingList.size} item(s), $packed packed",
                )
                val items = trip.sortedItems
                items.take(MAX_ITEMS_PER_TRIP).forEach { item ->
                    append("\n  - ${item.startDate.take(10)}: ${item.title} (${item.type.label})")
                }
                if (items.size > MAX_ITEMS_PER_TRIP) {
                    append("\n  …and ${items.size - MAX_ITEMS_PER_TRIP} more itinerary item(s)")
                }
            }
        }
    }

    private suspend fun getWeather(input: JSONObject): String {
        val location = input.optString("location").trim()
        if (location.isEmpty()) return "getWeather needs a location — a city name or IATA airport code."
        val report = weatherService.current(location)
            ?: return "Couldn't fetch live weather for \"$location\" right now."
        return "Current weather in ${report.location}: ${report.tempF.roundToInt()}°F, " +
            "${report.conditions}, wind ${report.windMph.roundToInt()} mph."
    }

    private suspend fun getVisaAndCountryEssentials(input: JSONObject): String {
        val country = input.optString("country").trim()
        if (country.isEmpty()) return "getVisaAndCountryEssentials needs the destination country."
        return dataSources.visaEssentials.essentials(country)
    }

    private suspend fun rememberUserPreference(input: JSONObject): String {
        val value = input.optString("value").trim()
        if (value.isEmpty()) return "rememberUserPreference needs the preference text to remember."
        val category = IrisPreferenceCategory.fromWire(input.optString("category"))
        memory.remember(category, value)
        return "Remembered under ${category.wireName}: $value"
    }

    private suspend fun getLearnedTravelProfile(): String {
        val summary = runCatching { profileProvider.summaryForPrompt() }.getOrDefault("").trim()
        return summary.ifEmpty {
            "No learned travel profile yet — it fills in as the user checks in, completes trips, and logs expenses."
        }
    }

    private suspend fun getDepartureRecommendation(input: JSONObject): String {
        val origin = input.optString("originAirportIATA").trim()
        if (origin.isEmpty()) return "getDepartureRecommendation needs originAirportIATA (e.g. LAS)."
        val departureIso = input.optString("scheduledDepartureISO").trim()
        if (departureIso.isEmpty()) {
            return "getDepartureRecommendation needs scheduledDepartureISO (e.g. 2026-07-18T07:00:00Z)."
        }
        return dataSources.departure.recommendation(
            originIata = origin.uppercase(Locale.US),
            scheduledDepartureIso = departureIso,
            lane = input.optString("lane").trim().takeIf { it.isNotEmpty() },
            lat = if (input.has("currentLatitude")) input.optDouble("currentLatitude") else null,
            lng = if (input.has("currentLongitude")) input.optDouble("currentLongitude") else null,
        )
    }

    // ── Navigation tools (immediate) ─────────────────────────────────────────────────────────

    private fun openScreen(input: JSONObject): String {
        val screen = IrisScreen.fromWire(input.optString("screen"))
            ?: return "openScreen needs one of: ${IrisScreen.wireNames.joinToString(", ")}."
        actionRouter.navigate(IrisNavEvent.OpenScreen(screen))
        return "Opening ${screen.label} now."
    }

    private fun trackFlight(input: JSONObject): String {
        val ident = input.optString("flightNumber").trim().replace(" ", "").uppercase(Locale.US)
        if (ident.isEmpty()) return "trackFlight needs a flight number like DL1423."
        actionRouter.navigate(IrisNavEvent.TrackFlight(ident))
        return "Opening the Flight Tracker and running a live search for $ident."
    }

    // ── Staged tools (confirm-before-commit) ─────────────────────────────────────────────────

    private suspend fun logExpense(input: JSONObject): String {
        if (!input.has("amount")) return "logExpense needs a numeric amount — ask the user how much was spent."
        val amount = input.optDouble("amount", 0.0)
        if (amount <= 0.0) return "logExpense needs a positive amount — ask the user how much was spent."
        val merchant = input.optString("merchant").trim()
        if (merchant.isEmpty()) return "logExpense needs the merchant — ask the user where the money was spent."
        val currency = input.optString("currency").trim().uppercase(Locale.US).ifEmpty { "USD" }
        val explicit = runCatching {
            ExpenseCategory.valueOf(input.optString("category").trim().uppercase(Locale.US))
        }.getOrNull()
        // No category from the model → try the on-device categorizer, else OTHER. The categorizer
        // contract forbids MILEAGE (user-declared only) — coerce defensively anyway.
        val category = explicit
            ?: runCatching { categorizer.categorize(merchant) }.getOrNull()
                ?.takeUnless { it == ExpenseCategory.MILEAGE }
            ?: ExpenseCategory.OTHER
        return stageForConfirmation(
            kind = IrisPendingAction.Kind.LOG_EXPENSE,
            summary = "Log expense: $currency ${money(amount)} at $merchant (${category.label})",
        ) {
            expenses.add(
                Expense(
                    amount = amount,
                    currency = currency,
                    category = category,
                    merchant = merchant,
                    date = today().toString(),
                ),
            )
            // Learning seam (plan A4): a confirmed IRIS expense feeds the profile's spend stats,
            // same signal as the Expenses screen. Consent-gated + silent inside the store.
            runCatching {
                signals.record(
                    TravelSignal(
                        kind = TravelSignal.Kind.EXPENSE_LOGGED,
                        value = merchant,
                        attributes = mapOf(
                            TravelSignal.Attr.AMOUNT to amount.toString(),
                            TravelSignal.Attr.CURRENCY to currency,
                            TravelSignal.Attr.CATEGORY to category.name.lowercase(Locale.US),
                        ),
                        timestamp = now().toString(),
                        source = "iris",
                    ),
                )
            }
            "Logged $currency ${money(amount)} at $merchant (${category.label})."
        }
    }

    private fun addTrip(input: JSONObject): String {
        val destination = input.optString("destination").trim()
        if (destination.isEmpty()) return "addTrip needs a destination — ask the user where they're going."
        val startRaw = input.optString("startDate").trim()
        val start = IsoDates.parseDate(startRaw)
            ?: return "addTrip needs startDate as an ISO date (yyyy-MM-dd); got \"$startRaw\"."
        val endRaw = input.optString("endDate").trim()
        val end = IsoDates.parseDate(endRaw)
            ?: return "addTrip needs endDate as an ISO date (yyyy-MM-dd); got \"$endRaw\"."
        if (end.isBefore(start)) {
            return "addTrip: endDate ($end) is before startDate ($start) — ask the user to correct the dates."
        }
        val name = input.optString("name").trim().ifEmpty { "Trip to $destination" }
        return stageForConfirmation(
            kind = IrisPendingAction.Kind.ADD_TRIP,
            summary = "Add trip \"$name\" — $destination ($start → $end)",
        ) {
            val trip = Trip(
                name = name,
                destination = destination,
                startDate = start.toString(),
                endDate = end.toString(),
            )
            trips.upsert(trip)
            // Learning seam (plan A4): a retroactively-added past trip records its tripCompleted
            // signal now (source "iris"); future trips are picked up by the store's completion
            // watcher when they actually end. Consent-gated + idempotent inside the store.
            runCatching { signals.tripAdded(trip) }
            "Added trip \"$name\" to $destination ($start – $end)."
        }
    }

    private suspend fun checkInForFlight(input: JSONObject): String {
        val provided = input.optString("flightNumber").trim()
        val ident = if (provided.isNotEmpty()) {
            provided.replace(" ", "").uppercase(Locale.US)
        } else {
            dataSources.checkin.nextFlightIdent()
                ?: TripQueries.nextFlight(trips.trips(), now())?.ident
                ?: return "No upcoming flight found to check in for — ask the user which flight they mean."
        }
        return stageForConfirmation(
            kind = IrisPendingAction.Kind.CHECK_IN,
            summary = "Check in for flight $ident",
        ) {
            dataSources.checkin.markCheckedIn(ident)
        }
    }

    private suspend fun generatePackingList(input: JSONObject): String {
        val tripName = input.optString("tripName").trim()
        val all = trips.trips()
        val trip = if (tripName.isNotEmpty()) {
            all.firstOrNull {
                it.name.contains(tripName, ignoreCase = true) ||
                    it.destination.contains(tripName, ignoreCase = true)
            } ?: return "No trip matches \"$tripName\" — ask the user which trip they mean."
        } else {
            TripQueries.activeTrip(all, today())
                ?: return "No active or upcoming trip to pack for — add a trip first."
        }
        return stageForConfirmation(
            kind = IrisPendingAction.Kind.GENERATE_PACKING_LIST,
            summary = "Generate a packing list for ${trip.name} (${trip.destination})",
        ) {
            val start = IsoDates.parseDate(trip.startDate)
            val end = IsoDates.parseDate(trip.endDate)
            val days = if (start != null && end != null && !end.isBefore(start)) {
                (ChronoUnit.DAYS.between(start, end) + 1).toInt()
            } else {
                DEFAULT_TRIP_DAYS
            }
            val weather = runCatching {
                weatherService.current(trip.destination.substringBefore(",").trim())
            }.getOrNull()
            val generated = PackingListGenerator.generate(days, weather)
            val existing = trip.packingList.map { it.name.lowercase(Locale.US) }.toSet()
            val newItems = generated.filterNot { it.lowercase(Locale.US) in existing }
            val updated = trip.copy(packingList = trip.packingList + newItems.map { PackingItem(name = it) })
            trips.upsert(updated)
            buildString {
                append("Generated a ${updated.packingList.size}-item packing list for ${trip.name}")
                if (weather != null) {
                    append(" — packed for ${weather.conditions.lowercase(Locale.US)}, ~${weather.tempF.roundToInt()}°F in ${weather.location}")
                }
                append(". It's on the trip's packing list in the Itinerary tab.")
            }
        }
    }

    private suspend fun submitExpenses(input: JSONObject): String {
        val providerRaw = input.optString("provider").trim().lowercase(Locale.US)
        val provider = SUBMIT_PROVIDERS[providerRaw]
            ?: return "submitExpenses needs a provider: one of email, expensify, ramp, brex, divvy."
        val tripName = input.optString("tripName").trim().takeIf { it.isNotEmpty() }
        val count = expenses.expenses().size
        if (count == 0) return "There are no logged expenses to submit yet."
        val destination = if (providerRaw == "email") "by email" else "to $provider"
        val forTrip = tripName?.let { " for trip \"$it\"" }.orEmpty()
        return stageForConfirmation(
            kind = IrisPendingAction.Kind.SUBMIT_EXPENSES,
            summary = "Submit $count expense${plural(count)} $destination$forTrip",
        ) {
            val current = expenses.expenses()
            val totals = current
                .groupBy { it.currency }
                .map { (currency, group) -> "$currency ${money(group.sumOf { it.amount })}" }
                .joinToString("; ")
            "Submitted ${current.size} expense${plural(current.size)} ($totals) $destination$forTrip."
        }
    }

    // ── flightActions ────────────────────────────────────────────────────────────────────────

    private suspend fun flightActions(input: JSONObject): String {
        val action = input.optString("action").trim()
        return when {
            action.equals("luggageStatus", ignoreCase = true) -> dataSources.luggage.luggageStatus()
            action.equals("notifyLovedOnes", ignoreCase = true) ->
                notifyLovedOnes(input.optString("event").trim().lowercase(Locale.US))
            else -> "flightActions needs an action: notifyLovedOnes or luggageStatus."
        }
    }

    private suspend fun notifyLovedOnes(eventRaw: String): String {
        val landing = eventRaw == "landing"
        val contacts = lovedOnes.all()
        val recipients = contacts.filter { if (landing) it.notifyOnLanding else it.notifyOnTakeoff }
        if (recipients.isEmpty()) {
            return if (contacts.isEmpty()) {
                "No loved ones are saved yet — the user can add them from the Loved Ones screen."
            } else {
                "None of the saved loved ones opted into ${if (landing) "landing" else "takeoff"} notifications."
            }
        }
        val allTrips = trips.trips()
        val body = if (landing) {
            val city = TripQueries.activeTrip(allTrips, today())?.destination
                ?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
                ?: TripQueries.nextFlight(allTrips, now())?.trip?.destination
                    ?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "town"
            SmsTemplates.landing(city)
        } else {
            val ident = dataSources.checkin.nextFlightIdent()
                ?: TripQueries.nextFlight(allTrips, now())?.ident
                ?: "our flight"
            SmsTemplates.takeoff(ident)
        }
        actionRouter.navigate(IrisNavEvent.ComposeSms(recipients.map { it.phoneNumber }, body))
        return "Opened the SMS composer to ${recipients.size} loved one${plural(recipients.size)} " +
            "with: \"$body\" — the user just hits send."
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun plural(count: Int): String = if (count == 1) "" else "s"

    internal companion object {
        /** R10(c): itinerary items included per trip in `getUserTrips`. */
        internal const val MAX_ITEMS_PER_TRIP = 6

        /** Fallback trip length for packing-list sizing when dates don't parse. */
        internal const val DEFAULT_TRIP_DAYS = 5

        /** R4: the tool_result for a staged tool arriving while another action is pending. */
        internal const val STAGE_REJECTED =
            "Another action is already awaiting the user's confirmation — ask them to confirm or cancel it first."

        internal val SUBMIT_PROVIDERS: Map<String, String> = mapOf(
            "email" to "Email",
            "expensify" to "Expensify",
            "ramp" to "Ramp",
            "brex" to "Brex",
            "divvy" to "Divvy",
        )
    }

    // ── Anthropic tool-definition schema ─────────────────────────────────────────────────────

    /** The 14-tool schema sent on every IRIS request (spec §1.3 roster, camelCase, iOS parity). */
    val schema: JSONArray by lazy {
        JSONArray()
            .put(
                tool(
                    name = "getUserTrips",
                    description = "List the user's trips with dates, up to $MAX_ITEMS_PER_TRIP itinerary items each, and packing-list progress. Use when asked about trips, plans, or packing status.",
                    properties = JSONObject()
                        .put("filter", enumProp("Which trips to include; default all", listOf("upcoming", "past", "all"))),
                    required = emptyList(),
                ),
            )
            .put(
                tool(
                    name = "getWeather",
                    description = "Current weather (°F, conditions, wind) for a city or IATA airport code.",
                    properties = JSONObject()
                        .put("location", strProp("City name (\"Paris\") or IATA airport code (\"CDG\")")),
                    required = listOf("location"),
                ),
            )
            .put(
                tool(
                    name = "getVisaAndCountryEssentials",
                    description = "Visa/entry rules plus country essentials (emergency numbers, tipping, plugs, water safety) for a destination country.",
                    properties = JSONObject()
                        .put("country", strProp("Destination country, e.g. \"Japan\"")),
                    required = listOf("country"),
                ),
            )
            .put(
                tool(
                    name = "rememberUserPreference",
                    description = "Persist (or reinforce) a travel preference the user stated, so future sessions recall it.",
                    properties = JSONObject()
                        .put(
                            "category",
                            enumProp(
                                "Preference category",
                                IrisPreferenceCategory.entries.map { it.wireName },
                            ),
                        )
                        .put("value", strProp("The preference itself, e.g. \"window seat\"")),
                    required = listOf("category", "value"),
                ),
            )
            .put(
                tool(
                    name = "getLearnedTravelProfile",
                    description = "The travel profile learned from the user's behavior (seat/airline/hotel patterns, spend, places). Use before recommending.",
                    properties = JSONObject()
                        .put(
                            "aspect",
                            enumProp(
                                "Slice of the profile to focus on; default all",
                                listOf("seat", "airlines", "hotels", "spend", "places", "all"),
                            ),
                        ),
                    required = emptyList(),
                ),
            )
            .put(
                tool(
                    name = "getDepartureRecommendation",
                    description = "Leave-by recommendation for a departure: drive time, security wait, buffers, and the time to leave.",
                    properties = JSONObject()
                        .put("originAirportIATA", strProp("Departure airport IATA code, e.g. \"LAS\""))
                        .put("scheduledDepartureISO", strProp("Scheduled departure, ISO-8601, e.g. 2026-07-18T07:00:00Z"))
                        .put("lane", strProp("Optional security lane, e.g. \"PreCheck\" or \"CLEAR\""))
                        .put("currentLatitude", numProp("Traveler's current latitude, if known"))
                        .put("currentLongitude", numProp("Traveler's current longitude, if known")),
                    required = listOf("originAirportIATA", "scheduledDepartureISO"),
                ),
            )
            .put(
                tool(
                    name = "submitExpenses",
                    description = "Submit the logged expenses to an expense provider. STAGED: the user must confirm before anything is sent.",
                    properties = JSONObject()
                        .put(
                            "provider",
                            enumProp("Where to submit", SUBMIT_PROVIDERS.keys.toList()),
                        )
                        .put("tripName", strProp("Optional trip to scope the report to")),
                    required = listOf("provider"),
                ),
            )
            .put(
                tool(
                    name = "openScreen",
                    description = "Navigate the app to a screen immediately. Use when the user asks to see or open something.",
                    properties = JSONObject()
                        .put("screen", enumProp("Destination screen", IrisScreen.wireNames)),
                    required = listOf("screen"),
                ),
            )
            .put(
                tool(
                    name = "trackFlight",
                    description = "Open the Flight Tracker and run a live search for a flight. Immediate — no confirmation needed.",
                    properties = JSONObject()
                        .put("flightNumber", strProp("Flight ident, e.g. \"DL1423\"")),
                    required = listOf("flightNumber"),
                ),
            )
            .put(
                tool(
                    name = "logExpense",
                    description = "Record a travel expense. STAGED: the user must confirm before it is saved.",
                    properties = JSONObject()
                        .put("amount", numProp("Amount spent, in the given currency"))
                        .put("merchant", strProp("Where the money was spent"))
                        .put("currency", strProp("ISO currency code; default USD"))
                        .put(
                            "category",
                            enumProp(
                                "Expense category; omit to auto-categorize",
                                ExpenseCategory.entries.map { it.name.lowercase(Locale.US) },
                            ),
                        ),
                    required = listOf("amount", "merchant"),
                ),
            )
            .put(
                tool(
                    name = "addTrip",
                    description = "Add a trip to the itinerary. STAGED: the user must confirm before it is created. endDate must be on or after startDate.",
                    properties = JSONObject()
                        .put("destination", strProp("Destination city or place"))
                        .put("startDate", strProp("Start date, yyyy-MM-dd"))
                        .put("endDate", strProp("End date, yyyy-MM-dd"))
                        .put("name", strProp("Optional short trip name")),
                    required = listOf("destination", "startDate", "endDate"),
                ),
            )
            .put(
                tool(
                    name = "checkInForFlight",
                    description = "Check the user in for a flight. STAGED: the user must confirm first. Omit flightNumber to use the next upcoming flight.",
                    properties = JSONObject()
                        .put("flightNumber", strProp("Flight ident, e.g. \"DL1423\"; default = next upcoming flight")),
                    required = emptyList(),
                ),
            )
            .put(
                tool(
                    name = "generatePackingList",
                    description = "Generate a weather-aware packing list for a trip. STAGED: the user must confirm first. Omit tripName to use the active or next trip.",
                    properties = JSONObject()
                        .put("tripName", strProp("Trip name or destination; default = active/next trip")),
                    required = emptyList(),
                ),
            )
            .put(
                tool(
                    name = "flightActions",
                    description = "Flight-day helpers: notifyLovedOnes opens a prefilled SMS to the user's loved ones (never auto-sent); luggageStatus reports tracked bags.",
                    properties = JSONObject()
                        .put("action", enumProp("Which helper", listOf("notifyLovedOnes", "luggageStatus")))
                        .put("event", enumProp("For notifyLovedOnes: which message", listOf("takeoff", "landing"))),
                    required = listOf("action"),
                ),
            )
    }

    private fun tool(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put(
                "input_schema",
                JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", JSONArray(required)),
            )

    private fun strProp(description: String) = JSONObject().put("type", "string").put("description", description)

    private fun numProp(description: String) = JSONObject().put("type", "number").put("description", description)

    private fun enumProp(description: String, values: List<String>) = JSONObject()
        .put("type", "string")
        .put("description", description)
        .put("enum", JSONArray(values))
}

/**
 * Deterministic rule-based packing list (spec §1.3 `generatePackingList`): base travel kit +
 * clothing scaled to trip length + weather-driven extras. Pure so tests can pin it; runs inside
 * the staged commit, never at dispatch time.
 */
internal object PackingListGenerator {

    fun generate(days: Int, weather: WeatherReport?): List<String> {
        val clothingCount = days.coerceIn(1, 10)
        val items = mutableListOf(
            "Passport / ID",
            "Wallet & boarding passes",
            "Phone + charger",
            "Toiletries kit",
            "Medications",
            "Underwear ×$clothingCount",
            "Socks ×$clothingCount",
            "Shirts/tops ×$clothingCount",
            "Pants/bottoms ×${(clothingCount / 2).coerceAtLeast(1)}",
            "Comfortable shoes",
        )
        if (days >= 7) items += "Laundry bag"
        when {
            weather == null -> items += "Light jacket (check the forecast)"
            weather.tempF < 45 -> {
                items += "Warm coat"
                items += "Gloves & beanie"
                items += "Thermal layers"
            }
            weather.tempF < 65 -> {
                items += "Light jacket"
                items += "Layering sweater"
            }
            else -> {
                items += "Sunscreen"
                items += "Sunglasses"
            }
        }
        if (weather != null && RAINY_MARKERS.any { weather.conditions.contains(it, ignoreCase = true) }) {
            items += "Compact umbrella"
            items += "Rain jacket"
        }
        return items
    }

    private val RAINY_MARKERS = listOf("rain", "drizzle", "shower", "thunder")
}
