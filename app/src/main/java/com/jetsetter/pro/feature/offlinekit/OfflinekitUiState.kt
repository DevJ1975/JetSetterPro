package com.jetsetter.pro.feature.offlinekit

/**
 * Single immutable UI-state object for the Offline Kit screen. Only the raw inputs are stored;
 * every total / fraction below is a *derived* pure function of those inputs, so the storage
 * readout can never drift out of sync with which bundles are actually downloaded.
 */
data class OfflinekitUiState(
    val bundles: List<OfflineKitBundle> = emptyList(),
    val storageQuotaMb: Double = 2048.0,
    /** Live search text; matches a bundle's trip name or destination. Transient (not persisted). */
    val query: String = "",
    val isLoading: Boolean = false,
) {
    /** MB occupied on-device by the bundles currently marked downloaded. */
    val storageUsedMb: Double get() = bundles.filter { it.isDownloaded }.sumOf { it.sizeMb }

    /** MB still free within the reserved offline budget. */
    val storageFreeMb: Double get() = (storageQuotaMb - storageUsedMb).coerceAtLeast(0.0)

    /** How full the offline cache is, clamped to a 0f..1f bar fraction. */
    val storageFraction: Float
        get() = if (storageQuotaMb <= 0.0) 0f else (storageUsedMb / storageQuotaMb).toFloat().coerceIn(0f, 1f)

    /** Fraction the bar would reach if every bundle were downloaded — drawn as a faint preview. */
    val projectedFraction: Float
        get() = if (storageQuotaMb <= 0.0) 0f
        else (bundles.sumOf { it.sizeMb } / storageQuotaMb).toFloat().coerceIn(0f, 1f)

    val downloadedCount: Int get() = bundles.count { it.isDownloaded }

    /** Bundles not yet cached, and the MB that downloading them all would add. */
    val pendingCount: Int get() = bundles.count { !it.isDownloaded }
    val pendingDownloadMb: Double get() = bundles.filterNot { it.isDownloaded }.sumOf { it.sizeMb }

    val allDownloaded: Boolean get() = bundles.isNotEmpty() && bundles.all { it.isDownloaded }

    val hasQuery: Boolean get() = query.isNotBlank()

    /** Bundles matching the current search query (by trip name or destination). */
    val visibleBundles: List<OfflineKitBundle>
        get() = if (!hasQuery) {
            bundles
        } else {
            val q = query.trim()
            bundles.filter {
                it.tripName.contains(q, ignoreCase = true) || it.destination.contains(q, ignoreCase = true)
            }
        }
}
