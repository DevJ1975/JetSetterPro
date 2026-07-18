package com.jetsetter.pro

import com.jetsetter.pro.core.ai.NanoCategorizerLogic
import com.jetsetter.pro.core.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the pure prompt-build + response-validation contract behind
 * [com.jetsetter.pro.core.ai.NanoExpenseCategorizer] (spec §3.1): the prompt offers exactly the
 * eight user-assignable lowercase labels and never mentions mileage; validation trims/lowercases
 * then exact-matches, mapping each label to its [ExpenseCategory] and rejecting everything else.
 * Pure-JVM (no ML Kit / Android deps).
 */
class NanoCategorizerLogicTest {

    // ---- prompt ----

    @Test
    fun prompt_containsAllEightLabels() {
        val prompt = NanoCategorizerLogic.buildPrompt("Nobu", null, null)
        listOf(
            "food", "transport", "accommodation", "entertainment",
            "business", "shopping", "medical", "other",
        ).forEach { label ->
            assertTrue("prompt should offer '$label'", prompt.contains(label))
        }
    }

    @Test
    fun prompt_neverMentionsMileage() {
        val prompt = NanoCategorizerLogic.buildPrompt("Hertz", "rental car", "RECEIPT #42")
        assertFalse(prompt.lowercase().contains("mileage"))
    }

    @Test
    fun allowedLabels_areTheEnumMinusMileage() {
        assertEquals(8, NanoCategorizerLogic.allowedLabels.size)
        assertFalse(NanoCategorizerLogic.allowedLabels.contains("mileage"))
        assertEquals(
            ExpenseCategory.entries.filter { it != ExpenseCategory.MILEAGE }.map { it.name.lowercase() },
            NanoCategorizerLogic.allowedLabels,
        )
    }

    @Test
    fun prompt_instructsSingleWordAnswer() {
        val prompt = NanoCategorizerLogic.buildPrompt("Nobu", null, null)
        assertTrue(prompt.contains("a single lowercase word"))
        assertTrue(prompt.contains("the single word only"))
        assertTrue(prompt.contains("exactly one category"))
    }

    @Test
    fun prompt_includesMerchantNotesAndReceiptSections() {
        val prompt = NanoCategorizerLogic.buildPrompt("  Nobu Malibu  ", "team dinner", "SUSHI OMAKASE 240.00")
        assertTrue(prompt.contains("Merchant: Nobu Malibu")) // trimmed
        assertTrue(prompt.contains("Notes: team dinner"))
        assertTrue(prompt.contains("Receipt text: SUSHI OMAKASE 240.00"))
    }

    @Test
    fun prompt_omitsBlankOptionalSections() {
        val prompt = NanoCategorizerLogic.buildPrompt("Uber", "   ", null)
        assertFalse(prompt.contains("Notes:"))
        assertFalse(prompt.contains("Receipt text:"))
    }

    @Test
    fun prompt_truncatesReceiptTextTo400Chars() {
        val receipt = "x".repeat(NanoCategorizerLogic.MAX_RECEIPT_CHARS) + "OVERFLOW"
        val prompt = NanoCategorizerLogic.buildPrompt("CVS", null, receipt)
        assertTrue(prompt.contains("x".repeat(NanoCategorizerLogic.MAX_RECEIPT_CHARS)))
        assertFalse(prompt.contains("OVERFLOW"))
    }

    // ---- validation ----

    @Test
    fun validate_mapsEveryExactLabelToItsCategory() {
        val expected = mapOf(
            "food" to ExpenseCategory.FOOD,
            "transport" to ExpenseCategory.TRANSPORT,
            "accommodation" to ExpenseCategory.ACCOMMODATION,
            "entertainment" to ExpenseCategory.ENTERTAINMENT,
            "business" to ExpenseCategory.BUSINESS,
            "shopping" to ExpenseCategory.SHOPPING,
            "medical" to ExpenseCategory.MEDICAL,
            "other" to ExpenseCategory.OTHER,
        )
        expected.forEach { (label, category) ->
            assertEquals(category, NanoCategorizerLogic.validate(label))
        }
    }

    @Test
    fun validate_trimsAndLowercasesBeforeMatching() {
        assertEquals(ExpenseCategory.FOOD, NanoCategorizerLogic.validate("  Food \n"))
        assertEquals(ExpenseCategory.TRANSPORT, NanoCategorizerLogic.validate("TRANSPORT"))
    }

    @Test
    fun validate_rejectsMileageInAnyCasing() {
        assertNull(NanoCategorizerLogic.validate("Mileage"))
        assertNull(NanoCategorizerLogic.validate("mileage"))
        assertNull(NanoCategorizerLogic.validate("MILEAGE"))
    }

    @Test
    fun validate_rejectsUnknownLabels() {
        assertNull(NanoCategorizerLogic.validate("unknown"))
        assertNull(NanoCategorizerLogic.validate("groceries"))
    }

    @Test
    fun validate_rejectsMultiWordAndProseAnswers() {
        assertNull(NanoCategorizerLogic.validate("food and drink"))
        assertNull(NanoCategorizerLogic.validate("I'd classify this as food"))
        assertNull(NanoCategorizerLogic.validate("food.")) // punctuation breaks the exact match
    }

    @Test
    fun validate_rejectsNullBlankAndEmpty() {
        assertNull(NanoCategorizerLogic.validate(null))
        assertNull(NanoCategorizerLogic.validate(""))
        assertNull(NanoCategorizerLogic.validate("   "))
    }
}
