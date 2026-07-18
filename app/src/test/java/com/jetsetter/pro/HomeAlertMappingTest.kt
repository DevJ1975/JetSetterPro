package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.IrisSuggestion
import com.jetsetter.pro.core.intelligence.IrisSuggestionKind
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.feature.home.AlertSeverity
import com.jetsetter.pro.feature.home.guessArrivalIata
import com.jetsetter.pro.feature.home.toHomeAlert
import com.jetsetter.pro.feature.home.trimNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the pure Home-dashboard projection of the proactive engine's output (plan A5 switchover):
 * [IrisSuggestion] → HomeAlert field mapping + severity table, the arrival-IATA title guess, and
 * the KB note trimming. Pure JVM — no ViewModel, no Android.
 */
class HomeAlertMappingTest {

    private fun suggestion(kind: IrisSuggestionKind) = IrisSuggestion(
        kind = kind,
        title = "Title",
        body = "Body",
        promptToIris = "Do the thing",
        dismissalKey = "${kind.name}:qualifier",
    )

    @Test
    fun toHomeAlert_carriesIdentityAndPrompt() {
        val alert = suggestion(IrisSuggestionKind.PACKING_NUDGE).toHomeAlert()
        assertEquals("PACKING_NUDGE:qualifier", alert.id)
        assertEquals("Title", alert.title)
        assertEquals("Body", alert.message)
        assertEquals("PACKING_NUDGE", alert.category)
        assertEquals("Do the thing", alert.promptToIris)
    }

    @Test
    fun toHomeAlert_severityTable() {
        val warning = setOf(
            IrisSuggestionKind.CHECK_IN_WINDOW,
            IrisSuggestionKind.VISA_CHECK,
            IrisSuggestionKind.TIER_AT_RISK,
        )
        IrisSuggestionKind.entries.forEach { kind ->
            val expected = if (kind in warning) AlertSeverity.WARNING else AlertSeverity.INFO
            assertEquals("severity for $kind", expected, suggestion(kind).toHomeAlert().severity)
        }
    }

    private fun flightItem(title: String) = ItineraryItem(
        title = title,
        type = ItineraryItemType.FLIGHT,
        startDate = "2026-07-20T09:00:00Z",
    )

    @Test
    fun guessArrivalIata_readsArrowSeparatedCode() {
        assertEquals("ATL", guessArrivalIata(flightItem("DL1423 LAS → ATL")))
        assertEquals("ATL", guessArrivalIata(flightItem("DL 1423 · LAS → ATL")))
        assertEquals("JFK", guessArrivalIata(flightItem("LAS -> JFK")))
    }

    @Test
    fun guessArrivalIata_nullWhenNoCode() {
        assertNull(guessArrivalIata(flightItem("Board Dinner — Bacchanalia")))
        assertNull(guessArrivalIata(flightItem("The Ritz-Carlton, Atlanta")))
        assertNull(guessArrivalIata(flightItem("DL1423")))
    }

    @Test
    fun trimNote_firstLineAndCap() {
        assertEquals("Short note.", trimNote("Short note.\nSecond line ignored."))
        val long = "x".repeat(200)
        val trimmed = trimNote(long)
        assertEquals(161, trimmed.length) // 160 chars + ellipsis
        assertEquals('…', trimmed.last())
    }
}
