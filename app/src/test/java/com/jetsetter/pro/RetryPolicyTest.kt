package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.RetryInterceptor
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.random.Random

/**
 * Pins the §2.1 retry policy of [RetryInterceptor] against a fake OkHttp chain (no network):
 * GET-only retries, transient-vs-fatal IOException classification, backoff + seeded jitter
 * bounds, the ~10s cumulative sleep budget, and numeric Retry-After handling on 429.
 * Sleeps are recorded through the injectable sleep lambda — nothing actually waits.
 */
class RetryPolicyTest {

    // ── Backoff + jitter ─────────────────────────────────────────────────────

    @Test
    fun transientTimeout_retriesThreeTimes_backoffWithinJitterBounds() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(random = Random(42), sleep = { sleeps += it })
        val chain = FakeChain(get(), outcomes({ throw SocketTimeoutException("read timed out") }))

        assertThrows(SocketTimeoutException::class.java) { interceptor.intercept(chain) }

        assertEquals(4, chain.proceedCount) // 1 attempt + 3 retries
        assertEquals(3, sleeps.size)
        // Backoff base 1s/2s/4s, each with random(0..0.3)s jitter on top.
        assertTrue("sleep[0]=${sleeps[0]}", sleeps[0] in 1000..1299)
        assertTrue("sleep[1]=${sleeps[1]}", sleeps[1] in 2000..2299)
        assertTrue("sleep[2]=${sleeps[2]}", sleeps[2] in 4000..4299)
    }

    @Test
    fun seededJitter_isDeterministic() {
        fun run(): List<Long> {
            val sleeps = mutableListOf<Long>()
            val interceptor = RetryInterceptor(random = Random(7), sleep = { sleeps += it })
            val chain = FakeChain(get(), outcomes({ throw UnknownHostException("dns") }))
            assertThrows(UnknownHostException::class.java) { interceptor.intercept(chain) }
            return sleeps
        }
        assertEquals(run(), run())
    }

    @Test
    fun zeroJitter_backoffIsExactly1s2s4s() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(random = FixedRandom(0.0), sleep = { sleeps += it })
        val chain = FakeChain(get(), outcomes({ throw ConnectException("refused") }))

        assertThrows(ConnectException::class.java) { interceptor.intercept(chain) }

        assertEquals(listOf(1000L, 2000L, 4000L), sleeps)
    }

    // ── IOException classification ───────────────────────────────────────────

    @Test
    fun eachTransientErrorKind_isRetriedToSuccess() {
        val transients = listOf<() -> Response>(
            { throw SocketTimeoutException("timeout") },
            { throw UnknownHostException("dns") },
            { throw ConnectException("refused") },
            { throw EOFException("dropped") },
        )
        transients.forEach { failure ->
            val sleeps = mutableListOf<Long>()
            val interceptor = RetryInterceptor(random = FixedRandom(0.0), sleep = { sleeps += it })
            val req = get()
            val chain = FakeChain(req, outcomes({ failure() }, { response(req, 200) }))

            val result = interceptor.intercept(chain)

            assertEquals(200, result.code)
            assertEquals(2, chain.proceedCount)
            assertEquals(listOf(1000L), sleeps)
        }
    }

    @Test
    fun nonTransientIoExceptions_areNotRetried() {
        val fatals = listOf<() -> Response>(
            { throw SSLException("handshake failed") },
            { throw InterruptedIOException("cancelled") },
            { throw IOException("something else") },
        )
        fatals.forEach { failure ->
            val sleeps = mutableListOf<Long>()
            val interceptor = RetryInterceptor(sleep = { sleeps += it })
            val chain = FakeChain(get(), outcomes({ failure() }))

            assertThrows(IOException::class.java) { interceptor.intercept(chain) }

            assertEquals(1, chain.proceedCount)
            assertTrue(sleeps.isEmpty())
        }
    }

    // ── HTTP status handling ─────────────────────────────────────────────────

    @Test
    fun get503ThenSuccess_isRetriedOnce() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(random = FixedRandom(0.0), sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 503) }, { response(req, 200) }))

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(2, chain.proceedCount)
        assertEquals(listOf(1000L), sleeps)
    }

    @Test
    fun persistent500_exhaustsRetries_returnsLastResponse() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(random = FixedRandom(0.0), sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 500) }))

        val result = interceptor.intercept(chain)

        assertEquals(500, result.code)
        assertEquals(4, chain.proceedCount) // 1 attempt + 3 retries
        assertEquals(listOf(1000L, 2000L, 4000L), sleeps)
    }

    @Test
    fun clientErrors_areNotRetried() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 404) }))

        val result = interceptor.intercept(chain)

        assertEquals(404, result.code)
        assertEquals(1, chain.proceedCount)
        assertTrue(sleeps.isEmpty())
    }

    // ── Retry-After on 429 ───────────────────────────────────────────────────

    @Test
    fun numericRetryAfterOn429_isHonoredExactly_withoutJitter() {
        val sleeps = mutableListOf<Long>()
        // Max jitter random: proves Retry-After bypasses the jittered backoff entirely.
        val interceptor = RetryInterceptor(random = FixedRandom(0.99), sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 429, retryAfter = "2") }, { response(req, 200) }))

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(listOf(2000L), sleeps)
    }

    @Test
    fun unparsableRetryAfter_fallsBackToComputedBackoff() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(random = FixedRandom(0.0), sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 429, retryAfter = "in a minute") }, { response(req, 200) }))

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        assertEquals(listOf(1000L), sleeps) // computed 1s backoff, not a parsed header
    }

    // ── Sleep budget (~10s cumulative) ───────────────────────────────────────

    @Test
    fun hugeRetryAfter_isClampedToBudget_thenRetryingStops() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 429, retryAfter = "20") }))

        val result = interceptor.intercept(chain)

        // First wait clamped to the full 10s budget; the next retry has no budget left.
        assertEquals(429, result.code)
        assertEquals(2, chain.proceedCount)
        assertEquals(listOf(10_000L), sleeps)
    }

    @Test
    fun budgetExhaustion_stopsRetryingBeforeMaxRetries() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleep = { sleeps += it })
        val req = get()
        val chain = FakeChain(req, outcomes({ response(req, 429, retryAfter = "6") }))

        val result = interceptor.intercept(chain)

        // 6s honored, then only 4s of budget remain, then nothing — 2 of 3 retries used.
        assertEquals(429, result.code)
        assertEquals(3, chain.proceedCount)
        assertEquals(listOf(6000L, 4000L), sleeps)
    }

    // ── Non-GET methods ──────────────────────────────────────────────────────

    @Test
    fun post503_isNeverRetried() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleep = { sleeps += it })
        val req = post()
        val chain = FakeChain(req, outcomes({ response(req, 503) }))

        val result = interceptor.intercept(chain)

        assertEquals(503, result.code)
        assertEquals(1, chain.proceedCount)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun postTransientIoException_isNeverRetried() {
        val sleeps = mutableListOf<Long>()
        val interceptor = RetryInterceptor(sleep = { sleeps += it })
        val chain = FakeChain(post(), outcomes({ throw SocketTimeoutException("timeout") }))

        assertThrows(SocketTimeoutException::class.java) { interceptor.intercept(chain) }

        assertEquals(1, chain.proceedCount)
        assertTrue(sleeps.isEmpty())
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun get(): Request = Request.Builder().url("https://api.example.com/v1/thing").build()

    private fun post(): Request = Request.Builder()
        .url("https://api.example.com/v1/thing")
        .post("{}".toRequestBody("application/json".toMediaType()))
        .build()

    private fun response(request: Request, code: Int, retryAfter: String? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("".toResponseBody())
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
            .build()

    private fun outcomes(vararg steps: () -> Response): MutableList<() -> Response> =
        steps.toMutableList()

    /** Serves scripted outcomes per proceed(); the last one repeats ("server keeps failing"). */
    private class FakeChain(
        private val initialRequest: Request,
        private val steps: MutableList<() -> Response>,
    ) : Interceptor.Chain {
        var proceedCount = 0
            private set

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            proceedCount++
            val step = if (steps.size > 1) steps.removeAt(0) else steps.first()
            return step()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException("not used by RetryInterceptor")
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    /** A [Random] whose nextDouble() is pinned — for exact jitter assertions. */
    private class FixedRandom(private val double: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = double
    }
}
