package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.expedia.ExpediaAmount
import com.jetsetter.pro.core.data.remote.expedia.ExpediaOccupancyPricing
import com.jetsetter.pro.core.data.remote.expedia.ExpediaPricingByCurrency
import com.jetsetter.pro.core.data.remote.expedia.ExpediaProperty
import com.jetsetter.pro.core.data.remote.expedia.ExpediaRate
import com.jetsetter.pro.core.data.remote.expedia.ExpediaRoom
import com.jetsetter.pro.core.data.remote.expedia.ExpediaToken
import com.jetsetter.pro.core.data.remote.expedia.ExpediaTokenLogic
import com.jetsetter.pro.core.data.remote.expedia.ExpediaTokenProvider
import com.jetsetter.pro.core.data.remote.expedia.ExpediaTokenResponse
import com.jetsetter.pro.core.data.remote.expedia.ExpediaTotals
import com.jetsetter.pro.core.data.remote.expedia.lowestInclusiveTotal
import com.jetsetter.pro.feature.booking.toHotelBookingItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Base64

/**
 * Pins the pure parts of the Expedia Rapid seam (plan B7): the OAuth2 token cache rules
 * ([ExpediaTokenLogic] — payload mapping, the ~60 s refresh-early window, Basic auth), the
 * [ExpediaTokenProvider] behavior with an injected clock + fetch (caching, early refresh,
 * mutex single-flight, nothing cached on failure), and the price/booking derivations
 * ([lowestInclusiveTotal], `toHotelBookingItem`). Pure JVM — no network, no Android.
 */
class ExpediaTokenLogicTest {

    // ── ExpediaTokenLogic.toToken ────────────────────────────────────────────

    @Test
    fun toToken_mapsAccessTokenAndAbsoluteExpiry() {
        val token = ExpediaTokenLogic.toToken(
            ExpediaTokenResponse(accessToken = "abc123", expiresInSeconds = 1799),
            nowMillis = 1_000_000L,
        )

        assertNotNull(token)
        assertEquals("abc123", token!!.accessToken)
        assertEquals(1_000_000L + 1799 * 1000L, token.expiresAtMillis)
    }

    @Test
    fun toToken_trimsAccessToken() {
        val token = ExpediaTokenLogic.toToken(
            ExpediaTokenResponse(accessToken = "  abc123  ", expiresInSeconds = 60),
            nowMillis = 0L,
        )
        assertEquals("abc123", token!!.accessToken)
    }

    @Test
    fun toToken_missingOrBlankAccessToken_isNull() {
        assertNull(ExpediaTokenLogic.toToken(ExpediaTokenResponse(accessToken = null, expiresInSeconds = 60), 0L))
        assertNull(ExpediaTokenLogic.toToken(ExpediaTokenResponse(accessToken = "   ", expiresInSeconds = 60), 0L))
    }

    @Test
    fun toToken_missingOrNonPositiveExpiry_isNull() {
        assertNull(ExpediaTokenLogic.toToken(ExpediaTokenResponse(accessToken = "abc", expiresInSeconds = null), 0L))
        assertNull(ExpediaTokenLogic.toToken(ExpediaTokenResponse(accessToken = "abc", expiresInSeconds = 0), 0L))
        assertNull(ExpediaTokenLogic.toToken(ExpediaTokenResponse(accessToken = "abc", expiresInSeconds = -5), 0L))
    }

    // ── ExpediaTokenLogic.isFresh (refresh-early window) ─────────────────────

    @Test
    fun isFresh_nullToken_isStale() {
        assertFalse(ExpediaTokenLogic.isFresh(null, nowMillis = 0L))
    }

    @Test
    fun isFresh_insideWindow_untilExactlyRefreshEarlyBeforeExpiry() {
        val token = ExpediaToken(accessToken = "abc", expiresAtMillis = 1_000_000L)
        val boundary = 1_000_000L - ExpediaTokenLogic.REFRESH_EARLY_MILLIS

        assertTrue(ExpediaTokenLogic.isFresh(token, nowMillis = boundary - 1)) // just inside
        assertFalse(ExpediaTokenLogic.isFresh(token, nowMillis = boundary)) // refresh-early kicks in
        assertFalse(ExpediaTokenLogic.isFresh(token, nowMillis = 1_000_000L)) // nominal expiry
    }

