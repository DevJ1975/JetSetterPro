package com.jetsetter.pro.core.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.jetsetter.pro.core.model.ExpenseCategory
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real on-device [ExpenseCategorizer] (spec §3.1): Gemini Nano via the ML Kit GenAI Prompt API.
 *
 * Self-gating like [GeminiNanoOnDeviceAi]: unless the AICore feature is genuinely `AVAILABLE`
 * (model downloaded + ready) every call returns `null`, so expense entry stays fully manual on
 * devices without the runtime — no download is ever triggered from here.
 *
 * Determinism comes from two layers: greedy decoding (temperature 0, topK 1, single candidate)
 * and strict post-validation in [NanoCategorizerLogic] — the response must exact-match one of the
 * eight allowed lowercase labels after trim/lowercase, else one retry, else `null` (fail closed to
 * the manual picker). `MILEAGE` can never be produced: it is absent from the prompt's label list
 * and rejected by validation.
 *
 * PRIVACY: classification runs entirely on-device; merchant/notes/receipt text never leave it.
 */
@Singleton
class NanoExpenseCategorizer @Inject constructor(
    private val manager: NanoModelManager,
) : ExpenseCategorizer {

    override suspend fun categorize(
        merchant: String,
        notes: String?,
        receiptText: String?,
    ): ExpenseCategory? {
        if (merchant.isBlank() && notes.isNullOrBlank() && receiptText.isNullOrBlank()) return null
        val available = runCatching { manager.status() == FeatureStatus.AVAILABLE }.getOrDefault(false)
        if (!available) return null
        val model = manager.client() ?: return null

        val request = deterministicRequest(NanoCategorizerLogic.buildPrompt(merchant, notes, receiptText))
        // Strict post-validation is the hard guarantee; one retry (spec §3.1) covers a stray
        // near-miss (e.g. trailing prose), after which we fail closed to manual selection.
        repeat(ATTEMPTS) {
            val text = runCatching { model.generateContent(request) }
                .getOrNull()?.candidates?.firstOrNull()?.text
                ?: return null // generation error: don't hammer a failing runtime
            NanoCategorizerLogic.validate(text)?.let { return it }
        }
        return null
    }

    /** Greedy, single-candidate decoding — the Prompt API's closest analog of "temperature 0". */
    private fun deterministicRequest(prompt: String): GenerateContentRequest =
        GenerateContentRequest.Builder(TextPart(prompt)).apply {
            temperature = 0.0f
            topK = 1
            candidateCount = 1
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }.build()

    private companion object {
        /** One initial attempt + one retry on a validation mismatch. */
        const val ATTEMPTS = 2

        /** A valid answer is a single word; anything longer fails validation anyway. */
        const val MAX_OUTPUT_TOKENS = 8
    }
}

/**
 * Pure prompt-building + response-validation behind [NanoExpenseCategorizer], extracted so the
 * contract is JVM-testable without the ML Kit runtime (see `NanoCategorizerLogicTest`).
 */
internal object NanoCategorizerLogic {

    /** Spec §3.1: raw OCR text is truncated to ~400 chars before prompting. */
    const val MAX_RECEIPT_CHARS = 400

    /**
     * The eight labels the model may answer with — every [ExpenseCategory] except `MILEAGE`.
     * Mileage entries are user-declared, never inferred, so the label is deliberately absent
     * from the prompt entirely and [validate] rejects it defensively.
     */
    val allowedLabels: List<String> =
        ExpenseCategory.entries
            .filter { it != ExpenseCategory.MILEAGE }
            .map { it.name.lowercase(Locale.US) }

    /** Builds the classification prompt; blank optional sections are omitted. */
    fun buildPrompt(merchant: String, notes: String?, receiptText: String?): String = buildString {
        appendLine("Classify the travel expense below into exactly one category.")
        appendLine("Answer with a single lowercase word, chosen only from this list:")
        appendLine(allowedLabels.joinToString(", "))
        appendLine("Answer with the single word only — no punctuation, no explanation, no other words.")
        appendLine()
        appendLine("Merchant: ${merchant.trim()}")
        notes?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("Notes: $it") }
        receiptText?.takeIf { it.isNotBlank() }?.let {
            appendLine("Receipt text: ${it.take(MAX_RECEIPT_CHARS)}")
        }
        append("Category:")
    }

    /**
     * Strict post-validation: trim + lowercase, then exact-match against [allowedLabels].
     * Anything else — "Mileage", "unknown", multi-word prose, blank — returns `null`.
     */
    fun validate(response: String?): ExpenseCategory? {
        val label = response?.trim()?.lowercase(Locale.US) ?: return null
        if (label !in allowedLabels) return null
        return ExpenseCategory.valueOf(label.uppercase(Locale.US))
    }
}
