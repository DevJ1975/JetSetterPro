package com.jetsetter.pro.feature.currencytracker

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock backing for the Currency feature. Mirrors the shape of a real FX-rate API
 * (a snapshot of USD-anchored rates plus a fetched-at timestamp) without any network — the live
 * integration (e.g. Open Exchange Rates / ECB) is documented in the feature parity guide.
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
    val referenceCurrency: String = "USD"

    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ConverterPrefs::class.java)

    private val snapshot: List<CurrencyRate> = listOf(
        CurrencyRate(code = "USD", name = "US Dollar", flag = "🇺🇸", symbol = "$", ratePerUsd = 1.0, dailyChangePct = 0.0),
        CurrencyRate(code = "EUR", name = "Euro", flag = "🇪🇺", symbol = "€", ratePerUsd = 0.9214, dailyChangePct = -0.12),
        CurrencyRate(code = "GBP", name = "British Pound", flag = "🇬🇧", symbol = "£", ratePerUsd = 0.7843, dailyChangePct = 0.08),
        CurrencyRate(code = "JPY", name = "Japanese Yen", flag = "🇯🇵", symbol = "¥", ratePerUsd = 157.32, dailyChangePct = 0.45),
        CurrencyRate(code = "CHF", name = "Swiss Franc", flag = "🇨🇭", symbol = "Fr", ratePerUsd = 0.8956, dailyChangePct = -0.18),
        CurrencyRate(code = "CAD", name = "Canadian Dollar", flag = "🇨🇦", symbol = "C$", ratePerUsd = 1.3712, dailyChangePct = -0.21),
        CurrencyRate(code = "AUD", name = "Australian Dollar", flag = "🇦🇺", symbol = "A$", ratePerUsd = 1.5089, dailyChangePct = 0.33),
        CurrencyRate(code = "MXN", name = "Mexican Peso", flag = "🇲🇽", symbol = "Mex$", ratePerUsd = 17.84, dailyChangePct = -0.05),
        CurrencyRate(code = "SGD", name = "Singapore Dollar", flag = "🇸🇬", symbol = "S$", ratePerUsd = 1.3489, dailyChangePct = 0.11),
    )

    /** Snapshot of the current USD-anchored rates shown in the rate list and converter. */
    suspend fun currencies(): List<CurrencyRate> = snapshot

    /** When the mock snapshot was "fetched" — rendered as a freshness caption. */
    suspend fun lastUpdated(): String = "Updated Jun 26, 2026 · 09:30"

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
