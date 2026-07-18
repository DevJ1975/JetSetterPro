package com.jetsetter.pro.core.ocr

import java.time.LocalDate

/**
 * Structured fields extracted from a scanned receipt (spec §3.1) — the cross-platform contract
 * shared with the iOS receipt scanner.
 *
 * Every field except [rawText] is best-effort: [ReceiptParser] fills what it can recognize and
 * leaves the rest `null`, so callers prefill the expense-entry form rather than commit anything.
 * [rawText] always carries the full recognized text — it feeds the on-device
 * [com.jetsetter.pro.core.ai.ExpenseCategorizer] (truncated there, not here) and the
 * `receiptScanned` learning signal.
 */
data class ReceiptScan(
    /** Best-guess merchant name — the first receipt line that isn't an amount/date. */
    val merchant: String?,
    /** Best-guess total in [currency] units. */
    val amount: Double?,
    /** ISO-4217 code inferred from a currency symbol or explicit code, e.g. "USD". */
    val currency: String?,
    /** Purchase date when a recognizable date appears on the receipt. */
    val date: LocalDate?,
    /** The full recognized text, verbatim. */
    val rawText: String,
)
