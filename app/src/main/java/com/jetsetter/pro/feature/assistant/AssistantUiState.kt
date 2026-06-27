package com.jetsetter.pro.feature.assistant

/**
 * Single immutable UI-state object for the Assistant screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 */
data class AssistantUiState(
    val prompts: List<AssistantPrompt> = emptyList(),
    /** Ids of recently asked prompts, newest first; resolved against [prompts] in the UI. */
    val recentIds: List<String> = emptyList(),
    /** Live search/filter text for the quick-action grid. */
    val query: String = "",
    val selectedPromptId: String? = null,
    val response: AssistantResponse? = null,
    val isResponding: Boolean = false,
    val isLoading: Boolean = false,
) {
    /** Prompts matching the trimmed [query] (title or subtitle), or all prompts when blank. */
    val filteredPrompts: List<AssistantPrompt>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return prompts
            return prompts.filter {
                it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
            }
        }

    /** Recently asked prompts resolved to objects, in recency order; safe if ids are stale. */
    val recentPrompts: List<AssistantPrompt>
        get() = recentIds.mapNotNull { id -> prompts.firstOrNull { it.id == id } }
}
