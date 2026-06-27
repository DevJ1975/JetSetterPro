package com.jetsetter.pro.feature.more

import com.jetsetter.pro.core.model.UserPreferences

/**
 * Single immutable UI-state object for the More screen — preferences plus the live Features
 * search query, so the stateless content is a pure function of one input (guide §6).
 */
data class MoreUiState(
    val preferences: UserPreferences = UserPreferences(),
    val searchQuery: String = "",
)

/**
 * Tiny persisted shape for the Features filter. Serialized as JSON via Moshi into
 * [com.jetsetter.pro.core.data.prefs.ModuleStateStore], mirroring the Converters.kt idiom, so the
 * last-used filter survives process death and is trivial to extend later.
 */
data class MoreFilterState(
    val searchQuery: String = "",
)
