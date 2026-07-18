package com.jetsetter.pro.feature.travelwallet

import com.jetsetter.pro.core.backend.CloudBackend
import com.jetsetter.pro.core.backend.TravelWalletItemDoc
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.di.ApplicationScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Travel Wallet. Local-first: seeded from [seed], then any
 * tester-added passes (plus pin / delete edits) are persisted as a Moshi-serialized JSON list via
 * [ModuleStateStore] (key [KEY]) so they survive app restarts — the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`. Moshi serializes [TravelWalletPassType] to its name automatically.
 *
 * CLOUD WRITE-THROUGH (plan R8): every local mutation mirrors best-effort to
 * [CloudBackend.walletItems] on the app scope (the `TripRepository.upsert` idiom — the local write
 * returns immediately, a failed mirror is silently dropped), and on sign-in the remote passes are
 * merged by id into the local wallet (adopt remote-only, push local-only up — the
 * `TravelProfileStore` reconcile shape). The local store stays the source of truth throughout;
 * seed/demo passes never push to the cloud (plan R10e's seed-data guard). With the live project's
 * `wallet_items` table not yet created and anonymous auth off, every cloud path degrades to a
 * silent no-op.
 */
@Singleton
class TravelWalletRepository @Inject constructor(
    private val store: ModuleStateStore,
    private val backend: CloudBackend,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<TravelWalletItem>>(
            Types.newParameterizedType(List::class.java, TravelWalletItem::class.java),
        )

    init {
        // Sign-in reconcile: merge-by-id pull once per signed-in uid (distinctUntilChanged over
        // non-null uids — the TravelProfileStore idiom). A failed merge leaves local data alone.
        appScope.launch {
            backend.session
                .mapNotNull { it?.uid }
                .distinctUntilChanged()
                .collect { uid -> runCatching { mergeCloudPasses(uid) } }
        }
    }

    /** Live view of the wallet, falling back to the seed until something is persisted. */
    fun observePasses(): Flow<List<TravelWalletItem>> =
        store.observe(KEY).map { json -> json?.let(::parse) ?: seed() }

    /** Write the seed once so the first launch has something concrete to edit against. */
    suspend fun seedIfEmpty() {
        if (store.read(KEY) == null) persist(seed())
    }

    /** Pin / unpin a pass so it's easy to find at the gate. */
    suspend fun toggleFavorite(id: String) {
        val updated = current().map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        persist(updated)
        updated.firstOrNull { it.id == id }?.let(::pushPass)
    }

    /** Add a tester-built pass to the top of the wallet so it's immediately visible. */
    suspend fun addPass(item: TravelWalletItem) {
        persist(listOf(item) + current())
        pushPass(item)
    }

    /** Remove a pass entirely. */
    suspend fun removePass(id: String) {
        persist(current().filterNot { it.id == id })
        if (id in seedIds) return
        val uid = backend.currentSession()?.uid ?: return
        appScope.launch { backend.walletItems.delete(uid, id) }
    }

    // ── Cloud write-through (plan R8) ────────────────────────────────────────

    /**
     * Best-effort cloud mirror of one pass, fired on [appScope] so the local write returns
     * immediately. Seed/demo passes and signed-out sessions are silent no-ops; the sign-in merge
     * pushes local-only passes up later.
     */
    private fun pushPass(item: TravelWalletItem) {
        if (item.id in seedIds) return
        val uid = backend.currentSession()?.uid ?: return
        appScope.launch { backend.walletItems.upsert(uid, item.toWalletDoc()) }
    }

    /**
     * Non-destructive merge-by-id against the cloud: adopt remote-only passes into the local
     * wallet (appended below local ones — local order wins) and push local-only, non-seed passes
     * up. Remote docs with unknown pass types are skipped (a newer client's data is preserved
     * remotely, just not rendered here). Local passes are never overwritten or deleted by this.
     */
    private suspend fun mergeCloudPasses(uid: String) {
        val remote = runCatching { backend.walletItems.getOnce(uid) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toWalletItemOrNull() }
        val local = current()
        val localIds = local.mapTo(HashSet()) { it.id }
        val remoteOnly = remote.filter { it.id !in localIds }
        if (remoteOnly.isNotEmpty()) persist(local + remoteOnly)
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        local.filter { it.id !in remoteIds && it.id !in seedIds }
            .forEach { backend.walletItems.upsert(uid, it.toWalletDoc()) }
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

    /** Ids of the demo passes — derived from [seed] so the guard can never drift from it. */
    private val seedIds: Set<String> by lazy { seed().mapTo(HashSet()) { it.id } }

    private fun seed(): List<TravelWalletItem> = listOf(
        TravelWalletItem(
            id = "wallet-boarding",
            type = TravelWalletPassType.BOARDING_PASS,
            title = "DL 1423 · LAS → ATL",
            // Matches the Check-In module's DL 1423 (First cabin, seat 3A, Zone 1) so the wallet
            // pass and the issued boarding pass never disagree during a demo.
            subtitle = "Delta Air Lines · First",
            keyDetailLabel = "Seat",
            keyDetailValue = "3A · Zone 1",
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

// ── Cloud doc mapping (plan R8) ──────────────────────────────────────────────

/** Pass → backend-neutral doc; the enum travels as its constant name (CloudModels contract). */
internal fun TravelWalletItem.toWalletDoc() = TravelWalletItemDoc(
    id = id,
    type = type.name,
    title = title,
    subtitle = subtitle,
    keyDetailLabel = keyDetailLabel,
    keyDetailValue = keyDetailValue,
    date = date,
    isFavorite = isFavorite,
)

/** Doc → pass, or null for a pass type this client doesn't know (skipped by the merge). */
internal fun TravelWalletItemDoc.toWalletItemOrNull(): TravelWalletItem? {
    val passType = runCatching { TravelWalletPassType.valueOf(type) }.getOrNull() ?: return null
    return TravelWalletItem(
        id = id,
        type = passType,
        title = title,
        subtitle = subtitle,
        keyDetailLabel = keyDetailLabel,
        keyDetailValue = keyDetailValue,
        date = date,
        isFavorite = isFavorite,
    )
}
