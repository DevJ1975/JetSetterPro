package com.jetsetter.pro.core.ocr

import com.jetsetter.pro.core.util.IsoDates
import java.time.LocalDate

/**
 * Pure receipt-text heuristics (spec §3.1): turns raw OCR output into a best-effort [ReceiptScan].
 *
 * Deterministic, no Android deps — the main test target for the OCR path. The rules are shared
 * with the iOS parser:
 *  - **merchant** — the first non-empty line that isn't amount- or date-like (receipts open with
 *    the store name; address/phone lines are letter-bearing too, so this is first-match only).
 *  - **amount** — the largest money-formatted number (`$12.34`, `1,234.56`, `12,34`) on a line
 *    containing TOTAL/AMOUNT/BALANCE (case-insensitive); when no keyword line has one, the
 *    largest money-formatted number anywhere.
 *  - **currency** — the symbol attached to the winning amount ($→USD, €→EUR, £→GBP), else the
 *    first explicit ISO-4217 code in the text, else any symbol anywhere.
 *  - **date** — first recognizable date scanning top-down: ISO-8601 (via [IsoDates]), then
 *    `MM/dd/yyyy` (falling back to `dd/MM/yyyy` when the first field can't be a month), then
 *    `MMM d yyyy` / `MMM d, yyyy`.
 *
 * Anything unrecognizable stays `null` — garbage in, nulls out, never a throw.
 */
object ReceiptParser {

    /** Optional currency symbol + money-formatted number: `$1,234.56`, `43.50`, `€12,34`. */
    private val MONEY = Regex("""([€£$])?\s*(\d{1,3}(?:,\d{3})+\.\d{2}(?!\d)|\d+\.\d{2}(?!\d)|\d+,\d{2}(?!\d))""")

    /** Lines whose amounts take precedence over ordinary line items. */
    private val AMOUNT_KEYWORDS = Regex("""(?i)total|amount|balance""")

    private val SYMBOL = Regex("""[€£$]""")
    private val SYMBOL_TO_CODE = mapOf("$" to "USD", "€" to "EUR", "£" to "GBP")

    /** Explicit codes recognized in receipt text (uppercase, standalone word). */
    private val KNOWN_CODES = setOf(
        "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "MXN", "INR",
        "SGD", "HKD", "KRW", "THB", "AED", "NZD", "SEK", "NOK", "DKK", "BRL",
    )
    private val CODE = Regex("""\b[A-Z]{3}\b""")

    private val ISO_DATE = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    private val SLASH_DATE = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val MONTH_NAME_DATE =
        Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2}),?\s+(\d{4})\b""")
    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Parses [rawText] into a [ReceiptScan]; unrecognizable fields are `null`, never a throw. */
    fun parse(rawText: String): ReceiptScan {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val amountMatch = findAmount(lines)
        return ReceiptScan(
            merchant = lines.firstOrNull { !isAmountOrDateLike(it) && !AMOUNT_KEYWORDS.containsMatchIn(it) },
            amount = amountMatch?.let { toDouble(it.groupValues[2]) },
            currency = detectCurrency(amountMatch?.groupValues?.get(1), rawText),
            date = findDate(lines),
            rawText = rawText,
        )
    }

    // ---- amount --------------------------------------------------------------------------

    private fun findAmount(lines: List<String>): MatchResult? {
        val keywordMatches = lines
            .filter { AMOUNT_KEYWORDS.containsMatchIn(it) }
            .flatMap { MONEY.findAll(it).toList() }
        val candidates = keywordMatches.ifEmpty { lines.flatMap { MONEY.findAll(it).toList() } }
        return candidates.maxByOrNull { toDouble(it.groupValues[2]) ?: Double.NEGATIVE_INFINITY }
    }

    /** `1,234.56` → 1234.56 (comma = thousands); `12,34` → 12.34 (comma = decimal). */
    private fun toDouble(num: String): Double? {
        val normalized = if (num.contains('.')) num.replace(",", "") else num.replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    // ---- currency ------------------------------------------------------------------------

    private fun detectCurrency(winningSymbol: String?, rawText: String): String? {
        winningSymbol?.takeIf { it.isNotEmpty() }?.let { return SYMBOL_TO_CODE[it] }
        CODE.findAll(rawText).map { it.value }.firstOrNull { it in KNOWN_CODES }?.let { return it }
        SYMBOL.find(rawText)?.let { return SYMBOL_TO_CODE[it.value] }
        return null
    }

    // ---- date ----------------------------------------------------------------------------

    private fun findDate(lines: List<String>): LocalDate? =
        lines.firstNotNullOfOrNull { parseDateIn(it) }

    private fun parseDateIn(line: String): LocalDate? {
        ISO_DATE.find(line)?.let { m ->
            IsoDates.parseDate(m.value)?.let { return it }
        }
        SLASH_DATE.find(line)?.let { m ->
            val (a, b, year) = m.destructured
            parseSlashDate(a.toInt(), b.toInt(), year.toInt())?.let { return it }
        }
        MONTH_NAME_DATE.find(line)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[1].lowercase()) + 1
            runCatching {
                return LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
            }
        }
        return null
    }

    /** `MM/dd/yyyy` first; `dd/MM/yyyy` when the first field can't be (or doesn't form) a month. */
    private fun parseSlashDate(a: Int, b: Int, year: Int): LocalDate? {
        if (a in 1..12) runCatching { return LocalDate.of(year, a, b) }
        if (b in 1..12) runCatching { return LocalDate.of(year, b, a) }
        return null
    }

    // ---- merchant ------------------------------------------------------------------------

    private fun isAmountOrDateLike(line: String): Boolean =
        MONEY.containsMatchIn(line) ||
            ISO_DATE.containsMatchIn(line) ||
            SLASH_DATE.containsMatchIn(line) ||
            MONTH_NAME_DATE.containsMatchIn(line) ||
            line.none { it.isLetter() }
}
