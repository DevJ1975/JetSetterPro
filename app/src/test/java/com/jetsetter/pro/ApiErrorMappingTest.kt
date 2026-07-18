package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.IsoDateAdapters
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.data.remote.map
import com.jetsetter.pro.core.data.remote.onFailure
import com.jetsetter.pro.core.data.remote.onSuccess
import com.jetsetter.pro.core.data.remote.toApiError
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** DTO for the [IsoDateAdapters] round-trip below (top-level so Moshi's reflection can reach it). */
data class IsoStampDto(val at: Instant, val on: LocalDate)

/**
 * Pins the §2.1 typed-error mapping (`Throwable.toApiError`), the `apiCall` wrapper, the
 * ApiResult combinators, and the ISO-8601 Moshi adapters. Pure-JVM (no Android deps).
 */
class ApiErrorMappingTest {

    // ── Throwable.toApiError ─────────────────────────────────────────────────

    @Test
    fun http401_mapsToUnauthorized() {
        assertEquals(ApiError.Unauthorized, httpException(401).toApiError())
    }

    @Test
    fun http429_withNumericRetryAfter_mapsToRateLimitedWithSeconds() {
        val error = httpException(429, "Retry-After" to "7").toApiError()
        assertEquals(ApiError.RateLimited(retryAfterSeconds = 7L), error)
    }

    @Test
    fun http429_withoutRetryAfter_mapsToRateLimitedNull() {
        assertEquals(ApiError.RateLimited(retryAfterSeconds = null), httpException(429).toApiError())
    }

    @Test
    fun http429_withUnparsableRetryAfter_mapsToRateLimitedNull() {
        val error = httpException(429, "Retry-After" to "Fri, 17 Jul 2026 12:00:00 GMT").toApiError()
        assertEquals(ApiError.RateLimited(retryAfterSeconds = null), error)
    }

    @Test
    fun otherHttpStatuses_mapToRequestFailed() {
        assertEquals(ApiError.RequestFailed(500), httpException(500).toApiError())
        assertEquals(ApiError.RequestFailed(404), httpException(404).toApiError())
        assertEquals(ApiError.RequestFailed(403), httpException(403).toApiError())
    }

    @Test
    fun jsonExceptions_mapToDecodingFailed() {
        val data = JsonDataException("expected a string")
        assertEquals(ApiError.DecodingFailed(data), data.toApiError())

        // JsonEncodingException extends IOException — must still map to DecodingFailed, not Unknown.
        val encoding = JsonEncodingException("malformed JSON")
        assertEquals(ApiError.DecodingFailed(encoding), encoding.toApiError())
    }

    @Test
    fun ioExceptionAndEverythingElse_mapToUnknown() {
        val io = IOException("socket closed")
        assertEquals(ApiError.Unknown(io), io.toApiError())

        val other = IllegalStateException("boom")
        assertEquals(ApiError.Unknown(other), other.toApiError())
    }

    @Test
    fun existingApiError_passesThroughUntouched() {
        val error = ApiError.RateLimited(3)
        assertSame(error, error.toApiError())
    }

    // ── apiCall ──────────────────────────────────────────────────────────────

    @Test
    fun apiCall_wrapsSuccess() = runBlocking {
        assertEquals(ApiResult.Success("ok"), apiCall { "ok" })
    }

    @Test
    fun apiCall_mapsFailures() = runBlocking {
        val result = apiCall<String> { throw httpException(401) }
        assertEquals(ApiResult.Failure(ApiError.Unauthorized), result)
    }

    @Test
    fun apiCall_neverSwallowsCancellation() {
        try {
            runBlocking { apiCall<Unit> { throw CancellationException("cancelled") } }
            fail("expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            // Structured concurrency intact.
        }
    }

    // ── Combinators ──────────────────────────────────────────────────────────

    @Test
    fun map_transformsSuccessAndPreservesFailure() {
        val success: ApiResult<Int> = ApiResult.Success(21)
        assertEquals(ApiResult.Success(42), success.map { it * 2 })

        val failure: ApiResult<Int> = ApiResult.Failure(ApiError.Unauthorized)
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun getOrNull_unwrapsSuccessOnly() {
        assertEquals("data", ApiResult.Success("data").getOrNull())
        assertNull(ApiResult.Failure(ApiError.RequestFailed(500)).getOrNull())
    }

    @Test
    fun onSuccessAndOnFailure_fireOnTheRightSide() {
        var seenData: String? = null
        var seenError: ApiError? = null

        val success: ApiResult<String> = ApiResult.Success("data")
        assertSame(success, success.onSuccess { seenData = it }.onFailure { seenError = it })
        assertEquals("data", seenData)
        assertNull(seenError)

        seenData = null
        val failure: ApiResult<String> = ApiResult.Failure(ApiError.Unauthorized)
        assertSame(failure, failure.onSuccess { seenData = it }.onFailure { seenError = it })
        assertNull(seenData)
        assertEquals(ApiError.Unauthorized, seenError)
    }

    // ── IsoDateAdapters ──────────────────────────────────────────────────────

    @Test
    fun isoAdapters_roundTripInstantAndLocalDate() {
        val adapter = moshi().adapter(IsoStampDto::class.java)
        val json = """{"at":"2026-07-17T12:30:00Z","on":"2026-07-17"}"""

        val parsed = adapter.fromJson(json)!!
        assertEquals(Instant.parse("2026-07-17T12:30:00Z"), parsed.at)
        assertEquals(LocalDate.of(2026, 7, 17), parsed.on)
        assertEquals(json, adapter.toJson(parsed))
    }

    @Test
    fun isoAdapters_acceptOffsetDateTimes_normalizedToUtc() {
        val adapter = moshi().adapter(IsoStampDto::class.java)
        val parsed = adapter.fromJson("""{"at":"2026-07-17T12:30:00+02:00","on":"2026-07-17"}""")!!
        assertEquals(Instant.parse("2026-07-17T10:30:00Z"), parsed.at)
    }

    @Test
    fun isoAdapters_badDate_surfacesAsDecodingFailed() {
        val adapter = moshi().adapter(IsoStampDto::class.java)
        val thrown = assertThrows(JsonDataException::class.java) {
            adapter.fromJson("""{"at":"tomorrow","on":"2026-07-17"}""")
        }
        assertTrue(thrown.toApiError() is ApiError.DecodingFailed)
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun moshi(): Moshi =
        Moshi.Builder().add(IsoDateAdapters()).add(KotlinJsonAdapterFactory()).build()

    private fun httpException(code: Int, vararg headers: Pair<String, String>): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://api.example.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(retrofit2.Response.error<Any>(body, raw))
    }
}
