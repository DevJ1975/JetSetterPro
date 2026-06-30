package com.jetsetter.pro.feature.currencytracker

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing store for the Currency feature, sourced from a bundled **offline** FX table
 * ([CurrencytrackerFxData]) rather than a network call. The table is a static, in-code snapshot of
 * realistic *recent approximate* USD-anchored rates covering the major travel currencies; any
 * base→target conversion is derived from those USD anchors as a cross rate so every pair stays
 * self-consistent. There is no API key — live FX (e.g. Open Exchange Rates / ECB) is a future
 * drop-in, gated behind [CurrencytrackerFxData.API_FX_KEY] (currently unset).
 *
 * The tester-mutable converter state (amount + From/To pair) is persisted as a Moshi-serialized
 * JSON blob via [ModuleStateStore] (key [KEY]) so the converter reopens exactly as it was left —
 * the same Moshi idiom Room uses in `core/data/local/Converters.kt`. No network.
 */
@Singleton
class CurrencytrackerRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    /** Reference currency every rate is anchored to (ratePerUsd == 1.0). */
    val referenceCurrency: String = CurrencytrackerFxData.REFERENCE_CURRENCY

    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ConverterPrefs::class.java)

    /** Snapshot of the current USD-anchored rates shown in the rate list and converter. */
    suspend fun currencies(): List<CurrencyRate> = CurrencytrackerFxData.table

    /**
     * Look up a single currency by ISO code from the bundled table, with a USD-parity default for
     * anything not present — so a conversion is always computable.
     */
    fun rateFor(code: String): CurrencyRate = CurrencytrackerFxData.rate(code)

    /**
     * Freshness caption for the rate list. These are bundled/offline approximations — not a live
     * fetch — so the caption says so. When [CurrencytrackerFxData.API_FX_KEY] is wired to a real
     * key this becomes the actual fetch timestamp.
     */
    suspend fun lastUpdated(): String = CurrencytrackerFxData.SNAPSHOT_CAPTION

    /** Tester's last converter state, or null on first launch (so the ViewModel falls back to defaults). */
    suspend fun loadPrefs(): ConverterPrefs? =
        store.read(KEY)?.let { json -> runCatching { prefsAdapter.fromJson(json) }.getOrNull() }

    /** Persist the converter state so it survives an app restart. */
    suspend fun savePrefs(prefs: ConverterPrefs) {
        store.save(KEY, prefsAdapter.toJson(prefs))
    }

    private companion object {
        const val KEY = "currency_converter_prefs"
    }
}
