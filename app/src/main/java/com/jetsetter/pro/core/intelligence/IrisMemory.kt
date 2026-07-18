package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.PrefKeys
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * IRIS's explicit preference memory (spec §1.5) — things the user asked IRIS to remember,
 * persisted as a Moshi-serialized JSON list via [ModuleStateStore] under the cross-platform key
 * [PrefKeys.IRIS_MEMORY] (same idiom as the other store-backed repositories). The remember/
 * recall/summarize rules live in the pure [IrisMemoryLogic] so they stay unit-testable.
 *
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 * PRIVACY INVARIANT: preferences feed IRIS's own system prompt and the inspect/forget UI only.
 * They are NEVER transmitted to external (non-Anthropic) APIs.
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class IrisMemory @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(IrisPreferenceCategoryAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<IrisPreference>>(
            Types.newParameterizedType(List::class.java, IrisPreference::class.java),
        )

    /** Persist or reinforce ([category], [value]): create at 0.7, +0.1 per repeat, cap 1.0. */
    suspend fun remember(category: IrisPreferenceCategory, value: String) {
        persist(
            IrisMemoryLogic.rememberInto(current(), category, value, nowIso = Instant.now().toString()),
        )
    }

    /** Stored preferences (optionally only [category]), sorted by confidence descending. */
    suspend fun recall(category: IrisPreferenceCategory? = null): List<IrisPreference> =
        IrisMemoryLogic.recallSorted(current(), category)

    /** Remove the preference with [id]; no-op when absent. */
    suspend fun delete(id: String) {
        persist(current().filterNot { it.id == id })
    }

    /** "Forget everything" — wipes the whole `iris_memory` store. */
    suspend fun forgetEverything() {
        store.remove(PrefKeys.IRIS_MEMORY)
    }

    /** Session-start prompt summary of stored preferences; `""` when nothing is remembered. */
    suspend fun summaryForPrompt(): String = IrisMemoryLogic.summaryFor(current())

    /** Live view for the inspect/forget UI; empty until something is remembered. */
    fun observe(): Flow<List<IrisPreference>> =
        store.observe(PrefKeys.IRIS_MEMORY).map { json -> json?.let(::parse).orEmpty() }

    private suspend fun current(): List<IrisPreference> =
        store.read(PrefKeys.IRIS_MEMORY)?.let(::parse).orEmpty()

    private suspend fun persist(preferences: List<IrisPreference>) {
        store.save(PrefKeys.IRIS_MEMORY, adapter.toJson(preferences))
    }

    private fun parse(json: String): List<IrisPreference>? =
        runCatching { adapter.fromJson(json) }.getOrNull()
}
