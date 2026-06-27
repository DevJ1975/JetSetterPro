package com.jetsetter.pro.feature.travelwallet

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Travel Wallet. Mock-first: seeded from [seed], then any
 * tester-added passes (plus pin / delete edits) are persisted as a Moshi-serialized JSON list via
 * [ModuleStateStore] (key [KEY]) so they survive app restarts — the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`. Moshi serializes [TravelWalletPassType] to its name automatically.
 */
@Singleton
class TravelWalletRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<TravelWalletItem>>(
            Types.newParameterizedType(List::class.java, TravelWalletItem::class.java),
        )

    /** Live view of the wallet, falling back to the seed until something is persisted. */
    fun observePasses(): Flow<List<TravelWalletItem>> =
        store.observe(KEY).map { json -> json?.let(::parse) ?: seed() }

    /** Write the seed once so the first launch has something concrete to edit against. */
    suspend fun seedIfEmpty() {
        if (store.read(KEY) == null) persist(seed())
    }

    /** Pin / unpin a pass so it's easy to find at the gate. */
    suspend fun toggleFavorite(id: String) {
        persist(current().map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it })
    }

    /** Add a tester-built pass to the top of the wallet so it's immediately visible. */
    suspend fun addPass(item: TravelWalletItem) {
        persist(listOf(item) + current())
    }

    /** Remove a pass entirely. */
    suspend fun removePass(id: String) {
        persist(current().filterNot { it.id == id })
    }

    private suspend fun current(): List<TravelWalletItem> {
        val json = store.read(KEY) ?: return seed()
        return parse(json) ?: seed()
    }

    private suspend fun persist(passes: List<TravelWalletItem>) {
        store.save(KEY, adapter.toJson(passes))
    }

    private fun parse(json: String): List<TravelWalletItem>? =
        runCatching { adapter.fromJson(json) }.getOrNull()

    private fun seed(): List<TravelWalletItem> = listOf(
        TravelWalletItem(
            id = "wallet-boarding",
            type = TravelWalletPassType.BOARDING_PASS,
            title = "DL 1423 · LAS → ATL",
            subtitle = "Delta Air Lines · Main Cabin",
            keyDetailLabel = "Seat",
            keyDetailValue = "12A · Zone 1",
            date = "Jul 14, 2026 · 7:00 AM",
            isFavorite = true,
        ),
        TravelWalletItem(
            id = "wallet-hotel",
            type = TravelWalletPassType.HOTEL,
            title = "Four Seasons Atlanta",
            subtitle = "75 14th St NE · King Suite",
            keyDetailLabel = "Confirmation",
            keyDetailValue = "FS-8842193",
            date = "Check-in Jul 14 · Check-out Jul 16",
        ),
        TravelWalletItem(
            id = "wallet-car",
            type = TravelWalletPassType.RENTAL_CAR,
            title = "Hertz · Tesla Model 3",
            subtitle = "ATL Airport · Counter B",
            keyDetailLabel = "Reservation",
            keyDetailValue = "H7-2290114",
            date = "Pickup Jul 14 · 9:30 AM",
        ),
        TravelWalletItem(
            id = "wallet-event",
            type = TravelWalletPassType.EVENT_TICKET,
            title = "Q3 Leadership Summit",
            subtitle = "Mercedes-Benz Stadium · Sec 114",
            keyDetailLabel = "Ticket",
            keyDetailValue = "Row 7 · Seat 18",
            date = "Jul 15, 2026 · 6:00 PM",
        ),
        TravelWalletItem(
            id = "wallet-insurance",
            type = TravelWalletPassType.INSURANCE,
            title = "AIG Travel Guard",
            subtitle = "Trip protection · Up to \$50,000",
            keyDetailLabel = "Policy",
            keyDetailValue = "TG-4471209",
            date = "Valid Jul 14 – Jul 16, 2026",
        ),
    )

    private companion object {
        const val KEY = "travel_wallet_passes"
    }
}
