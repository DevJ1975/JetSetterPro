package com.jetsetter.pro.feature.offlinekit

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory source for offline data bundles. Mock-first: no network, Room, or DataStore — just
 * realistic sample data so a beta tester can exercise the screen. A real build would back this
 * with on-device caching + a sync service.
 */
@Singleton
class OfflinekitRepository @Inject constructor() {

    /** Total on-device budget reserved for offline travel data, in MB. */
    val storageQuotaMb: Double = 2048.0

    suspend fun loadBundles(): List<OfflineKitBundle> = listOf(
        OfflineKitBundle(
            id = "atl-board",
            tripName = "Atlanta Board Meeting",
            destination = "Atlanta, GA",
            sizeMb = 142.6,
            lastSynced = "Today, 8:04 AM",
            contents = listOf("Itinerary", "Boarding passes", "Hotel docs", "Offline map"),
            isDownloaded = true,
        ),
        OfflineKitBundle(
            id = "tyo-summit",
            tripName = "Tokyo Product Summit",
            destination = "Tokyo, JP",
            sizeMb = 486.3,
            lastSynced = "Yesterday, 6:12 PM",
            contents = listOf("Itinerary", "Metro map", "Phrasebook", "Offline map", "Receipts"),
            isDownloaded = false,
        ),
        OfflineKitBundle(
            id = "lis-retreat",
            tripName = "Lisbon Team Retreat",
            destination = "Lisbon, PT",
            sizeMb = 268.9,
            lastSynced = "Jun 21, 9:30 AM",
            contents = listOf("Itinerary", "Offline map", "Restaurant list"),
            isDownloaded = false,
        ),
    )
}
