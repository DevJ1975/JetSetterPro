package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.fx.FrankfurterLatestResponse
import com.jetsetter.pro.core.data.remote.fx.toFxRates
import com.jetsetter.pro.feature.currencytracker.CachedLiveRates
import com.jetsetter.pro.feature.currencytracker.CurrencyRate
import com.jetsetter.pro.feature.currencytracker.CurrencytrackerLiveFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure parts of the live-FX seam (plan B7, keyless Frankfurter/ECB): the wire
 * DTO→[com.jetsetter.pro.core.data.remote.fx.FxRates] mapping, the ~12 h staleness decision
 * ([CurrencytrackerLiveFx.isStale]), and the fallback ordering of the live-over-static merge
 * ([CurrencytrackerLiveFx.merge]). Pure JVM — no network, no Android.
 */
class FrankfurterMappingTest {

    // ── DTO → FxRates mapping ────────────────────────────────────────────────

    private fun dto(
        amount: Double? = 1.0,
        base: String? = "USD",
        date: String? = "2026-07-17",
        rates: Map<String, Double>? = mapOf("EUR" to 0.9187, "JPY" to 155.42),
    ) = FrankfurterLatestResponse(amount = amount, base = base, date = date, rates = rates)

    @Test
    fun fullPayload_maps() {
        val fx = dto().toFxRates()

        assertNotNull(fx)
        assertEquals("USD", fx!!.base)
        assertEquals("2026-07-17", fx.date)
        assertEquals(mapOf("EUR" to 0.9187, "JPY" to 155.42), fx.rates)
    }

    @Test
    fun base_isTrimmedAndUppercased() {
        val fx = dto(base = " usd ").toFxRates()
        assertEquals("USD", fx!!.base)
    }

    @Test
    fun missingAmount_isIrrelevant() {
        // `amount` is echo metadata — mapping must not depend on it.
        assertNotNull(dto(amount = null).toFxRates())
    }

    @Test
    fun missingOrBlankBase_mapsToNull() {
        assertNull(dto(base = null).toFxRates())
        assertNull(dto(base = "  ").toFxRates())
    }

    @Test
    fun missingOrBlankDate_mapsToNull() {
        assertNull(dto(date = null).toFxRates())
        assertNull(dto(date = "").toFxRates())
    }

    @Test
    fun missingOrEmptyRates_mapToNull() {
        assertNull(dto(rates = null).toFxRates())
        assertNull(dto(rates = emptyMap()).toFxRates())
    }

    @Test
    fun junkRateValues_areFiltered_andAllJunkMapsToNull() {
        val mixed = dto(
            rates = mapOf(
                "EUR" to 0.9187,
                "BAD" to 0.0,
                "NEG" to -3.5,
                "NAN" to Double.NaN,
                "INF" to Double.POSITIVE_INFINITY,
            ),
        ).toFxRates()
        assertEquals(mapOf("EUR" to 0.9187), mixed!!.rates)

        assertNull(dto(rates = mapOf("BAD" to 0.0, "NAN" to Double.NaN)).toFxRates())
    }

    // ── Staleness decision (~12 h TTL) ───────────────────────────────────────

    @Test
    fun freshCache_isNotStale() {
        assertFalse(CurrencytrackerLiveFx.isStale(fetchedAtMillis = 1_000L, nowMillis = 1_000L))
        assertFalse(
            CurrencytrackerLiveFx.isStale(
                fetchedAtMillis = 0L,
                nowMillis = CurrencytrackerLiveFx.STALE_AFTER_MILLIS - 1L,
            ),
        )
    }

    @Test
    fun cacheAtOrPastTwelveHours_isStale() {
        assertEquals(12L * 60L * 60L * 1000L, CurrencytrackerLiveFx.STALE_AFTER_MILLIS)
        assertTrue(
            CurrencytrackerLiveFx.isStale(
                fetchedAtMillis = 0L,
                nowMillis = CurrencytrackerLiveFx.STALE_AFTER_MILLIS,
            ),
        )
        assertTrue(
            CurrencytrackerLiveFx.isStale(
                fetchedAtMillis = 0L,
                nowMillis = CurrencytrackerLiveFx.STALE_AFTER_MILLIS + 123_456L,
            ),
        )
    }

