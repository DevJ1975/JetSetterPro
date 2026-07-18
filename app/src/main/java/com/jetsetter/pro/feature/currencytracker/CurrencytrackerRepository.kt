package com.jetsetter.pro.feature.currencytracker

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.remote.ApiResult
import com.jetsetter.pro.core.data.remote.fx.FrankfurterService
import com.jetsetter.pro.core.data.remote.fx.FxRates
import com.jetsetter.pro.core.di.ApplicationScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing store for the Currency feature. Rates now come from a three-level fallback chain
 * (plan B7 — keyless live FX via frankfurter.dev, ECB provenance):
 *
 *  1. **Cached live rates** — the last successful Frankfurter fetch, persisted as JSON under
 *     [CurrencytrackerLiveFx.CACHE_KEY] in [ModuleStateStore] alongside its fetch timestamp.
 *     Served whenever present (fresh *or* stale — last-good beats bundled), merged over the
 *     static table so names/flags/symbols stay curated.
 *  2. **Best-effort refresh** — when the cache is missing or older than ~12 h
 *     ([CurrencytrackerLiveFx.isStale]), a background fetch is launched on the app scope; the
 *     current call still returns immediately with whatever is available. Only currency codes go
 *     over the wire (privacy contract R10f).
 *  3. **Static table** — [CurrencytrackerFxData] bundled approximations, served until the first
 *     successful fetch or when the network never succeeds.
 *
 * The 24-hour change column stays the static illustrative figure even when rates are live —
 * Frankfurter's `latest` endpoint carries no previous-day delta (a historical fetch is a future
 * follow-up). [lastUpdated] reflects the live fetch time when live, else the bundled caption.
 *
 * The tester-mutable converter state (amount + From/To pair) is persisted as a Moshi-serialized
 * JSON blob via [ModuleStateStore] (key [KEY]) so the converter reopens exactly as it was left.
 * The public API is unchanged from the offline build — the screen and ViewModel compile untouched.
 */
