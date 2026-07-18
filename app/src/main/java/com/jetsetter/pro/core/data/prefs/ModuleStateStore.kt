package com.jetsetter.pro.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.moduleStateDataStore by preferencesDataStore(name = "module_state")

/**
 * Generic per-feature persistence so tester-entered state survives app restarts. Each feature
 * persists its mutable state as a JSON string under a unique [key] (serialize with Moshi). This
 * keeps the shared Room DB free of throwaway mock entities while still feeling real in beta.
 *
 * Example:
 * ```
 * private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
 *     .adapter<List<Foo>>(Types.newParameterizedType(List::class.java, Foo::class.java))
 * // load:  adapter.fromJson(store.read("journal_entries") ?: "")
 * // save:  store.save("journal_entries", adapter.toJson(entries))
 * ```
 */
@Singleton
class ModuleStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun save(key: String, json: String) {
        context.moduleStateDataStore.edit { it[stringPreferencesKey(key)] = json }
    }

    fun observe(key: String): Flow<String?> =
        context.moduleStateDataStore.data.map { it[stringPreferencesKey(key)] }

    suspend fun read(key: String): String? =
        context.moduleStateDataStore.data.first()[stringPreferencesKey(key)]

    suspend fun remove(key: String) {
        context.moduleStateDataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    /**
     * Wipes every key — the account-deletion path
     * ([com.jetsetter.pro.core.backend.CloudBackend.deleteAccount]). Also clears seed/consent
     * flags, so the app returns to fresh-install behavior on next launch.
     */
    suspend fun clearAll() {
        context.moduleStateDataStore.edit { it.clear() }
    }
}
