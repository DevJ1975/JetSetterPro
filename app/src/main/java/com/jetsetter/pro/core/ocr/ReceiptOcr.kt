package com.jetsetter.pro.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device receipt OCR (spec §2.3/§3.1): ML Kit Text Recognition over a captured bitmap,
 * heuristically parsed into a [ReceiptScan] by [ReceiptParser].
 *
 * The Latin-script model is bundled in the APK, so recognition works on every device with no
 * download gate and no network round-trip — the receipt image never leaves the handset on this
 * path. Failures (recognizer error, unreadable image, blank result) return `null` so callers
 * fall back to manual expense entry.
 */
@Singleton
class ReceiptOcr @Inject constructor() {

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
            // Phase 3 seam: before giving up, fall back to Google Vision `images:annotate`
            // (API_GOOGLE_VISION, isConfigured-gated) per spec §2.3 — ML Kit stays primary.
            return null
        }
        return raw.takeIf { it.isNotBlank() }?.let { ReceiptParser.parse(it) }
    }
}
