package com.jetsetter.pro.feature.currencytracker

/**
 * Single immutable UI-state object for the Currency screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 *
 * The stored fields are the only source of truth: the loaded [currencies] snapshot, the raw
 * [amountInput] text the tester typed, and the selected From ([baseCurrencyCode]) / To
 * ([targetCurrencyCode]) pair. Everything the converter renders — the parsed amount, the cross
 * rate, the converted value — is derived live from those fields so it can never drift out of sync
 * with the inputs.
 */
data class CurrencytrackerUiState(
    val currencies: List<CurrencyRate> = emptyList(),
    val amountInput: String = "100",
    val baseCurrencyCode: String = "USD",
    val targetCurrencyCode: String = "EUR",
    val lastUpdated: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** The resolved From currency, or null until the snapshot has loaded. */
    val baseCurrency: CurrencyRate?
        get() = currencies.firstOrNull { it.code == baseCurrencyCode }

    /** The resolved To currency, or null until the snapshot has loaded. */
    val targetCurrency: CurrencyRate?
        get() = currencies.firstOrNull { it.code == targetCurrencyCode }

    /** Parsed amount, or null when the field is blank / unparseable — this drives the empty + validation state. */
    val amount: Double?
        get() = amountInput.toDoubleOrNull()

    /** Units of the To currency per 1 unit of the From currency, derived via the USD anchors. */
    val crossRate: Double?
        get() {
            val base = baseCurrency ?: return null
            val target = targetCurrency ?: return null
            if (base.ratePerUsd == 0.0) return null
            return target.ratePerUsd / base.ratePerUsd
        }

    /** The live converted value (amount × cross rate), or null when the amount is missing. */
    val convertedAmount: Double?
        get() {
            val value = amount ?: return null
            val rate = crossRate ?: return null
            return value * rate
        }
}
