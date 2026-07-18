package com.jetsetter.pro.core.data.remote

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and wraps the outcome in the typed [ApiResult] — the §2.1 error model shared with
 * iOS. Every failure is mapped through [toApiError]; cancellation is never swallowed so
 * structured concurrency keeps working.
 *
 * ```
 * val result: ApiResult<FlightAwareResponse> = apiCall { api.getFlight(ident) }
 * ```
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    ApiResult.Failure(t.toApiError())
}

/**
 * Maps a raw failure to the typed [ApiError] — parallels the iOS `APIError` mapping:
 * HTTP 401 → [ApiError.Unauthorized]; 429 → [ApiError.RateLimited] (with the numeric
 * `Retry-After` seconds when the response is reachable); any other HTTP status →
 * [ApiError.RequestFailed]; malformed/mismatched JSON → [ApiError.DecodingFailed];
 * [IOException] and everything else → [ApiError.Unknown].
 */
fun Throwable.toApiError(): ApiError = when (this) {
    is ApiError -> this
    is HttpException -> when (code()) {
        401 -> ApiError.Unauthorized
        429 -> ApiError.RateLimited(retryAfterSecondsOrNull())
        else -> ApiError.RequestFailed(code())
    }
    // JsonEncodingException extends IOException — keep decoding checks before the IOException arm.
    is JsonDataException, is JsonEncodingException -> ApiError.DecodingFailed(this)
    is IOException -> ApiError.Unknown(this)
    else -> ApiError.Unknown(this)
}

private fun HttpException.retryAfterSecondsOrNull(): Long? =
    response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()

// ── Combinators ──────────────────────────────────────────────────────────────

/** Transforms the success value, passing failures through unchanged. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

/** The success value, or null on failure. */
fun <T> ApiResult<T>.getOrNull(): T? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> null
}

/** Runs [action] with the success value (if any); returns the receiver for chaining. */
inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

/** Runs [action] with the error (if any); returns the receiver for chaining. */
inline fun <T> ApiResult<T>.onFailure(action: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}
