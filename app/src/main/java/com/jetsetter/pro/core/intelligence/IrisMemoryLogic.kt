package com.jetsetter.pro.core.intelligence

/**
 * Pure remember/recall/summarize rules behind [IrisMemory] (spec §1.5), separated so the
 * confidence math is unit-testable without Android/DataStore.
 *
 * remember(): create at [INITIAL_CONFIDENCE]; each reinforcement adds [REINFORCEMENT_STEP],
 * capped at [MAX_CONFIDENCE]. A reinforcement is a case-insensitive match on
 * (category, trimmed value) — "Window Seat" reinforces "window seat", it doesn't duplicate it.
 */
internal object IrisMemoryLogic {

    const val INITIAL_CONFIDENCE = 0.7
    const val REINFORCEMENT_STEP = 0.1
    const val MAX_CONFIDENCE = 1.0

    /**
     * Returns [existing] with ([category], [value]) either appended at [INITIAL_CONFIDENCE] or —
     * when already present — reinforced (+[REINFORCEMENT_STEP], capped, `lastReinforcedAt` =
     * [nowIso]). Blank values are ignored. List order is preserved.
     */
    fun rememberInto(
        existing: List<IrisPreference>,
        category: IrisPreferenceCategory,
        value: String,
        nowIso: String,
    ): List<IrisPreference> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return existing
        val match = existing.firstOrNull {
            it.category == category && it.value.trim().equals(trimmed, ignoreCase = true)
        }
        return if (match == null) {
            existing + IrisPreference(
                category = category,
                value = trimmed,
                createdAt = nowIso,
                lastReinforcedAt = nowIso,
                confidence = INITIAL_CONFIDENCE,
            )
        } else {
            existing.map { pref ->
                if (pref.id == match.id) {
                    pref.copy(
                        confidence = minOf(MAX_CONFIDENCE, pref.confidence + REINFORCEMENT_STEP),
                        lastReinforcedAt = nowIso,
                    )
                } else {
                    pref
                }
            }
        }
    }

    /** [list] (optionally only [category]) sorted by confidence descending; ties keep list order. */
    fun recallSorted(
        list: List<IrisPreference>,
        category: IrisPreferenceCategory? = null,
    ): List<IrisPreference> =
        list.filter { category == null || it.category == category }
            .sortedByDescending { it.confidence }

    /**
     * Prompt-ready summary: `""` when [list] is empty, else one bullet line per category present
     * (in enum declaration order), values joined by confidence descending — e.g.
     * `- seating: window seat, exit row`.
     */
    fun summaryFor(list: List<IrisPreference>): String {
        if (list.isEmpty()) return ""
        return IrisPreferenceCategory.entries.mapNotNull { category ->
            val values = recallSorted(list, category).map(IrisPreference::value)
            if (values.isEmpty()) null else "- ${category.wireName}: ${values.joinToString(", ")}"
        }.joinToString("\n")
    }
}
