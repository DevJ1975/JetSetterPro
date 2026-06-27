package com.jetsetter.pro.feature.loyaltyvault

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Loyalty Vault. Mock-first: seeded from [seed], then any
 * tester edits (added programs, pins, deletions) are persisted as a Moshi-serialized JSON list via
 * [ModuleStateStore] (key [KEY]) so they survive app restarts — the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`. Moshi serializes the enum [LoyaltyVaultProgramType] to its name
 * automatically. No network.
 */
@Singleton
class LoyaltyvaultRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<LoyaltyVaultAccount>>(
            Types.newParameterizedType(List::class.java, LoyaltyVaultAccount::class.java),
        )

    /** Live view of the vault, falling back to the seed until something is persisted. */
    fun observeAccounts(): Flow<List<LoyaltyVaultAccount>> =
        store.observe(KEY).map { json -> json?.let(::parse) ?: seed }

    /** Write the seed once so the first launch has concrete data to edit against. */
    suspend fun seedIfEmpty() {
        if (store.read(KEY) == null) persist(seed)
    }

    /** Add a tester-built program to the top of the vault so it's immediately visible. */
    suspend fun addAccount(account: LoyaltyVaultAccount) {
        persist(listOf(account) + current())
    }

    /** Remove a program entirely. */
    suspend fun removeAccount(id: String) {
        persist(current().filterNot { it.id == id })
    }

    /** Pin / unpin a program so it floats to the top of the list. */
    suspend fun togglePin(id: String) {
        persist(current().map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it })
    }

    private suspend fun current(): List<LoyaltyVaultAccount> {
        val json = store.read(KEY) ?: return seed
        return parse(json) ?: seed
    }

    private suspend fun persist(accounts: List<LoyaltyVaultAccount>) {
        store.save(KEY, adapter.toJson(accounts))
    }

    private fun parse(json: String): List<LoyaltyVaultAccount>? =
        runCatching { adapter.fromJson(json) }.getOrNull()

    private val seed: List<LoyaltyVaultAccount> = listOf(
        LoyaltyVaultAccount(
            id = "delta",
            programName = "Delta SkyMiles",
            membershipNumber = "9087654321",
            tier = "Platinum Medallion",
            balance = 248_530,
            unitLabel = "miles",
            type = LoyaltyVaultProgramType.AIRLINE,
            centsPerUnit = 1.2,
            nextTier = "Diamond Medallion",
            tierProgress = 14_200,
            tierTarget = 28_000,
            tierUnit = "MQDs",
            isPinned = true,
        ),
        LoyaltyVaultAccount(
            id = "united",
            programName = "United MileagePlus",
            membershipNumber = "ML4471203",
            tier = "Premier Gold",
            balance = 96_210,
            unitLabel = "miles",
            type = LoyaltyVaultProgramType.AIRLINE,
            centsPerUnit = 1.3,
            nextTier = "Premier Platinum",
            tierProgress = 5_400,
            tierTarget = 8_000,
            tierUnit = "PQPs",
        ),
        LoyaltyVaultAccount(
            id = "aa",
            programName = "American AAdvantage",
            membershipNumber = "AA7782219",
            tier = "Platinum Pro",
            balance = 142_870,
            unitLabel = "miles",
            type = LoyaltyVaultProgramType.AIRLINE,
            centsPerUnit = 1.4,
            nextTier = "Executive Platinum",
            tierProgress = 86_500,
            tierTarget = 200_000,
            tierUnit = "Loyalty Points",
        ),
        LoyaltyVaultAccount(
            id = "marriott",
            programName = "Marriott Bonvoy",
            membershipNumber = "BV559013344",
            tier = "Titanium Elite",
            balance = 318_940,
            unitLabel = "points",
            type = LoyaltyVaultProgramType.HOTEL,
            centsPerUnit = 0.84,
            nextTier = "Ambassador Elite",
            tierProgress = 62,
            tierTarget = 100,
            tierUnit = "elite nights",
        ),
        LoyaltyVaultAccount(
            id = "hilton",
            programName = "Hilton Honors",
            membershipNumber = "HH88123490",
            tier = "Diamond",
            balance = 205_600,
            unitLabel = "points",
            type = LoyaltyVaultProgramType.HOTEL,
            centsPerUnit = 0.5,
            nextTier = null,
        ),
        LoyaltyVaultAccount(
            id = "hyatt",
            programName = "World of Hyatt",
            membershipNumber = "WH3344551",
            tier = "Globalist",
            balance = 87_450,
            unitLabel = "points",
            type = LoyaltyVaultProgramType.HOTEL,
            centsPerUnit = 1.7,
            nextTier = null,
        ),
    )

    private companion object {
        const val KEY = "loyalty_vault_accounts"
    }
}
