package com.jetsetter.pro.feature.visalookup

/**
 * Single immutable UI-state object for the Visa & Entry screen (guide §6). One object instead of
 * scattered flags, so previews are trivial and impossible states can't arise. The `*` derived
 * properties run the real [VisaCalculator] maths against the current selection + inputs, so the UI
 * never has to recompute or risk drifting out of sync.
 */
data class VisalookupUiState(
    val destinations: List<VisaEntryItem> = emptyList(),
    val selectedId: String? = null,
    /** Search box text used to filter the destination list. Transient — not persisted. */
    val query: String = "",
    /** Raw trip-length input (digits only); blank until the traveller types one. */
    val tripLengthDays: String = "",
    /** Raw passport-expiry input ("YYYY-MM-DD" / "YYYY-MM"); blank until entered. */
    val passportExpiry: String = "",
    /** Per-destination set of documents the traveller has ticked off (destId -> doc labels). */
    val checkedDocs: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = false,
) {
    /** The currently selected destination, derived from [selectedId]. */
    val selected: VisaEntryItem?
        get() = destinations.firstOrNull { it.id == selectedId }

    /** Destinations matching [query] (country or region). Whole list when the query is blank. */
    val filteredDestinations: List<VisaEntryItem>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return destinations
            return destinations.filter {
                it.country.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true)
            }
        }

    /** Parsed, validated trip length; null when the field is empty or not a positive number. */
    val tripDays: Int?
        get() = tripLengthDays.toIntOrNull()?.takeIf { it > 0 }

    /** Documents ticked off for the current selection. */
    val selectedCheckedDocs: List<String>
        get() = checkedDocs[selectedId].orEmpty()

    /** Live stay-length check for the selection, or null when nothing is selected. */
    val stayCheck: VisaCheck?
        get() = selected?.let { VisaCalculator.stayCheck(it, tripDays) }

    /** Live passport-validity check for the selection, or null when nothing is selected. */
    val passportCheck: VisaCheck?
        get() = selected?.let { VisaCalculator.passportCheck(it, passportExpiry) }

    /** Live document-readiness tally for the selection, or null when nothing is selected. */
    val readiness: VisaReadiness?
        get() = selected?.let { VisaCalculator.readiness(it, selectedCheckedDocs) }
}
