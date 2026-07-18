package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.lyft.LyftCostEstimate
import com.jetsetter.pro.core.data.remote.uber.UberPriceEstimate
import com.jetsetter.pro.feature.groundtransport.GroundTransportVehicleClass
import com.jetsetter.pro.feature.groundtransport.primetimeMultiplier
import com.jetsetter.pro.feature.groundtransport.rideVehicleClass
import com.jetsetter.pro.feature.groundtransport.toGroundTransportOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure parts of the ride-estimate seam (plan B7, keyed Uber + Lyft): the wire
 * DTO→[com.jetsetter.pro.feature.groundtransport.GroundTransportOption] mappings the
 * Ground Transport live-over-rate-card merge relies on — including Lyft's cents→dollars
 * display conversion and primetime→surge parsing. Pure JVM — no network, no Android.
 */
class RideEstimateMappingTest {

    // ── Uber DTO → option ────────────────────────────────────────────────────

    private fun uberDto(
        productId: String? = "prod-123",
        displayName: String? = "UberX",
        localizedDisplayName: String? = null,
        estimate: String? = "$23-29",
        lowEstimate: Double? = 23.0,
        highEstimate: Double? = 29.0,
        currencyCode: String? = "USD",
        surgeMultiplier: Double? = null,
        duration: Long? = 900L,
        distance: Double? = 4.7,
    ) = UberPriceEstimate(
        productId = productId,
        displayName = displayName,
        localizedDisplayName = localizedDisplayName,
        estimate = estimate,
        lowEstimate = lowEstimate,
        highEstimate = highEstimate,
        currencyCode = currencyCode,
        surgeMultiplier = surgeMultiplier,
        duration = duration,
        distance = distance,
    )

    @Test
    fun uber_fullPayload_maps() {
        val option = uberDto().toGroundTransportOption()

        assertNotNull(option)
        assertEquals("uber-live-prod-123", option!!.id)
        assertEquals("UberX", option.service)
        assertEquals("Uber · live estimate", option.provider)
        assertEquals(GroundTransportVehicleClass.STANDARD, option.vehicleClass)
        assertEquals(4, option.capacity)
        assertEquals(23.0, option.priceLow, 1e-9)
        assertEquals(29.0, option.priceHigh, 1e-9)
        assertEquals(1.0, option.surgeMultiplier, 1e-9)
        assertFalse(option.surge)
    }

    @Test
    fun uber_localizedName_winsOverDisplayName() {
        val option = uberDto(displayName = "UberX", localizedDisplayName = "UberX Vegas").toGroundTransportOption()
        assertEquals("UberX Vegas", option!!.service)
    }

    @Test
    fun uber_missingName_mapsToNull() {
        assertNull(uberDto(displayName = null, localizedDisplayName = null).toGroundTransportOption())
        assertNull(uberDto(displayName = "  ", localizedDisplayName = null).toGroundTransportOption())
    }

    @Test
    fun uber_meteredProductWithoutBounds_mapsToNull() {
        // TAXI-style metered products only carry the display estimate string — no honest range to show.
        assertNull(uberDto(lowEstimate = null, highEstimate = 29.0).toGroundTransportOption())
        assertNull(uberDto(lowEstimate = 23.0, highEstimate = null).toGroundTransportOption())
    }

    @Test
    fun uber_invertedOrNegativeRange_mapsToNull() {
        assertNull(uberDto(lowEstimate = 30.0, highEstimate = 20.0).toGroundTransportOption())
        assertNull(uberDto(lowEstimate = -5.0, highEstimate = 20.0).toGroundTransportOption())
    }

    @Test
    fun uber_nonUsdCurrency_mapsToNull() {
        // The option model displays dollars — a EUR quote must not render behind a "$".
        assertNull(uberDto(currencyCode = "EUR").toGroundTransportOption())
        assertNull(uberDto(currencyCode = null).toGroundTransportOption())
    }

    @Test
    fun uber_usdCurrency_isCaseAndPaddingInsensitive() {
        assertNotNull(uberDto(currencyCode = " usd ").toGroundTransportOption())
    }

    @Test
    fun uber_surgeMultiplier_passesThrough() {
        val option = uberDto(surgeMultiplier = 1.4).toGroundTransportOption()
        assertEquals(1.4, option!!.surgeMultiplier, 1e-9)
        assertTrue(option.surge)
    }

    @Test
    fun uber_noSurgeOrBaselineMultiplier_normalizesToOne() {
        assertEquals(1.0, uberDto(surgeMultiplier = null).toGroundTransportOption()!!.surgeMultiplier, 1e-9)
        assertEquals(1.0, uberDto(surgeMultiplier = 1.0).toGroundTransportOption()!!.surgeMultiplier, 1e-9)
    }

    @Test
    fun uber_missingProductId_fallsBackToNameSlug() {
        val option = uberDto(productId = null, displayName = "Uber Black").toGroundTransportOption()
        assertEquals("uber-live-uber-black", option!!.id)
    }

    // ── Lyft DTO → option (cents → display dollars) ──────────────────────────