    @Test
    fun refreshEarlyWindow_isSixtySeconds() {
        // Plan B7 contract: refresh ~60 s before nominal expiry.
        assertEquals(60_000L, ExpediaTokenLogic.REFRESH_EARLY_MILLIS)
    }

    // ── ExpediaTokenLogic.basicAuth ──────────────────────────────────────────

    @Test
    fun basicAuth_isBase64OfIdColonSecret() {
        val header = ExpediaTokenLogic.basicAuth(clientId = "my-id", clientSecret = "my-secret")

        assertTrue(header.startsWith("Basic "))
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ")))
        assertEquals("my-id:my-secret", decoded)
    }

    // ── ExpediaTokenProvider (injected clock + fetch) ────────────────────────

    private class FetchScript(
        private val responses: MutableList<() -> ExpediaTokenResponse>,
    ) {
        var fetchCount = 0
            private set

        suspend fun fetch(): ExpediaTokenResponse {
            fetchCount++
            return responses.removeAt(0).invoke()
        }
    }

    private fun response(token: String, expiresInSeconds: Long = 1800) =
        ExpediaTokenResponse(accessToken = token, expiresInSeconds = expiresInSeconds)

    @Test
    fun provider_cachesFreshToken_acrossCalls() = runBlocking {
        val script = FetchScript(mutableListOf({ response("tok-1") }, { response("tok-2") }))
        val provider = ExpediaTokenProvider(fetchToken = script::fetch, clock = { 0L })

        assertEquals("tok-1", provider.accessToken())
        assertEquals("tok-1", provider.accessToken())
        assertEquals(1, script.fetchCount)
    }

    @Test
    fun provider_refreshesEarly_atSixtySecondsBeforeExpiry() = runBlocking {
        var now = 0L
        val script = FetchScript(mutableListOf({ response("tok-1", 1800) }, { response("tok-2", 1800) }))
        val provider = ExpediaTokenProvider(fetchToken = script::fetch, clock = { now })

        assertEquals("tok-1", provider.accessToken()) // expires at 1_800_000

        now = 1_800_000L - 60_001L // still strictly inside the window
        assertEquals("tok-1", provider.accessToken())
        assertEquals(1, script.fetchCount)

        now = 1_800_000L - 60_000L // window closes → refresh early
        assertEquals("tok-2", provider.accessToken())
        assertEquals(2, script.fetchCount)
    }

    @Test
    fun provider_singleFlightsConcurrentCallers_throughTheMutex() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var fetchCount = 0
        val provider = ExpediaTokenProvider(
            fetchToken = {
                fetchCount++
                gate.await() // hold the first caller in-flight while the second arrives
                response("tok-$fetchCount")
            },
            clock = { 0L },
        )

        val first = async { provider.accessToken() }
        val second = async { provider.accessToken() }
        yield() // let both callers reach their suspension points (fetch / mutex)
        gate.complete(Unit)

