package com.jetsetter.pro.feature.more

import com.jetsetter.pro.core.ai.NanoModelManager
import com.jetsetter.pro.core.model.UserPreferences

/**
 * Single immutable UI-state object for the More screen — preferences, the live Features search
 * query, and the on-device AI status, so the stateless content is a pure function of one input.
 */
data class MoreUiState(
    val preferences: UserPreferences = UserPreferences(),
    val searchQuery: String = "",
    val nanoState: NanoModelManager.State = NanoModelManager.State.Unknown,
)

/**
 * Tiny persisted shape for the Features filter. Serialized as JSON via Moshi into
 * [com.jetsetter.pro.core.data.prefs.ModuleStateStore], mirroring the Converters.kt idiom, so the
 * last-used filter survives process death and is trivial to extend later.
 */
data class MoreFilterState(
    val searchQuery: String = "",
)
