package com.jetsetter.pro.core.data.lovedones

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.PrefKeys
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Loved Ones contacts (spec §3.3). Persisted as a Moshi-serialized
 * JSON list via [ModuleStateStore] under the cross-platform key [PrefKeys.LOVED_ONES] — the same
 * idiom as the other ModuleStateStore-backed repositories (e.g. the Loyalty Vault). Lives in
 * `core/data` (not a feature package) because both the management screen and IRIS's
 * `flightActions(notifyLovedOnes)` tool consume it (plan R5). No seed — the list starts empty
 * until the user adds someone.
 */
@Singleton
class LovedOnesRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<LovedOne>>(
            Types.newParameterizedType(List::class.java, LovedOne::class.java),
        )

    /** Live view of the contact list; empty until something is persisted. */
    fun observe(): Flow<List<LovedOne>> =
        store.observe(PrefKeys.LOVED_ONES).map { json -> json?.let(::parse).orEmpty() }

    /** Add [one], or replace the entry with the same id in place (preserving list order). */
    suspend fun upsert(one: LovedOne) {
        val existing = current()
        val updated = if (existing.any { it.id == one.id }) {
            existing.map { if (it.id == one.id) one else it }
        } else {
            existing + one
        }
        persist(updated)
    }

    /** Remove the contact with [id]; no-op when absent. */
    suspend fun delete(id: String) {
        persist(current().filterNot { it.id == id })
    }

    private suspend fun current(): List<LovedOne> =
        store.read(PrefKeys.LOVED_ONES)?.let(::parse).orEmpty()

    private suspend fun persist(ones: List<LovedOne>) {
        store.save(PrefKeys.LOVED_ONES, adapter.toJson(ones))
    }

    private fun parse(json: String): List<LovedOne>? =
        runCatching { adapter.fromJson(json) }.getOrNull()
}