        assertEquals("tok-1", first.await())
        assertEquals("tok-1", second.await()) // waited on the mutex, reused the cache
        assertEquals(1, fetchCount)
    }

    @Test
    fun provider_failedFetch_cachesNothing_nextCallRetries() = runBlocking {
        val script = FetchScript(
            mutableListOf({ throw IOException("network down") }, { response("tok-2") }),
        )
        val provider = ExpediaTokenProvider(fetchToken = script::fetch, clock = { 0L })

        val failure = runCatching { provider.accessToken() }
        assertTrue(failure.exceptionOrNull() is IOException)

        assertEquals("tok-2", provider.accessToken())
        assertEquals(2, script.fetchCount)
    }

    @Test
    fun provider_unusablePayload_throwsDecodingFailed_andCachesNothing() = runBlocking {
        val script = FetchScript(
            mutableListOf({ response("", 1800) }, { response("tok-2") }),
        )
        val provider = ExpediaTokenProvider(fetchToken = script::fetch, clock = { 0L })

        val failure = runCatching { provider.accessToken() }
        assertTrue(failure.exceptionOrNull() is ApiError.DecodingFailed)

        assertEquals("tok-2", provider.accessToken())
        assertEquals(2, script.fetchCount)
    }

    // ── lowestInclusiveTotal ─────────────────────────────────────────────────

    private fun rate(value: String?, currency: String? = "USD", refundable: Boolean? = false) = ExpediaRate(
        id = "r-$value",
        refundable = refundable,
        occupancyPricing = mapOf(
            "2" to ExpediaOccupancyPricing(
                totals = ExpediaTotals(
                    inclusive = ExpediaPricingByCurrency(
                        requestCurrency = ExpediaAmount(value = value, currency = currency),
                    ),
                ),
            ),
        ),
    )

    private fun property(vararg rates: ExpediaRate, id: String? = "12345", name: String? = "Test Hotel") =
        ExpediaProperty(propertyId = id, name = name, rooms = listOf(ExpediaRoom(id = "room-1", rates = rates.toList())))

    @Test
    fun lowestInclusiveTotal_picksCheapestAcrossRates() {
        val price = property(
            rate("450.00"),
            rate("389.10", refundable = true),
            rate("512.99"),
        ).lowestInclusiveTotal()

        assertNotNull(price)
        assertEquals(389.10, price!!.total, 1e-9)
        assertEquals("USD", price.currency)
        assertTrue(price.refundable)
    }

    @Test
    fun lowestInclusiveTotal_uppercasesCurrency() {
        val price = property(rate("100.00", currency = " usd ")).lowestInclusiveTotal()
        assertEquals("USD", price!!.currency)
    }

    @Test
    fun lowestInclusiveTotal_skipsJunkAmounts() {
        val price = property(
            rate(null), // missing amount
            rate("not-a-number"),
            rate("0"), // non-positive
            rate("-12.50"),
            rate("77.00", currency = null), // no currency code
            rate("240.00"), // the only usable quote
        ).lowestInclusiveTotal()

        assertEquals(240.00, price!!.total, 1e-9)
    }

    @Test
    fun lowestInclusiveTotal_nullWhenNothingUsable() {
        assertNull(property(rate(null), rate("junk")).lowestInclusiveTotal())
        assertNull(ExpediaProperty(propertyId = "12345", rooms = emptyList()).lowestInclusiveTotal())
    }

    // ── toHotelBookingItem (live → booking model) ────────────────────────────

    @Test
    fun toHotelBookingItem_mapsPricedProperty() {
        val item = property(rate("300.00", refundable = true)).toHotelBookingItem(
            nights = 3,
            expectedCurrency = "USD",
        )

        assertNotNull(item)
        assertEquals("expedia-12345", item!!.id) // stable, source-prefixed id
        assertEquals("Test Hotel", item.name)
        assertEquals(100.0, item.pricePerNight, 1e-9) // stay total / nights
        assertTrue(item.freeCancellation)
        // Availability carries no ratings — honest zeros, never invented.
        assertEquals(0, item.starRating)
        assertEquals(0.0, item.guestRating, 0.0)
    }

    @Test
    fun toHotelBookingItem_missingName_fallsBackToPropertyId() {
        val item = property(rate("300.00"), name = null).toHotelBookingItem(3, "USD")
        assertEquals("Property 12345", item!!.name)
    }

    @Test
    fun toHotelBookingItem_rejectsUnpricedOrUnidentifiedProperties() {
        assertNull(property(rate(null)).toHotelBookingItem(3, "USD")) // no usable quote
        assertNull(property(rate("300.00"), id = null).toHotelBookingItem(3, "USD"))
        assertNull(property(rate("300.00"), id = "  ").toHotelBookingItem(3, "USD"))
        assertNull(property(rate("300.00")).toHotelBookingItem(0, "USD")) // degenerate stay
    }

    @Test
    fun toHotelBookingItem_rejectsCurrencyMismatch() {
        // The screen renders bare `$` amounts — a EUR quote must not slip through.
        assertNull(property(rate("300.00", currency = "EUR")).toHotelBookingItem(3, "USD"))
    }

    @Test
    fun toHotelBookingItem_photoColor_isDeterministicPerProperty() {
        val a = property(rate("300.00")).toHotelBookingItem(3, "USD")
        val b = property(rate("450.00")).toHotelBookingItem(3, "USD")
        assertEquals(a!!.photoColorHex, b!!.photoColorHex) // same property id → same placeholder art
    }
}
