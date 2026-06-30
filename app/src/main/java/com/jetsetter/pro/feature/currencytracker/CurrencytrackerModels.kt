package com.jetsetter.pro.feature.currencytracker

/**
 * A single currency in the bundled offline FX table, quoted against the app's reference currency (USD).
 *
 * Module-prefixed so the name can't collide with currency/rate types elsewhere in the app.
 * [ratePerUsd] is how many units of [code] one US dollar buys (USD itself is exactly `1.0`);
 * [dailyChangePct] is the 24-hour move versus USD as a percentage (positive = strengthened).
 * Any base→target conversion is derived from these USD anchors as a cross rate, so every number
 * the screen shows stays self-consistent no matter which pair the tester picks.
 */
data class CurrencyRate(
    val code: String,
    val name: String,
    val flag: String,
    val symbol: String,
    val ratePerUsd: Double,
    val dailyChangePct: Double,
)

/**
 * The tester-mutable slice of the converter (amount typed + the From/To pair). Persisted as a
 * single Moshi-serialized JSON blob via `ModuleStateStore` so the converter reopens exactly as it
 * was left — the same Moshi idiom Room uses in `core/data/local/Converters.kt`.
 */
data class ConverterPrefs(
    val amountInput: String,
    val baseCurrencyCode: String,
    val targetCurrencyCode: String,
)
