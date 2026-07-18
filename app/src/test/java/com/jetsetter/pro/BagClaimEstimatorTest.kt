package com.jetsetter.pro

import com.jetsetter.pro.core.travel.BagClaimEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the deterministic bag-delivery bands in [BagClaimEstimator] (spec §3.2): no-bag zero,
 * tier-1 hubs 20–35, everywhere else 12–25, expected = midpoint. Pure-JVM (no Android deps).
 */
class BagClaimEstimatorTest {

    @Test
    fun noCheckedBag_isAllZeroes_regardlessOfAirport() {
        for (code in listOf("ATL", "BOI", "XXX")) {
            val e = BagClaimEstimator.estimate(code, hasCheckedBag = false)
            assertEquals(0, e.minMinutes)
            assertEquals(0, e.maxMinutes)
            assertEquals(0, e.expectedMinutes)
        }
    }

    @Test
    fun everyTier1Hub_gets20To35() {
        val expectedHubs = setOf(
            "ATL", "DFW", "ORD", "LAX", "JFK", "DEN", "SFO", "LAS", "SEA", "MIA",
            "EWR", "BOS", "MCO", "CLT", "IAH", "LHR", "CDG", "FRA", "AMS", "DXB",
            "HND", "NRT", "SIN", "HKG", "ICN", "PEK", "PVG",
        )
        // The hub roster itself is a cross-platform contract — pin it exactly.
        assertEquals(expectedHubs, BagClaimEstimator.TIER1_HUBS)

        for (hub in expectedHubs) {
            val e = BagClaimEstimator.estimate(hub, hasCheckedBag = true)
            assertEquals("min at $hub", 20, e.minMinutes)
            assertEquals("max at $hub", 35, e.maxMinutes)
        }
    }

    @Test
    fun nonHub_gets12To25() {
        for (code in listOf("BOI", "GEG", "SAV")) {
            val e = BagClaimEstimator.estimate(code, hasCheckedBag = true)
            assertEquals(12, e.minMinutes)
            assertEquals(25, e.maxMinutes)
        }
    }

    @Test
    fun expectedMinutes_isTheMidpoint() {
        val hub = BagClaimEstimator.estimate("ATL", hasCheckedBag = true)
        assertEquals((hub.minMinutes + hub.maxMinutes) / 2, hub.expectedMinutes)
        assertEquals(27, hub.expectedMinutes)

        val other = BagClaimEstimator.estimate("BOI", hasCheckedBag = true)
        assertEquals((other.minMinutes + other.maxMinutes) / 2, other.expectedMinutes)
        assertEquals(18, other.expectedMinutes)
    }

    @Test
    fun airportCode_isCaseInsensitiveAndTrimmed() {
        val e = BagClaimEstimator.estimate(" atl ", hasCheckedBag = true)
        assertEquals(20, e.minMinutes)
        assertEquals(35, e.maxMinutes)
    }

    @Test
    fun basisAndDisplay_areNonEmpty() {
        for (e in listOf(
            BagClaimEstimator.estimate("ATL", hasCheckedBag = true),
            BagClaimEstimator.estimate("BOI", hasCheckedBag = true),
            BagClaimEstimator.estimate("ATL", hasCheckedBag = false),
        )) {
            assertTrue(e.basis.isNotBlank())
            assertTrue(e.display.isNotBlank())
        }
    }
}
