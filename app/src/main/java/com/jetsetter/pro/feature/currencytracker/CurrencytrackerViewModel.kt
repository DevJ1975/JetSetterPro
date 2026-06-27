package com.jetsetter.pro.feature.currencytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencytrackerViewModel @Inject constructor(
    private val repository: CurrencytrackerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CurrencytrackerUiState(isLoading = true))
    val ui: StateFlow<CurrencytrackerUiState> = _ui.asStateFlow()

    init {
        // Seed the rate snapshot and restore the tester's saved converter state before they
        // interact, so the converter and list never flash empty on first open.
        viewModelScope.launch {
            val snapshot = repository.currencies()
            val updated = repository.lastUpdated()
            val saved = repository.loadPrefs()
            val codes = snapshot.mapTo(HashSet()) { it.code }

            // Restore saved selections, but defend against codes no longer in the snapshot and
            // against From == To (which would make the converter a no-op).
            val base = saved?.baseCurrencyCode?.takeIf { it in codes } ?: repository.referenceCurrency
            val target = saved?.targetCurrencyCode?.takeIf { it in codes && it != base }
                ?: snapshot.firstOrNull { it.code != base }?.code
                ?: base

            _ui.update {
                it.copy(
                    currencies = snapshot,
                    amountInput = saved?.amountInput ?: it.amountInput,
                    baseCurrencyCode = base,
                    targetCurrencyCode = target,
                    lastUpdated = updated,
                    isLoading = false,
                )
            }
        }
    }

    /** Keep only digits and a single decimal point so the amount stays parseable from a text keyboard. */
    fun onAmountChange(raw: String) {
        val sanitized = buildString {
            var seenDot = false
            for (ch in raw) {
                when {
                    ch.isDigit() -> append(ch)
                    ch == '.' && !seenDot -> {
                        seenDot = true
                        append(ch)
                    }
                }
            }
        }
        _ui.update { it.copy(amountInput = sanitized) }
        persist()
    }

    /** Pick the From currency. Choosing the current To currency swaps the pair instead of colliding. */
    fun onSelectBase(code: String) {
        _ui.update { state ->
            if (code == state.targetCurrencyCode) {
                state.copy(baseCurrencyCode = code, targetCurrencyCode = state.baseCurrencyCode)
            } else {
                state.copy(baseCurrencyCode = code)
            }
        }
        persist()
    }

    /** Pick the To currency. Choosing the current From currency swaps the pair instead of colliding. */
    fun onSelectTarget(code: String) {
        _ui.update { state ->
            if (code == state.baseCurrencyCode) {
                state.copy(targetCurrencyCode = code, baseCurrencyCode = state.targetCurrencyCode)
            } else {
                state.copy(targetCurrencyCode = code)
            }
        }
        persist()
    }

    /** Swap the From/To pair so the same typed amount converts the other direction. */
    fun onSwapCurrencies() {
        _ui.update { state ->
            state.copy(
                baseCurrencyCode = state.targetCurrencyCode,
                targetCurrencyCode = state.baseCurrencyCode,
            )
        }
        persist()
    }

    private fun persist() {
        val state = _ui.value
        viewModelScope.launch {
            repository.savePrefs(
                ConverterPrefs(
                    amountInput = state.amountInput,
                    baseCurrencyCode = state.baseCurrencyCode,
                    targetCurrencyCode = state.targetCurrencyCode,
                ),
            )
        }
    }
}
