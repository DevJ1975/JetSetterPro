package com.jetsetter.pro.feature.identityvault

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Identity Vault. Mock-first: seeded from [seed], then any tester
 * edits (added or deleted credentials) are persisted as a Moshi-serialized JSON list via
 * [ModuleStateStore] (key [KEY]) so they survive app restarts — the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`. Moshi serializes the enum [IdentityVaultItemType] to its name
 * automatically; expiry is stored as an ISO string so no custom date adapter is needed. No network.
 */
@Singleton
class IdentityvaultRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<IdentityVaultItem>>(
            Types.newParameterizedType(List::class.java, IdentityVaultItem::class.java),
        )

    /** Live view of the vault, falling back to the seed until something is persisted. */
    fun observeItems(): Flow<List<IdentityVaultItem>> =
        store.observe(KEY).map { json -> json?.let(::parse) ?: seed }

    /** Write the seed once so the first launch has concrete data to edit against. */
    suspend fun seedIfEmpty() {
        if (store.read(KEY) == null) persist(seed)
    }

    /** Add a tester-entered credential to the top of the vault so it's immediately visible. */
    suspend fun addItem(item: IdentityVaultItem) {
        persist(listOf(item) + current())
    }

    /** Remove a credential entirely. */
    suspend fun removeItem(id: String) {
        persist(current().filterNot { it.id == id })
    }

    private suspend fun current(): List<IdentityVaultItem> {
        val json = store.read(KEY) ?: return seed
        return parse(json) ?: seed
    }

    private suspend fun persist(items: List<IdentityVaultItem>) {
        store.save(KEY, adapter.toJson(items))
    }

    private fun parse(json: String): List<IdentityVaultItem>? =
        runCatching { adapter.fromJson(json) }.getOrNull()

    // Seeded relative to "today" so the demo statuses stay sensible: one passport inside the
    // six-month renewal window, one Global Entry expiring soon, and one expired license.
    private val seed: List<IdentityVaultItem> = run {
        val today = LocalDate.now()
        listOf(
            IdentityVaultItem(
                id = "passport",
                type = IdentityVaultItemType.PASSPORT,
                label = "Passport Number",
                value = "X1234567",
                holderName = "Jamil Jones",
                issuer = "United States",
                expiry = today.plusMonths(5).toString(),
            ),
            IdentityVaultItem(
                id = "ktn",
                type = IdentityVaultItemType.KNOWN_TRAVELER,
                label = "Known Traveler Number",
                value = "98765432",
                holderName = "Jamil Jones",
                issuer = "TSA PreCheck",
                expiry = today.plusYears(3).toString(),
            ),
            IdentityVaultItem(
                id = "global-entry",
                type = IdentityVaultItemType.GLOBAL_ENTRY,
                label = "Global Entry / PASSID",
                value = "150123456789",
                holderName = "Jamil Jones",
                issuer = "U.S. Customs & Border Protection",
                expiry = today.plusDays(58).toString(),
            ),
            IdentityVaultItem(
                id = "drivers-license",
                type = IdentityVaultItemType.DRIVERS_LICENSE,
                label = "Driver's License",
                value = "D0451 6678 0921",
                holderName = "Jamil Jones",
                issuer = "Nevada DMV",
                expiry = today.minusDays(21).toString(),
            ),
        )
    }

    private companion object {
        const val KEY = "identity_vault_items"
    }
}