    private fun lyftDto(
        rideType: String? = "lyft",
        displayName: String? = "Lyft",
        currency: String? = "USD",
        centsMin: Long? = 950L,
        centsMax: Long? = 1234L,
        durationSeconds: Long? = 840L,
        distanceMiles: Double? = 4.6,
        primetime: String? = null,
    ) = LyftCostEstimate(
        rideType = rideType,
        displayName = displayName,
        currency = currency,
        estimatedCostCentsMin = centsMin,
        estimatedCostCentsMax = centsMax,
        estimatedDurationSeconds = durationSeconds,
        estimatedDistanceMiles = distanceMiles,
        primetimePercentage = primetime,
    )

    @Test
    fun lyft_fullPayload_maps_centsBecomeDollars() {
        val option = lyftDto().toGroundTransportOption()

        assertNotNull(option)
        assertEquals("lyft-live-lyft", option!!.id)
        assertEquals("Lyft", option.service)
        assertEquals("Lyft · live estimate", option.provider)
        assertEquals(GroundTransportVehicleClass.STANDARD, option.vehicleClass)
        // 950 cents → $9.50, 1234 cents → $12.34 — the cents→display contract.
        assertEquals(9.50, option.priceLow, 1e-9)
        assertEquals(12.34, option.priceHigh, 1e-9)
        assertEquals(1.0, option.surgeMultiplier, 1e-9)
    }

    @Test
    fun lyft_missingEitherCentsBound_mapsToNull() {
        assertNull(lyftDto(centsMin = null).toGroundTransportOption())
        assertNull(lyftDto(centsMax = null).toGroundTransportOption())
    }

    @Test
    fun lyft_invertedOrNegativeCents_mapsToNull() {
        assertNull(lyftDto(centsMin = 1500L, centsMax = 900L).toGroundTransportOption())
        assertNull(lyftDto(centsMin = -100L, centsMax = 900L).toGroundTransportOption())
    }

    @Test
    fun lyft_nonUsdCurrency_mapsToNull() {
        assertNull(lyftDto(currency = "CAD").toGroundTransportOption())
        assertNull(lyftDto(currency = null).toGroundTransportOption())
    }

    @Test
    fun lyft_missingDisplayName_fallsBackToRideType() {
        val option = lyftDto(displayName = null, rideType = "lyft_lux").toGroundTransportOption()
        assertEquals("lyft_lux", option!!.service)
    }

    @Test
    fun lyft_missingBothNames_mapsToNull() {
        assertNull(lyftDto(displayName = null, rideType = null).toGroundTransportOption())
    }

    @Test
    fun lyft_xlTier_getsSixSeats() {
        val option = lyftDto(rideType = "lyft_plus", displayName = "Lyft XL").toGroundTransportOption()
        assertEquals(GroundTransportVehicleClass.XL, option!!.vehicleClass)
        assertEquals(6, option.capacity)
    }

    @Test
    fun lyft_luxTier_isPremium() {
        val option = lyftDto(rideType = "lyft_lux", displayName = "Lyft Lux").toGroundTransportOption()
        assertEquals(GroundTransportVehicleClass.PREMIUM, option!!.vehicleClass)
    }

    @Test
    fun lyft_primetime_mapsToSurgeMultiplier() {
        val option = lyftDto(primetime = "25%").toGroundTransportOption()
        assertEquals(1.25, option!!.surgeMultiplier, 1e-9)
        assertTrue(option.surge)
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    @Test
    fun vehicleClass_precedence_taxiThenPremiumThenXl() {
        assertEquals(GroundTransportVehicleClass.TAXI, rideVehicleClass("Uber Taxi"))
        // Premium markers outrank XL: "Lux Black XL" is a premium ride, not just a big one.
        assertEquals(GroundTransportVehicleClass.PREMIUM, rideVehicleClass("Lux Black XL"))
        assertEquals(GroundTransportVehicleClass.PREMIUM, rideVehicleClass("Uber Black"))
        assertEquals(GroundTransportVehicleClass.XL, rideVehicleClass("UberXL"))
        assertEquals(GroundTransportVehicleClass.XL, rideVehicleClass("lyft_plus"))
        assertEquals(GroundTransportVehicleClass.STANDARD, rideVehicleClass("UberX"))
        assertEquals(GroundTransportVehicleClass.STANDARD, rideVehicleClass("Lyft"))
    }

    @Test
    fun primetimeMultiplier_parsesPercentages() {
        assertEquals(1.25, primetimeMultiplier("25%"), 1e-9)
        assertEquals(1.5, primetimeMultiplier("50%"), 1e-9)
        assertEquals(1.0, primetimeMultiplier("0%"), 1e-9)
        assertEquals(1.0, primetimeMultiplier(null), 1e-9)
        assertEquals(1.0, primetimeMultiplier("n/a"), 1e-9)
        // A negative primetime makes no sense — never discount the fare because of junk data.
        assertEquals(1.0, primetimeMultiplier("-20%"), 1e-9)
    }
}
