package com.jetsetter.pro.feature.currencytracker

/**
 * Bundled, OFFLINE foreign-exchange reference table — the real data backing the Currency feature.
 *
 * Every rate is a static, in-code USD-anchored figure ([CurrencyRate.ratePerUsd] == how many units
 * of the currency one US dollar buys, so USD itself is exactly `1.0`). These are realistic *recent
 * approximate* values, NOT a live quote: there is no network call and no API key. Converting between
 * any two currencies is done by going through USD as a cross rate (`target / base`), which the
 * UI-state already derives, so any pair stays self-consistent.
 *
 * Live FX is a future drop-in: when [API_FX_KEY] is wired to a real key (e.g. Open Exchange Rates /
 * ECB), the repository can swap [table] for a fetched snapshot without any UI change. It is
 * intentionally **unset** (`null`) in this offline build.
 */
internal object CurrencytrackerFxData {

    /** Reference currency every rate is anchored to (ratePerUsd == 1.0). */
    const val REFERENCE_CURRENCY: String = "USD"

    /**
     * Live-FX API key placeholder. **Unset** in this offline build — the rates below are bundled
     * approximations. Drop a real key here (and add the fetch call in the repository) to go live.
     */
    val API_FX_KEY: String? = null

    /** Short caption made visible in the UI so testers know these are offline/approximate figures. */
    const val APPROXIMATE_LABEL: String = "Offline · approx."

    /** Freshness caption rendered next to the rate list — emphasises the bundled/offline nature. */
    const val SNAPSHOT_CAPTION: String = "Bundled rates · approximate"

    /**
     * The bundled USD-base FX table covering the major travel currencies. Rates are recent
     * approximate values (mid-market, rounded) and are deliberately offline. `dailyChangePct` is an
     * illustrative 24-hour move vs USD and is likewise approximate.
     */
    val table: List<CurrencyRate> = listOf(
        CurrencyRate(code = "USD", name = "US Dollar", flag = "🇺🇸", symbol = "$", ratePerUsd = 1.0, dailyChangePct = 0.0),
        CurrencyRate(code = "EUR", name = "Euro", flag = "🇪🇺", symbol = "€", ratePerUsd = 0.9200, dailyChangePct = -0.12),
        CurrencyRate(code = "GBP", name = "British Pound", flag = "🇬🇧", symbol = "£", ratePerUsd = 0.7850, dailyChangePct = 0.08),
        CurrencyRate(code = "JPY", name = "Japanese Yen", flag = "🇯🇵", symbol = "¥", ratePerUsd = 156.80, dailyChangePct = 0.45),
        CurrencyRate(code = "CAD", name = "Canadian Dollar", flag = "🇨🇦", symbol = "C$", ratePerUsd = 1.3700, dailyChangePct = -0.21),
        CurrencyRate(code = "AUD", name = "Australian Dollar", flag = "🇦🇺", symbol = "A$", ratePerUsd = 1.5200, dailyChangePct = 0.33),
        CurrencyRate(code = "MXN", name = "Mexican Peso", flag = "🇲🇽", symbol = "Mex$", ratePerUsd = 18.65, dailyChangePct = -0.05),
        CurrencyRate(code = "CHF", name = "Swiss Franc", flag = "🇨🇭", symbol = "Fr", ratePerUsd = 0.8900, dailyChangePct = -0.18),
        CurrencyRate(code = "CNY", name = "Chinese Yuan", flag = "🇨🇳", symbol = "¥", ratePerUsd = 7.2500, dailyChangePct = 0.04),
        CurrencyRate(code = "INR", name = "Indian Rupee", flag = "🇮🇳", symbol = "₹", ratePerUsd = 83.60, dailyChangePct = 0.06),
        CurrencyRate(code = "SGD", name = "Singapore Dollar", flag = "🇸🇬", symbol = "S$", ratePerUsd = 1.3500, dailyChangePct = 0.11),
        CurrencyRate(code = "THB", name = "Thai Baht", flag = "🇹🇭", symbol = "฿", ratePerUsd = 36.20, dailyChangePct = 0.18),
        CurrencyRate(code = "AED", name = "UAE Dirham", flag = "🇦🇪", symbol = "د.إ", ratePerUsd = 3.6725, dailyChangePct = 0.0),
        CurrencyRate(code = "BRL", name = "Brazilian Real", flag = "🇧🇷", symbol = "R$", ratePerUsd = 5.5500, dailyChangePct = -0.25),
    )

    private val byCode: Map<String, CurrencyRate> = table.associateBy { it.code }

    /**
     * Look up a currency by ISO code. Anything not in the bundled [table] falls back to a sensible
     * default — treated at USD parity (`ratePerUsd == 1.0`) with the raw code as its label — so a
     * conversion is always computable rather than crashing or returning null.
     */
    fun rate(code: String): CurrencyRate =
        byCode[code] ?: CurrencyRate(
            code = code,
            name = code,
            flag = "🏳️",
            symbol = code,
            ratePerUsd = 1.0,
            dailyChangePct = 0.0,
        )
}
