package com.jetsetter.pro.core.data.remote.vision

import com.jetsetter.pro.core.data.remote.ApiError
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.apiCall
import com.jetsetter.pro.core.secrets.Secrets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access to Google Vision OCR (§2.3) behind the `isConfigured` doctrine: callers check
 * [isConfigured] before invoking, and treat any failure as "no text" — this service is only
 * ever the *fallback* leg of `core/ocr/ReceiptOcr`, never the primary path (ML Kit stays
 * primary and fully on-device).
 *
 * PRIVACY INVARIANT (plan R10f): the request carries only the image bytes — never preference
 * or profile-derived fields.
 */
@Singleton
class GoogleVisionService @Inject constructor(
    private val api: VisionApi,
) {
    /** True when a real Vision API key is present; false → callers skip the fallback. */
    val isConfigured: Boolean get() = Secrets.isConfigured(Secrets.googleVision)

    /**
     * Runs TEXT_DETECTION over [imageBase64] (base64-encoded image bytes) and returns the first
     * `fullTextAnnotation.text`. Following the house convention (`FrankfurterService`), a
     * structurally unusable success — a per-image error status, or no text found — maps to a
     * typed failure rather than leaking a blank string: an embedded per-image error becomes
     * [ApiError.RequestFailed] with Vision's status code, a textless image becomes
     * [ApiError.DecodingFailed]. For the OCR-fallback use case both are simply a miss.
     */
    suspend fun ocr(imageBase64: String): ApiResult<String> =
        when (val result = apiCall { api.annotate(key = Secrets.googleVision, body = visionOcrRequest(imageBase64)) }) {
            is ApiResult.Success -> {
                val perImageError = result.data.responses.firstOrNull()?.error
                when {
                    perImageError != null -> ApiResult.Failure(ApiError.RequestFailed(perImageError.code ?: 0))
                    else -> result.data.firstText()
                        ?.let { ApiResult.Success(it) }
                        ?: ApiResult.Failure(
                            ApiError.DecodingFailed(
                                IllegalStateException("Vision response carried no text annotation"),
                            ),
                        )
                }
            }
            is ApiResult.Failure -> result
        }
}