    @Test
    fun futureTimestamp_isStale() {
        // Clock rolled back: refetch rather than trusting a timestamp that would stay "fresh".
        assertTrue(CurrencytrackerLiveFx.isStale(fetchedAtMillis = 2_000L, nowMillis = 1_999L))
    }

    // ── Fallback ordering (live over static merge, pure part) ────────────────

    private val staticTable = listOf(
        CurrencyRate(code = "USD", name = "US Dollar", flag = "🇺🇸", symbol = "$", ratePerUsd = 1.0, dailyChangePct = 0.0),
        CurrencyRate(code = "EUR", name = "Euro", flag = "🇪🇺", symbol = "€", ratePerUsd = 0.9200, dailyChangePct = -0.12),
        CurrencyRate(code = "JPY", name = "Japanese Yen", flag = "🇯🇵", symbol = "¥", ratePerUsd = 156.80, dailyChangePct = 0.45),
    )

    private fun cached(
        base: String = "USD",
        rates: Map<String, Double>,
        fetchedAtMillis: Long = 0L,
    ) = CachedLiveRates(base = base, date = "2026-07-17", rates = rates, fetchedAtMillis = fetchedAtMillis)

    @Test
    fun neverFetched_servesStaticTableUnchanged() {
        assertSame(staticTable, CurrencytrackerLiveFx.merge(staticTable, live = null))
    }

    @Test
    fun liveRates_overridePerCode_keepingStaticMetadata() {
        val merged = CurrencytrackerLiveFx.merge(
            staticTable,
            cached(rates = mapOf("EUR" to 0.9187, "JPY" to 155.42)),
        )

        val eur = merged.first { it.code == "EUR" }
        assertEquals(0.9187, eur.ratePerUsd, 0.0)
        // Static row still supplies everything but the rate.
        assertEquals("Euro", eur.name)
        assertEquals("🇪🇺", eur.flag)
        assertEquals("€", eur.symbol)
        assertEquals(-0.12, eur.dailyChangePct, 0.0)

        assertEquals(155.42, merged.first { it.code == "JPY" }.ratePerUsd, 0.0)
    }

    @Test
    fun codesAbsentFromLive_keepStaticRates_andUsdStaysParity() {
        val merged = CurrencytrackerLiveFx.merge(staticTable, cached(rates = mapOf("EUR" to 0.9187)))

        assertEquals(1.0, merged.first { it.code == "USD" }.ratePerUsd, 0.0)
        assertEquals(156.80, merged.first { it.code == "JPY" }.ratePerUsd, 0.0)
    }

    @Test
    fun liveCodesOutsideTheRoster_areIgnored() {
        val merged = CurrencytrackerLiveFx.merge(
            staticTable,
            cached(rates = mapOf("EUR" to 0.9187, "ZAR" to 18.02)),
        )
        assertEquals(staticTable.map { it.code }, merged.map { it.code })
    }

    @Test
    fun crossBaseSnapshot_isRejectedWholesale() {
        // A non-USD-anchored snapshot merged into a USD-anchored table would corrupt every
        // conversion — serve static instead.
        val merged = CurrencytrackerLiveFx.merge(
            staticTable,
            cached(base = "EUR", rates = mapOf("USD" to 1.0885, "JPY" to 169.20)),
        )
        assertSame(staticTable, merged)
    }

    @Test
    fun emptyLiveRates_serveStatic() {
        assertSame(staticTable, CurrencytrackerLiveFx.merge(staticTable, cached(rates = emptyMap())))
    }

    @Test
    fun junkLiveEntry_keepsStaticRateForThatRowOnly() {
        val merged = CurrencytrackerLiveFx.merge(
            staticTable,
            cached(rates = mapOf("EUR" to -1.0, "JPY" to 155.42)),
        )
        assertEquals(0.9200, merged.first { it.code == "EUR" }.ratePerUsd, 0.0)
        assertEquals(155.42, merged.first { it.code == "JPY" }.ratePerUsd, 0.0)
    }

    @Test
    fun liveCaption_carriesEcbAttributionAndATimestamp() {
        val caption = CurrencytrackerLiveFx.liveCaption(fetchedAtMillis = 1_752_750_000_000L)
        assertTrue(caption, caption.startsWith("Live ECB rates · updated "))
        assertTrue(caption, caption.length > "Live ECB rates · updated ".length)
    }
}
