package com.jetsetter.pro.feature.groundtransport

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * In-memory mock backing the Ground Transport screen. Mirrors the @Singleton @Inject repository
 * convention used elsewhere (e.g. ExpensesRepository) — no network. Fares are *derived* from a
 * per-service rate card applied to a route (distance + drive time), so every quoted number stays
 * consistent with the selected direction; nothing is a static figure that contradicts the inputs.
 * Estimates are seeded for a Las Vegas Strip ⇄ LAS transfer.
 *
 * User-mutable screen state (direction, party size, sort, the active booking) is persisted as a
 * single Moshi-serialized JSON object via [ModuleStateStore] so it survives restarts.
 */
@Singleton
class GroundtransportRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    /** The airport anchoring every estimate on this screen. */
    val airportName: String = "LAS · Harry Reid International"

    /** The rider's non-airport endpoint (their hotel for this trip). */
    private val hotelName: String = "Bellagio Resort & Casino"

    /**
     * A provider's pricing inputs. Fares are computed as
     * `max(base + perMile·miles + perMinute·drive + bookingFee + airportFee, minimumFare) · surge`.
     */
    private data class Rate(
        val id: String,
        val service: String,
        val provider: String,
        val vehicleClass: GroundTransportVehicleClass,
        val capacity: Int,
        val baseFare: Double,
        val perMile: Double,
        val perMinute: Double,
        val bookingFee: Double,
        val minimumFare: Double,
        val baseEta: Int,
        val rating: Double,
        /** Rideshare tiers surge with airport demand; metered taxis are regulated and don't. */
        val canSurge: Boolean,
    )

    private val rates: List<Rate> = listOf(
        Rate("uberx", "UberX", "Uber", GroundTransportVehicleClass.STANDARD, 4, 2.55, 1.75, 0.35, 3.00, 9.0, 4, 4.8, true),
        Rate("lyft", "Lyft", "Lyft", GroundTransportVehicleClass.STANDARD, 4, 2.40, 1.70, 0.33, 3.15, 9.0, 5, 4.7, true),
        Rate("lyft-xl", "Lyft XL", "Lyft · 6 seats", GroundTransportVehicleClass.XL, 6, 3.50, 2.60, 0.45, 3.50, 14.0, 8, 4.7, true),
        Rate("uber-black", "Uber Black", "Uber · Premium sedan", GroundTransportVehicleClass.PREMIUM, 4, 10.00, 4.50, 0.85, 0.0, 50.0, 7, 4.9, true),
        Rate("taxi", "Taxi", "Yellow Cab · metered", GroundTransportVehicleClass.TAXI, 4, 3.50, 2.83, 0.50, 0.0, 0.0, 3, 4.5, false),
    )

    /** Route facts per direction: (distanceMiles, driveMinutes). From-airport routing runs a touch longer. */
    private fun route(direction: GroundTransportDirection): Pair<Double, Int> = when (direction) {
        GroundTransportDirection.TO_AIRPORT -> 4.5 to 12
        GroundTransportDirection.FROM_AIRPORT -> 5.0 to 15
    }

    /** Where the rider is picked up for the given [direction]. */
    fun pickupLabel(direction: GroundTransportDirection): String = when (direction) {
        GroundTransportDirection.TO_AIRPORT -> hotelName
        GroundTransportDirection.FROM_AIRPORT -> airportName
    }

    /** Where the rider is dropped off for the given [direction]. */
    fun dropoffLabel(direction: GroundTransportDirection): String = when (direction) {
        GroundTransportDirection.TO_AIRPORT -> airportName
        GroundTransportDirection.FROM_AIRPORT -> hotelName
    }

    /**
     * Computes ride quotes for the given [direction]. Pickups *from* the airport carry the terminal
     * pickup fee + queue (longer driver ETA), and rideshare tiers add airport demand surge. A short
     * delay simulates fetching a live quote so the screen's loading state is exercised.
     */
    suspend fun quote(direction: GroundTransportDirection): GroundTransportQuote {
        delay(450)
        val (miles, driveMinutes) = route(direction)
        val fromAirport = direction == GroundTransportDirection.FROM_AIRPORT
        val options = rates.map { rate ->
            // Airport pickups add a fee: rideshare terminal fee vs. the taxi airport trip charge.
            val airportFee = if (fromAirport) (if (rate.canSurge) 2.45 else 4.00) else 0.0
            val surge = if (fromAirport && rate.canSurge) 1.35 else 1.0
            val queue = if (fromAirport) (if (rate.canSurge) 3 else 2) else 0

            val metered = rate.baseFare +
                rate.perMile * miles +
                rate.perMinute * driveMinutes +
                rate.bookingFee +
                airportFee
            val fare = max(metered, rate.minimumFare) * surge

            GroundTransportOption(
                id = rate.id,
                service = rate.service,
                provider = rate.provider,
                vehicleClass = rate.vehicleClass,
                capacity = rate.capacity,
                etaMinutes = rate.baseEta + queue,
                // ±~10% quote spread, rounded to whole dollars the way ride apps display ranges.
                priceLow = floor(fare * 0.94),
                priceHigh = ceil(fare * 1.12),
                surgeMultiplier = surge,
                rating = rate.rating,
            )
        }
        return GroundTransportQuote(distanceMiles = miles, rideMinutes = driveMinutes, options = options)
    }

    /** Loads persisted screen state, falling back to defaults on first run or a parse failure. */
    suspend fun loadState(): GroundTransportPersisted {
        val json = store.read(KEY) ?: return GroundTransportPersisted()
        return runCatching { adapter.fromJson(json) }.getOrNull() ?: GroundTransportPersisted()
    }

    /** Persists the full screen state as one JSON object. */
    suspend fun saveState(state: GroundTransportPersisted) {
        store.save(KEY, adapter.toJson(state))
    }

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(GroundTransportPersisted::class.java)

    private companion object {
        const val KEY = "groundtransport_state"
    }
}
