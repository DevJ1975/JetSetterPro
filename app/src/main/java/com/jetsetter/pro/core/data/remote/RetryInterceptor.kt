package com.jetsetter.pro.core.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * The §2.1 HTTP retry policy — mirrors the iOS `APIClient`.
 *
 * - **GET only.** POST and every other method are attempted exactly once.
 * - Up to [maxRetries] (3) re-sends after the first attempt, on:
 *   - transient [IOException]s — [SocketTimeoutException], [UnknownHostException],
 *     [ConnectException], [EOFException]. SSL failures, cancellations, and other
 *     [java.io.InterruptedIOException]s are NOT retried;
 *   - HTTP 429 and 5xx responses.
 * - Backoff before retry *n*: `min(2^(n-1), 8)` seconds plus `random(0..0.3)` seconds of jitter.
 * - A numeric `Retry-After` header is honored (without jitter) on 429; an unparsable value falls
 *   back to the computed backoff.
 * - Cumulative sleep is capped by [sleepBudgetMillis] (~10 s): each wait is bounded by the
 *   remaining budget, and once it is exhausted the last response (or error) is surfaced as-is.
 *
 * [random] and [sleep] are injectable so tests can pin jitter and record waits deterministically.
 */
class RetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val sleepBudgetMillis: Long = SLEEP_BUDGET_MILLIS,
    private val random: Random = Random.Default,
    private val sleep: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) return chain.proceed(request)

        var retries = 0
        var sleptMillis = 0L
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (!isTransient(e) || retries >= maxRetries) throw e
                retries++
                val delay = boundedDelay(backoffMillis(retries), sleptMillis) ?: throw e
                sleep(delay)
                sleptMillis += delay
                continue
            }

            if (!isRetryableStatus(response.code) || retries >= maxRetries) return response
            retries++
            val desired = retryAfterMillis(response) ?: backoffMillis(retries)
            val delay = boundedDelay(desired, sleptMillis) ?: return response
            response.close()
            sleep(delay)
            sleptMillis += delay
        }
    }

    /** Transient connectivity failures worth a retry; everything else propagates immediately. */
    private fun isTransient(e: IOException): Boolean = when (e) {
        is SocketTimeoutException -> true // read/connect timed out
        is UnknownHostException -> true // DNS blip
        is ConnectException -> true // refused / unreachable
        is EOFException -> true // connection dropped mid-response
        else -> false // SSLException, other InterruptedIOException (cancellation), etc.
    }

    private fun isRetryableStatus(code: Int): Boolean = code == 429 || code in 500..599

    /** `min(2^(retryNumber-1), 8)` seconds + `random(0..0.3)` seconds of jitter, in millis. */
    private fun backoffMillis(retryNumber: Int): Long {
        val baseSeconds = (1L shl (retryNumber - 1)).coerceAtMost(MAX_BACKOFF_SECONDS)
        val jitterMillis = (random.nextDouble() * MAX_JITTER_MILLIS).toLong()
        return baseSeconds * 1000L + jitterMillis
    }

    /** Numeric `Retry-After` (seconds) on a 429, or null to use the computed backoff. */
    private fun retryAfterMillis(response: Response): Long? {
        if (response.code != 429) return null
        return response.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1000L)
    }

    /** [desiredMillis] clamped to the remaining sleep budget; null once the budget is spent. */
    private fun boundedDelay(desiredMillis: Long, sleptMillis: Long): Long? {
        val remaining = sleepBudgetMillis - sleptMillis
        if (remaining <= 0L) return null
        return desiredMillis.coerceAtMost(remaining)
    }

    companion object {
        /** Re-sends after the first attempt (spec: retries ≤ 3, i.e. at most 4 attempts). */
        const val MAX_RETRIES = 3

        /** Total sleeping across all retries of one call is capped at ~10 s. */
        const val SLEEP_BUDGET_MILLIS = 10_000L

        private const val MAX_BACKOFF_SECONDS = 8L
        private const val MAX_JITTER_MILLIS = 300.0
    }
}
