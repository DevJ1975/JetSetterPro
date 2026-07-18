package com.jetsetter.pro.core.ai

import com.jetsetter.pro.core.model.ExpenseCategory

/**
 * On-device expense categorization seam (spec §3.1).
 *
 * Given a merchant name (plus optional notes and OCR'd receipt text), an implementation proposes
 * one of the eight user-assignable categories — FOOD, TRANSPORT, ACCOMMODATION, ENTERTAINMENT,
 * BUSINESS, SHOPPING, MEDICAL, OTHER. Implementations must **never** return
 * [ExpenseCategory.MILEAGE] (mileage entries are user-declared, not inferred) and must return
 * `null` whenever no confident on-device classification is possible, so callers keep the manual
 * picker as the source of truth and only *prefill* the suggestion.
 *
 * Consumers: expense entry prefill, the `logExpense` IRIS tool (category omitted), and the
 * consent-gated `receiptScanned` learning signal. The default binding is [NanoExpenseCategorizer]
 * (see `core/di/AiModule`), which self-gates and returns `null` when Gemini Nano is unavailable.
 */
interface ExpenseCategorizer {

    /**
     * Classifies an expense on-device. [receiptText] is raw OCR output (implementations truncate
     * to ~400 chars per spec). Returns `null` when the classifier is unavailable or unsure.
     */
    suspend fun categorize(
        merchant: String,
        notes: String? = null,
        receiptText: String? = null,
    ): ExpenseCategory?
}

/**
 * Default [ExpenseCategorizer]: always `null`, so expense entry stays fully manual. Keeps the
 * seam wired (call sites, DI, tests) without taking on the device-gated Gemini Nano dependency
 * until the real implementation lands.
 */
object NoopExpenseCategorizer : ExpenseCategorizer {
    override suspend fun categorize(
        merchant: String,
        notes: String?,
        receiptText: String?,
    ): ExpenseCategory? = null
}
