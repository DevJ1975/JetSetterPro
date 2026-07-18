package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.vision.VISION_FEATURE_TEXT_DETECTION
import com.jetsetter.pro.core.data.remote.vision.VisionAnnotateRequest
import com.jetsetter.pro.core.data.remote.vision.VisionAnnotateResponse
import com.jetsetter.pro.core.data.remote.vision.VisionFullTextAnnotation
import com.jetsetter.pro.core.data.remote.vision.VisionImageResponse
import com.jetsetter.pro.core.data.remote.vision.VisionStatus
import com.jetsetter.pro.core.data.remote.vision.firstText
import com.jetsetter.pro.core.data.remote.vision.visionOcrRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the pure parts of the Google Vision OCR-fallback seam (plan B7/R10f): the
 * `images:annotate` request-body shape built by `visionOcrRequest`, and the `firstText`
 * response extraction `GoogleVisionService.ocr` relies on. Pure JVM — no network, no Android.
 */
class VisionRequestTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(VisionAnnotateRequest::class.java)

    // ── Request builder ──────────────────────────────────────────────────────

    @Test
    fun request_serializesToDocumentedWireShape() {
        val json = requestAdapter.toJson(visionOcrRequest("QUJDMTIz"))

        assertEquals(
            """{"requests":[{"image":{"content":"QUJDMTIz"},"features":[{"type":"TEXT_DETECTION"}]}]}""",
            json,
        )
    }

    @Test
    fun request_carriesExactlyOneImageAndOneTextDetectionFeature() {
        val request = visionOcrRequest("Zm9v")

        assertEquals(1, request.requests.size)
        val image = request.requests.single()
        assertEquals("Zm9v", image.image.content)
        assertEquals(1, image.features.size)
        assertEquals(VISION_FEATURE_TEXT_DETECTION, image.features.single().type)
    }

    @Test
    fun request_passesBase64ContentThroughVerbatim() {
        // The builder must never re-encode/trim — the caller already produced NO_WRAP base64.
        val base64 = "aGVsbG8gd29ybGQ="
        assertEquals(base64, visionOcrRequest(base64).requests.single().image.content)
    }

    @Test
    fun featureConstant_isTextDetection() {
        assertEquals("TEXT_DETECTION", VISION_FEATURE_TEXT_DETECTION)
    }

    // ── Response extraction ──────────────────────────────────────────────────

    @Test
    fun firstText_returnsRecognizedText() {
        val response = VisionAnnotateResponse(
            responses = listOf(
                VisionImageResponse(
                    fullTextAnnotation = VisionFullTextAnnotation(text = "STARBUCKS\nTOTAL $6.45"),
                ),
            ),
        )

        assertEquals("STARBUCKS\nTOTAL $6.45", response.firstText())
    }

    @Test
    fun firstText_nullWhenResponsesEmpty() {
        assertNull(VisionAnnotateResponse().firstText())
    }

    @Test
    fun firstText_nullOnPerImageError() {
        // Vision reports per-image failures embedded in an HTTP 200 — must read as a miss.
        val response = VisionAnnotateResponse(
            responses = listOf(
                VisionImageResponse(
                    fullTextAnnotation = VisionFullTextAnnotation(text = "should be ignored"),
                    error = VisionStatus(code = 3, message = "Bad image data."),
                ),
            ),
        )

        assertNull(response.firstText())
    }

    @Test
    fun firstText_nullWhenAnnotationMissingOrBlank() {
        assertNull(VisionAnnotateResponse(responses = listOf(VisionImageResponse())).firstText())
        assertNull(
            VisionAnnotateResponse(
                responses = listOf(
                    VisionImageResponse(fullTextAnnotation = VisionFullTextAnnotation(text = "   ")),
                ),
            ).firstText(),
        )
    }
}
