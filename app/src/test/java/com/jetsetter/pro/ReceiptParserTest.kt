package com.jetsetter.pro

import com.jetsetter.pro.core.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the pure receipt heuristics in [ReceiptParser] (spec §3.1): keyword-anchored total
 * extraction, symbol/code currency detection, first-line merchant, common date formats, and the
 * garbage-in → nulls-out contract. Pure-JVM (no Android deps).
 */
class ReceiptParserTest {

    // ---- amount --------------------------------------------------------------------------

    @Test
    fun amount_keywordLine_beatsLargerLineItem() {
        val scan = ReceiptParser.parse("Nobu Malibu\nWagyu Tasting 99.99\nTOTAL 43.50")
        assertEquals(43.50, scan.amount!!, 1e-9)
    }

    @Test
    fun amount_largestAcrossKeywordLines() {
        val scan = ReceiptParser.parse("SUBTOTAL 40.00\nTAX 3.50\nTOTAL \$43.50")
        assertEquals(43.50, scan.amount!!, 1e-9)
    }

    @Test
    fun amount_balanceAndAmountKeywordsAlsoAnchor() {
        assertEquals(18.20, ReceiptParser.parse("Latte 5.00\nBALANCE DUE 18.20").amount!!, 1e-9)
        assertEquals(18.20, ReceiptParser.parse("Latte 5.00\nAmount: 18.20").amount!!, 1e-9)
    }

    @Test
    fun amount_fallsBackToLargestDecimal_whenNoKeywords() {
        val scan = ReceiptParser.parse("Latte 5.00\nBagel 12.34")
        assertEquals(12.34, scan.amount!!, 1e-9)
    }

    @Test
    fun amount_parsesThousandsSeparators() {
        val scan = ReceiptParser.parse("Ritz Paris\nTOTAL \$1,234.56")
        assertEquals(1234.56, scan.amount!!, 1e-9)
    }

    @Test
    fun amount_parsesEuropeanDecimalComma() {
        val scan = ReceiptParser.parse("Cafe de Flore\nTOTAL €12,34")
        assertEquals(12.34, scan.amount!!, 1e-9)
        assertEquals("EUR", scan.currency)
    }

    // ---- currency ------------------------------------------------------------------------

    @Test
    fun currency_fromSymbolOnWinningAmount() {
        assertEquals("USD", ReceiptParser.parse("Diner\nTOTAL \$10.00").currency)
        assertEquals("EUR", ReceiptParser.parse("Bistro\nTOTAL €10.00").currency)
        assertEquals("GBP", ReceiptParser.parse("Pub\nTOTAL £10.00").currency)
    }

    @Test
    fun currency_fromExplicitCode_whenNoSymbolOnAmount() {
        val scan = ReceiptParser.parse("Brauhaus\nTOTAL 45.00 EUR")
        assertEquals("EUR", scan.currency)
    }

    @Test
    fun currency_fromSymbolElsewhere_whenWinningAmountIsBare() {
        // The keyword line has no symbol, but the line item does — fall back to any symbol.
        val scan = ReceiptParser.parse("Starbucks\n£4.10 flat white\nTOTAL 4.10")
        assertEquals("GBP", scan.currency)
    }

    @Test
    fun currency_null_whenNoSymbolOrCode() {
        assertNull(ReceiptParser.parse("Diner\nTOTAL 10.00").currency)
    }

    // ---- merchant ------------------------------------------------------------------------

    @Test
    fun merchant_isFirstLine() {
        val scan = ReceiptParser.parse("Nobu Malibu\n123 Ocean Ave\nTOTAL \$88.00")
        assertEquals("Nobu Malibu", scan.merchant)
    }

    @Test
    fun merchant_skipsDateAndAmountLikeLines() {
        val scan = ReceiptParser.parse("07/14/2026\n\$5.75\nStarbucks Reserve\nTOTAL \$5.75")
        assertEquals("Starbucks Reserve", scan.merchant)
        assertEquals(LocalDate.of(2026, 7, 14), scan.date)
    }

    @Test
    fun merchant_skipsKeywordOnlyLines() {
        val scan = ReceiptParser.parse("TOTAL\nJoe's Garage")
        assertEquals("Joe's Garage", scan.merchant)
    }

    // ---- date ----------------------------------------------------------------------------

    @Test
    fun date_isoForm() {
        val scan = ReceiptParser.parse("Shop\nDate: 2026-07-14\nTOTAL 9.99")
        assertEquals(LocalDate.of(2026, 7, 14), scan.date)
    }

    @Test
    fun date_mmDdYyyy() {
        assertEquals(LocalDate.of(2026, 7, 14), ReceiptParser.parse("Shop\n07/14/2026").date)
    }

    @Test
    fun date_ddMmYyyy_whenFirstFieldCannotBeMonth() {
        assertEquals(LocalDate.of(2026, 7, 14), ReceiptParser.parse("Shop\n14/07/2026").date)
    }

    @Test
    fun date_ambiguousSlashForm_prefersMmDd() {
        assertEquals(LocalDate.of(2026, 5, 6), ReceiptParser.parse("Shop\n05/06/2026").date)
    }

    @Test
    fun date_monthNameForms() {
        assertEquals(LocalDate.of(2026, 7, 14), ReceiptParser.parse("Shop\nJul 14, 2026").date)
        assertEquals(LocalDate.of(2026, 7, 14), ReceiptParser.parse("Shop\nJuly 14 2026").date)
    }

    @Test
    fun date_null_whenAbsent() {
        assertNull(ReceiptParser.parse("Shop\nTOTAL 9.99").date)
    }

    // ---- garbage -------------------------------------------------------------------------

    @Test
    fun garbage_yieldsNulls_andPreservesRawText() {
        val raw = "@@@@\n----\n12 34"
        val scan = ReceiptParser.parse(raw)
        assertNull(scan.merchant)
        assertNull(scan.amount)
        assertNull(scan.currency)
        assertNull(scan.date)
        assertEquals(raw, scan.rawText)
    }

    @Test
    fun emptyInput_yieldsNulls() {
        val scan = ReceiptParser.parse("")
        assertNull(scan.merchant)
        assertNull(scan.amount)
        assertNull(scan.currency)
        assertNull(scan.date)
        assertEquals("", scan.rawText)
    }
}