@Singleton
class CurrencytrackerRepository @Inject constructor(
    private val store: ModuleStateStore,
    private val fxService: FrankfurterService,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /** Reference currency every rate is anchored to (ratePerUsd == 1.0). */
    val referenceCurrency: String = CurrencytrackerFxData.REFERENCE_CURRENCY

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val prefsAdapter = moshi.adapter(ConverterPrefs::class.java)
    private val cacheAdapter = moshi.adapter(CachedLiveRates::class.java)

    /** In-memory mirror of the persisted live cache so non-suspend [rateFor] can consult it. */
    @Volatile
    private var memoryCache: CachedLiveRates? = null

    /** Collapses concurrent refresh triggers into one in-flight fetch. */
    private val refreshInFlight = AtomicBoolean(false)

    /**
     * Snapshot of the current USD-anchored rates shown in the rate list and converter: cached
     * live rates merged over the bundled table when available, else the bundled table. A missing
     * or ~12 h-stale cache also kicks off a best-effort background refresh (this call never
     * blocks on the network — the refreshed rates appear on the next load).
     */
    suspend fun currencies(): List<CurrencyRate> {
        val cached = cachedLiveRates()
        if (cached == null || CurrencytrackerLiveFx.isStale(cached.fetchedAtMillis, System.currentTimeMillis())) {
            refreshBestEffort()
        }
        return CurrencytrackerLiveFx.merge(CurrencytrackerFxData.table, cached)
    }

    /**
     * Look up a single currency by ISO code — live-rate-adjusted when a fetched rate for it is in
     * memory, else the bundled figure, with a USD-parity default for anything unknown so a
     * conversion is always computable.
     */
    fun rateFor(code: String): CurrencyRate {
        val static = CurrencytrackerFxData.rate(code)
        val live = memoryCache
            ?.takeIf { it.base == referenceCurrency }
            ?.rates?.get(static.code)
        return if (live != null && live.isFinite() && live > 0.0) static.copy(ratePerUsd = live) else static
    }

    /**
     * Freshness caption for the rate list: the live fetch time (with ECB attribution) once a
     * fetch has succeeded, else the bundled-snapshot caption.
     */
    suspend fun lastUpdated(): String {
        val cached = cachedLiveRates() ?: return CurrencytrackerFxData.SNAPSHOT_CAPTION
        return CurrencytrackerLiveFx.liveCaption(cached.fetchedAtMillis)
    }

    /** Tester's last converter state, or null on first launch (so the ViewModel falls back to defaults). */
    suspend fun loadPrefs(): ConverterPrefs? =
        store.read(KEY)?.let { json -> runCatching { prefsAdapter.fromJson(json) }.getOrNull() }

    /** Persist the converter state so it survives an app restart. */
    suspend fun savePrefs(prefs: ConverterPrefs) {
        store.save(KEY, prefsAdapter.toJson(prefs))
    }

    // ── Live-rate plumbing ───────────────────────────────────────────────────

    /** The persisted live cache (memory-first), or null when no fetch has ever succeeded. */
    private suspend fun cachedLiveRates(): CachedLiveRates? {
        memoryCache?.let { return it }
        val parsed = store.read(CurrencytrackerLiveFx.CACHE_KEY)
            ?.let { json -> runCatching { cacheAdapter.fromJson(json) }.getOrNull() }
        if (parsed != null) memoryCache = parsed
        return parsed
    }

    /**
     * Fire-and-forget fetch on the application scope. Failures are swallowed — the last-good
     * cache (or the static table) keeps serving, and the next screen open retries.
     */
    private fun refreshBestEffort() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        appScope.launch {
            try {
                when (val result = fxService.latest(base = referenceCurrency)) {
                    is ApiResult.Success -> saveLive(result.data)
                    is ApiResult.Failure -> Unit // keep last-good / static
                }
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    private suspend fun saveLive(fx: FxRates) {
        val cached = CachedLiveRates(
            base = fx.base,
            date = fx.date,
            rates = fx.rates,
            fetchedAtMillis = System.currentTimeMillis(),
        )
        memoryCache = cached
        store.save(CurrencytrackerLiveFx.CACHE_KEY, cacheAdapter.toJson(cached))
    }

    private companion object {
        const val KEY = "currency_converter_prefs"
    }
}

/**
 * The persisted shape under [CurrencytrackerLiveFx.CACHE_KEY]: the last-good Frankfurter snapshot
 * ([base]-anchored [rates] as of ECB reference [date]) plus the wall-clock [fetchedAtMillis] the
 * fetch succeeded — the staleness clock and the "last updated" caption source.
 */
internal data class CachedLiveRates(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
    val fetchedAtMillis: Long,
)

/**
 * Pure live-FX policy — staleness, merge/fallback ordering, caption — extracted from the
 * repository so FrankfurterMappingTest can pin it without Android or network machinery.
 */
internal object CurrencytrackerLiveFx {

    /** ModuleStateStore key holding the [CachedLiveRates] JSON blob. */
    const val CACHE_KEY = "currency_live_rates"

    /** Refresh when the cache is older than ~12 h. */
    const val STALE_AFTER_MILLIS: Long = 12L * 60L * 60L * 1000L

    /**
     * True when a fetch made at [fetchedAtMillis] should be refreshed as of [nowMillis]: at or
     * past the 12 h TTL, or timestamped in the future (clock rolled back — refetch rather than
     * trusting a timestamp that could otherwise stay "fresh" for years).
     */
    fun isStale(fetchedAtMillis: Long, nowMillis: Long): Boolean {
        val age = nowMillis - fetchedAtMillis
        return age < 0L || age >= STALE_AFTER_MILLIS
    }

    /**
     * Fallback ordering, pure part: live rates override the bundled [staticTable] per-code; no
     * (usable) live data → the bundled table unchanged. The static row supplies everything but
     * the rate (name/flag/symbol/dailyChangePct), codes absent from [live] keep their bundled
     * rate, live codes outside the curated roster are ignored, and a snapshot anchored to a
     * different base than the app's USD reference is rejected wholesale (a cross-base merge
     * would silently corrupt every conversion).
     */
    fun merge(staticTable: List<CurrencyRate>, live: CachedLiveRates?): List<CurrencyRate> {
        if (live == null || live.base != CurrencytrackerFxData.REFERENCE_CURRENCY || live.rates.isEmpty()) {
            return staticTable
        }
        return staticTable.map { row ->
            val liveRate = live.rates[row.code]
            if (liveRate != null && liveRate.isFinite() && liveRate > 0.0) {
                row.copy(ratePerUsd = liveRate)
            } else {
                row
            }
        }
    }

    /** Caption for live rates, e.g. "Live ECB rates · updated Jul 17, 2026, 3:04 PM". */
    fun liveCaption(fetchedAtMillis: Long): String {
        val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(fetchedAtMillis))
        return "Live ECB rates · updated $stamp"
    }
}
