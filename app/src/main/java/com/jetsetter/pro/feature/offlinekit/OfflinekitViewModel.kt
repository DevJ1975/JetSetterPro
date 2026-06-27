package com.jetsetter.pro.feature.offlinekit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflinekitViewModel @Inject constructor(
    private val repository: OfflinekitRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(OfflinekitUiState(isLoading = true))
    val ui: StateFlow<OfflinekitUiState> = _ui.asStateFlow()

    // Persist each downloaded bundle's id together with its lastSynced stamp, as a JSON
    // Map<String, String> of id -> lastSynced, so the "Just now" stamp survives restart.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val downloadedAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java),
    )

    init {
        viewModelScope.launch {
            val bundles = repository.loadBundles()
            // First run (no saved state) keeps the seed defaults; otherwise the persisted map
            // is authoritative so toggling a bundle off also survives restart, and a downloaded
            // bundle restores the lastSynced we stamped rather than the stale seed value.
            val saved = readDownloaded()
            val restored = if (saved == null) {
                bundles
            } else {
                bundles.map { bundle ->
                    val syncedAt = saved[bundle.id]
                    if (syncedAt != null) {
                        bundle.copy(isDownloaded = true, lastSynced = syncedAt)
                    } else {
                        bundle.copy(isDownloaded = false)
                    }
                }
            }
            _ui.update {
                it.copy(
                    bundles = restored,
                    storageQuotaMb = repository.storageQuotaMb,
                    isLoading = false,
                )
            }
        }
    }

    /** Flip a single bundle between downloaded / not — local-only, mirrors a real download toggle. */
    fun toggleDownload(bundleId: String) {
        _ui.update { state ->
            state.copy(
                bundles = state.bundles.map { bundle ->
                    if (bundle.id != bundleId) {
                        bundle
                    } else {
                        val nowDownloaded = !bundle.isDownloaded
                        bundle.copy(
                            isDownloaded = nowDownloaded,
                            lastSynced = if (nowDownloaded) "Just now" else bundle.lastSynced,
                        )
                    }
                },
            )
        }
        persistDownloaded()
    }

    /** Mark every bundle downloaded in one tap; freshly cached ones get a "Just now" sync stamp. */
    fun downloadAll() {
        _ui.update { state ->
            state.copy(
                bundles = state.bundles.map { bundle ->
                    if (bundle.isDownloaded) {
                        bundle
                    } else {
                        bundle.copy(isDownloaded = true, lastSynced = "Just now")
                    }
                },
            )
        }
        persistDownloaded()
    }

    /** Free up the offline cache by removing every download in one tap; reclaims all used storage. */
    fun removeAll() {
        _ui.update { state ->
            state.copy(
                bundles = state.bundles.map { bundle ->
                    if (bundle.isDownloaded) bundle.copy(isDownloaded = false) else bundle
                },
            )
        }
        persistDownloaded()
    }

    /** Update the live search filter; transient UI state, not persisted across restarts. */
    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query) }
    }

    /** Save each downloaded bundle's id -> lastSynced so both the choice and stamp survive restart. */
    private fun persistDownloaded() {
        val downloaded = _ui.value.bundles
            .filter { it.isDownloaded }
            .associate { it.id to it.lastSynced }
        viewModelScope.launch {
            stateStore.save(DOWNLOADED_KEY, downloadedAdapter.toJson(downloaded))
        }
    }

    /** Read the persisted id -> lastSynced map, or null on first run (nothing saved yet). */
    private suspend fun readDownloaded(): Map<String, String>? {
        val json = stateStore.read(DOWNLOADED_KEY) ?: return null
        return runCatching { downloadedAdapter.fromJson(json) }.getOrNull()
    }

    private companion object {
        const val DOWNLOADED_KEY = "offlinekit_downloaded"
    }
}
