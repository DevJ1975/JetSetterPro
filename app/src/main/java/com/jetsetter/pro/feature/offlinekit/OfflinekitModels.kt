package com.jetsetter.pro.feature.offlinekit

/**
 * A per-trip offline data bundle — everything the traveler needs cached on-device so the app
 * keeps working with no signal (boarding passes, maps, itinerary, docs). Module-prefixed name
 * to avoid colliding with other features' models.
 */
data class OfflineKitBundle(
    val id: String,
    val tripName: String,
    val destination: String,
    val sizeMb: Double,
    val lastSynced: String,
    val contents: List<String>,
    val isDownloaded: Boolean = false,
)
