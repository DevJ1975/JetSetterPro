package com.jetsetter.pro.core.data.remote.vision

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Cloud Vision `images:annotate` (spec §2.3) — the OCR *fallback* behind ML Kit
 * (`core/ocr/ReceiptOcr`). Auth is the API key as a query parameter (Vision's convention,
 * unlike the header-auth services), passed per call so the interface stays key-agnostic.
 *
 * Wire naming: the Vision REST API is camelCase, which matches the Kotlin property names
 * directly — so no per-field `@Json` renames are needed (the §2.1 no-global-strategy rule
 * still holds). DTOs cover only the TEXT_DETECTION slice we consume.
 *
 * Note the request is a POST, which the shared `RetryInterceptor` never retries (§2.1) —
 * exactly right for a billed annotate call.
 */
interface VisionApi {
    @POST("v1/images:annotate")
    suspend fun annotate(
        @Query("key") key: String,
        @Body body: VisionAnnotateRequest,
    ): VisionAnnotateResponse
}

// ── Request DTOs ─────────────────────────────────────────────────────────────

data class VisionAnnotateRequest(
    val requests: List<VisionImageRequest>,
)

data class VisionImageRequest(
    val image: VisionImage,
    val features: List<VisionFeature>,
)

/** [content] is the base64-encoded image bytes (standard alphabet, no wrapping). */
data class VisionImage(
    val content: String,
)

data class VisionFeature(
    val type: String,
)

// ── Response DTOs ────────────────────────────────────────────────────────────

data class VisionAnnotateResponse(
    val responses: List<VisionImageResponse> = emptyList(),
)

/**
 * Per-image result. Vision reports per-image failures as an embedded [error] status on an
 * HTTP 200, and simply omits [fullTextAnnotation] when it finds no text.
 */
data class VisionImageResponse(
    val fullTextAnnotation: VisionFullTextAnnotation? = null,
    val error: VisionStatus? = null,
)

data class VisionFullTextAnnotation(
    val text: String? = null,
)

data class VisionStatus(
    val code: Int? = null,
    val message: String? = null,
)

// ── Pure helpers (the VisionRequestTest targets) ─────────────────────────────

/** The feature type for OCR — full-page text detection. */
const val VISION_FEATURE_TEXT_DETECTION = "TEXT_DETECTION"

/**
 * Builds the single-image TEXT_DETECTION annotate request: one request entry carrying the
 * base64 [imageBase64] and one TEXT_DETECTION feature — the §2.3 wire shape
 * `{"requests":[{"image":{"content":…},"features":[{"type":"TEXT_DETECTION"}]}]}`.
 */
fun visionOcrRequest(imageBase64: String): VisionAnnotateRequest = VisionAnnotateRequest(
    requests = listOf(
        VisionImageRequest(
            image = VisionImage(content = imageBase64),
            features = listOf(VisionFeature(type = VISION_FEATURE_TEXT_DETECTION)),
        ),
    ),
)

/**
 * The recognized text of the first image response — `fullTextAnnotation.text` — or `null`
 * when the response is empty, carries a per-image [VisionImageResponse.error], or found no
 * (non-blank) text.
 */
fun VisionAnnotateResponse.firstText(): String? = responses.firstOrNull()
    ?.takeIf { it.error == null }
    ?.fullTextAnnotation
    ?.text
    ?.takeIf { it.isNotBlank() }
