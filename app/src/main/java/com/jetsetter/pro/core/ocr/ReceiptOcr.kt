package com.jetsetter.pro.core.ocr

import android.graphics.Bitmap
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jetsetter.pro.core.data.remote.getOrNull
import com.jetsetter.pro.core.data.remote.vision.GoogleVisionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device receipt OCR (spec §2.3/§3.1): ML Kit Text Recognition over a captured bitmap,
 * heuristically parsed into a [ReceiptScan] by [ReceiptParser].
 *
 * The Latin-script model is bundled in the APK, so recognition works on every device with no
 * download gate and no network round-trip — the receipt image never leaves the handset on this
 * path. When ML Kit itself fails (recognizer error, unavailable model), the Phase 3 fallback
 * tries Google Vision `images:annotate` — but only when the key is configured; an unconfigured
 * key or a Vision miss still returns `null` so callers fall back to manual expense entry.
 */
@Singleton
class ReceiptOcr @Inject constructor(
    private val vision: GoogleVisionService,
) {

    // Lazy so the native model loads on first scan, not at injection time.
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognizes text in [bitmap] and parses it into a [ReceiptScan], or `null` when recognition
     * fails or finds no text.
     */
    suspend fun scan(bitmap: Bitmap): ReceiptScan? {
        val raw = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Phase 3 fallback: ML Kit failed/unavailable — try Google Vision `images:annotate`
            // (API_GOOGLE_VISION, isConfigured-gated) per spec §2.3. ML Kit stays primary;
            // unconfigured key or a Vision miss keeps the null-on-failure contract.
            visionFallbackText(bitmap) ?: return null
        }
        return raw.takeIf { it.isNotBlank() }?.let { ReceiptParser.parse(it) }
    }

    /**
     * The Vision leg of the fallback: JPEG-compress the bitmap, base64-encode it (standard
     * alphabet, no wrapping — the shape `VisionImage.content` documents), and run TEXT_DETECTION.
     * Null when the key is unconfigured, encoding fails, or Vision finds no text.
     */
    private suspend fun visionFallbackText(bitmap: Bitmap): String? {
        if (!vision.isConfigured) return null
        val base64 = runCatching {
            ByteArrayOutputStream().use { buffer ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, buffer)
                Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
            }
        }.getOrNull() ?: return null
        return vision.ocr(base64).getOrNull()
    }

    private companion object {
        /** Balances legible receipt glyphs against upload size for the billed annotate call. */
        const val VISION_JPEG_QUALITY = 85
    }
}
