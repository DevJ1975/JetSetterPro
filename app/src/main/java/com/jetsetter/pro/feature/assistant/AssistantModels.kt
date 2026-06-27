package com.jetsetter.pro.feature.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Grouping for the quick-action prompts so the screen can render labelled sections. Names are
 * module-prefixed to avoid collisions with other feature modules.
 */
enum class AssistantPromptCategory(val label: String) {
    TRIP("Your trip"),
    PACKING("Packing & prep"),
    AIRPORT("At the airport"),
    MONEY("Money"),
}

/**
 * A single tappable quick-action card. The answer itself is no longer stored on the prompt —
 * it is derived on demand from [AssistantTripContext] so every figure stays self-consistent.
 * [followUpIds] reference other prompts the user might ask next.
 */
data class AssistantPrompt(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: AssistantPromptCategory,
    val icon: ImageVector,
    val followUpIds: List<String> = emptyList(),
)

/** Status tone for an [AssistantMetric] chip, mapped to a semantic color in the UI layer. */
enum class AssistantMetricTone { NEUTRAL, POSITIVE, WARNING }

/** A single real, derived figure shown as a status chip on the answer card. */
data class AssistantMetric(
    val label: String,
    val value: String,
    val tone: AssistantMetricTone = AssistantMetricTone.NEUTRAL,
)

/**
 * The canned answer shown after a prompt is tapped. [metrics] surface the underlying numbers as
 * status chips; [followUpIds] are resolved against the prompt list in the UI so they can be
 * rendered as tappable "ask next" chips.
 */
data class AssistantResponse(
    val promptId: String,
    val promptTitle: String,
    val body: String,
    val metrics: List<AssistantMetric> = emptyList(),
    val followUpIds: List<String> = emptyList(),
)

/** One spend line in the trip wallet — amounts in whole cents to stay exact. */
data class AssistantExpense(val label: String, val amountCents: Long)

/** One packing-list entry with its packed flag. */
data class AssistantPackingEntry(val name: String, val packed: Boolean)

/**
 * Single source of truth for the Assistant's answers. Every figure rendered in a response is
 * derived from this object (sums, counts, remainders), so nothing on screen can contradict
 * anything else. Mock-first: this is a curated, deterministic stand-in for a live trip feed.
 */
data class AssistantTripContext(
    val destination: String,
    val dateRange: String,
    val tripDays: Int,
    val flightNumber: String,
    val flightRoute: String,
    val terminal: String,
    val gate: String,
    val seat: String,
    val boardingZone: String,
    val boardingTime: String,
    val departureTime: String,
    val hotel: String,
    val hotelCheckIn: String,
    val keynote: String,
    val keynoteVenue: String,
    val keynoteTime: String,
    val highF: Int,
    val lowF: Int,
    val stormDay: String,
    val loungeName: String,
    val loungeConcourse: String,
    val loungeWalkMinutes: Int,
    val loungeOpens: String,
    val budgetCents: Long,
    val expenses: List<AssistantExpense>,
    val packing: List<AssistantPackingEntry>,
) {
    val spentCents: Long get() = expenses.sumOf { it.amountCents }
    val remainingCents: Long get() = budgetCents - spentCents
    val topExpense: AssistantExpense? get() = expenses.maxByOrNull { it.amountCents }
    val packedCount: Int get() = packing.count { it.packed }
    val unpacked: List<AssistantPackingEntry> get() = packing.filter { !it.packed }
}

/** Convenience icon catalog kept here so the repository seed data stays terse. */
internal object AssistantIcons {
    val summary: ImageVector = Icons.Filled.Summarize
    val schedule: ImageVector = Icons.Filled.Event
    val packing: ImageVector = Icons.Filled.Checklist
    val weather: ImageVector = Icons.Filled.WbSunny
    val gate: ImageVector = Icons.Filled.MeetingRoom
    val lounge: ImageVector = Icons.Filled.Weekend
    val expenses: ImageVector = Icons.Filled.Payments
}

/** Exact, comma-grouped USD formatting shared by the repository and previews. */
internal fun formatMoney(cents: Long): String = "$%,.2f".format(cents / 100.0)
