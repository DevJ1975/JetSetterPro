package com.jetsetter.pro.feature.groundtransport

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.data.remote.lyft.LyftCostEstimate
import com.jetsetter.pro.core.data.remote.lyft.LyftService
import com.jetsetter.pro.core.data.remote.uber.UberPriceEstimate
import com.jetsetter.pro.core.data.remote.uber.UberService
import com.jetsetter.pro.core.travel.AirportCoordinates
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Backing store for the Ground Transport screen. Live-when-configured (plan B7): [quote] asks
 * Uber ([UberService]) and Lyft ([LyftService]) for real price estimates when their keys are
 * present and merges those options ahead of the static rate card — live rows are clearly sourced
 * ("Uber · live estimate") and displace the same brand's rate-card rows so a fare never appears
 * twice; an unconfigured key, a fetch failure, or an unmappable payload leave the rate-card
 * quote unchanged (the screen never surfaces an error state it can't act on).
 *
 * The rate card *derives* fares from a route (distance + drive time), so every mock number stays
 * consistent with the selected direction. Estimates are seeded for a Las Vegas Strip ⇄ LAS
 * transfer; the live coordinates come from [AirportCoordinates] (LAS) plus the hotel anchor
 * below — bare coordinates are the only thing sent off-device (plan R10f).
 *
 * User-mutable screen state (direction, party size, sort, the active booking) is persisted as a
 * single Moshi-serialized JSON object via [ModuleStateStore] so it survives restarts.
 */
@Singleton
class GroundtransportRepository @Inject constructor(
    private val store: ModuleStateStore,
    private val uber: UberService,
    private val lyft: LyftService,
) {

    /** The airport anchoring every estimate on this screen. */
    val airportName: String = "LAS · Harry Reid International"

    /** The rider's non-airport endpoint (their hotel for this trip). */
    private val hotelName: String = "Bellagio Resort & Casino"

    /** Hotel anchor at the same ~2-decimal accuracy as [AirportCoordinates] (plenty for a quote). */
    private val hotelLatitude = 36.11
    private val hotelLongitude = -115.18

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
     * Computes ride quotes for the given [direction]: live Uber/Lyft estimates first (when
     * configured and mappable), then the derived rate card minus any brand the live fetch
     * covered. Pickups *from* the airport carry the terminal pickup fee + queue (longer driver
     * ETA) on rate-card rows, and rideshare tiers add airport demand surge. On the pure-mock
     * path a short delay simulates fetching so the screen's loading state is exercised.
     */
    suspend fun quote(direction: GroundTransportDirection): GroundTransportQuote {
        val live = liveOptions(direction)
        if (live.isEmpty()) delay(450)

        val (miles, driveMinutes) = route(direction)
        val fromAirport = direction == GroundTransportDirection.FROM_AIRPORT
        val liveBrands = live.map { it.provider.substringBefore(" ·") }.toSet()
        val mockOptions = rates
            .filterNot { it.provider.substringBefore(" ·") in liveBrands }
            .map { rate ->
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
        return GroundTransportQuote(distanceMiles = miles, rideMinutes = driveMinutes, options = live + mockOptions)
    }

    /**
     * Live estimates from every configured ride service, fetched concurrently and mapped through
     * the pure DTO→option functions below. Any failure (or an unknown airport code) degrades to
     * an empty list — the caller then serves the unchanged rate card.
     */
    private suspend fun liveOptions(direction: GroundTransportDirection): List<GroundTransportOption> {
        if (!uber.isConfigured && !lyft.isConfigured) return emptyList()
        val airport = AirportCoordinates.lookup(AIRPORT_IATA) ?: return emptyList()

        val (startLat, startLng) = when (direction) {
            GroundTransportDirection.TO_AIRPORT -> hotelLatitude to hotelLongitude
            GroundTransportDirection.FROM_AIRPORT -> airport.latitude to airport.longitude
        }
        val (endLat, endLng) = when (direction) {
            GroundTransportDirection.TO_AIRPORT -> airport.latitude to airport.longitude
            GroundTransportDirection.FROM_AIRPORT -> hotelLatitude to hotelLongitude
        }

        return coroutineScope {
            val uberEstimates = async {
                if (!uber.isConfigured) return@async emptyList()
                uber.priceEstimates(startLat, startLng, endLat, endLng).getOrNull().orEmpty()
                    .mapNotNull { it.toGroundTransportOption() }
            }
            val lyftEstimates = async {
                if (!lyft.isConfigured) return@async emptyList()
                lyft.costEstimates(startLat, startLng, endLat, endLng).getOrNull().orEmpty()
                    .mapNotNull { it.toGroundTransportOption() }
            }
            uberEstimates.await() + lyftEstimates.await()
        }
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
        const val AIRPORT_IATA = "LAS"
    }
}

// ── Live mapping (pure, unit-tested in RideEstimateMappingTest) ──────────────

/** Brand labels the merge keys on — a live brand displaces its own rate-card rows only. */
private const val UBER_LIVE_PROVIDER = "Uber · live estimate"
private const val LYFT_LIVE_PROVIDER = "Lyft · live estimate"

/**
 * Maps one Uber product quote onto the screen's option model. Null (option dropped) when the
 * product has no display name, is missing either numeric fare bound (metered products like TAXI
 * only carry the display string), quotes an inverted/negative range, or is priced in a non-USD
 * currency (the option model displays dollars). The price endpoint carries no driver-pickup
 * ETA, so [GroundTransportOption.etaMinutes] falls back to the rate card's baseline for the
 * mapped vehicle class.
 */
internal fun UberPriceEstimate.toGroundTransportOption(): GroundTransportOption? {
    val name = (localizedDisplayName ?: displayName)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val low = lowEstimate ?: return null
    val high = highEstimate ?: return null
    if (low < 0.0 || high < low) return null
    if (!"USD".equals(currencyCode?.trim(), ignoreCase = true)) return null

    val vehicleClass = rideVehicleClass(name)
    return GroundTransportOption(
        id = "uber-live-" + (productId?.trim()?.takeIf { it.isNotEmpty() } ?: name.slugify()),
        service = name,
        provider = UBER_LIVE_PROVIDER,
        vehicleClass = vehicleClass,
        capacity = liveCapacity(vehicleClass),
        etaMinutes = livePickupEtaMinutes(vehicleClass),
        priceLow = low,
        priceHigh = high,
        surgeMultiplier = surgeMultiplier?.takeIf { it > 1.0 } ?: 1.0,
    )
}

/**
 * Maps one Lyft tier quote onto the screen's option model — cents → dollars (`cents / 100.0`),
 * primetime "25%" → 1.25 surge. Null (option dropped) when the tier has no name, is missing
 * either cents bound, quotes an inverted/negative range, or is priced in a non-USD currency.
 * Like Uber, the cost endpoint carries no driver-pickup ETA, so the rate-card baseline for the
 * mapped vehicle class is used.
 */
internal fun LyftCostEstimate.toGroundTransportOption(): GroundTransportOption? {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: rideType?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val minCents = estimatedCostCentsMin ?: return null
    val maxCents = estimatedCostCentsMax ?: return null
    if (minCents < 0 || maxCents < minCents) return null
    if (!"USD".equals(currency?.trim(), ignoreCase = true)) return null

    val vehicleClass = rideVehicleClass("${rideType.orEmpty()} $name")
    return GroundTransportOption(
        id = "lyft-live-" + (rideType?.trim()?.takeIf { it.isNotEmpty() } ?: name.slugify()),
        service = name,
        provider = LYFT_LIVE_PROVIDER,
        vehicleClass = vehicleClass,
        capacity = liveCapacity(vehicleClass),
        etaMinutes = livePickupEtaMinutes(vehicleClass),
        priceLow = minCents / 100.0,
        priceHigh = maxCents / 100.0,
        surgeMultiplier = primetimeMultiplier(primetimePercentage),
    )
}

/**
 * Classifies a product/tier name into the screen's vehicle tiers. Precedence: taxi markers,
 * then premium markers (black/lux/premier — so "Lux Black XL" lands PREMIUM, not XL), then
 * XL/plus markers, else STANDARD.
 */
internal fun rideVehicleClass(name: String): GroundTransportVehicleClass {
    val n = name.lowercase()
    return when {
        "taxi" in n || "cab" in n -> GroundTransportVehicleClass.TAXI
        "black" in n || "lux" in n || "premier" in n -> GroundTransportVehicleClass.PREMIUM
        "xl" in n || "plus" in n || "suv" in n -> GroundTransportVehicleClass.XL
        else -> GroundTransportVehicleClass.STANDARD
    }
}

/**
 * Parses Lyft's `primetime_percentage` display string ("25%") into the option model's fare
 * multiplier (1.25). "0%", junk, and null all mean no surge (1.0).
 */
internal fun primetimeMultiplier(primetimePercentage: String?): Double =
    primetimePercentage?.trim()?.removeSuffix("%")?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?.let { 1.0 + it / 100.0 }
        ?: 1.0

/** Seats by tier — the same assumption the static rate card makes. */
private fun liveCapacity(vehicleClass: GroundTransportVehicleClass): Int = when (vehicleClass) {
    GroundTransportVehicleClass.XL -> 6
    else -> 4
}

/**
 * Driver-pickup baseline by tier (the price endpoints don't return pickup ETA) — mirrors the
 * static rate card's baseEta values so live rows sort believably against retained mock rows.
 */
private fun livePickupEtaMinutes(vehicleClass: GroundTransportVehicleClass): Int = when (vehicleClass) {
    GroundTransportVehicleClass.STANDARD -> 5
    GroundTransportVehicleClass.PREMIUM -> 7
    GroundTransportVehicleClass.XL -> 8
    GroundTransportVehicleClass.TAXI -> 3
}

/** "Uber Black" → "uber-black" — a stable id fragment when the wire id is missing. */
private fun String.slugify(): String = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
