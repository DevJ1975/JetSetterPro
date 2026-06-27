package com.jetsetter.pro.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(SubscriptionUiState(isLoading = true))
    val ui: StateFlow<SubscriptionUiState> = _ui.asStateFlow()

    // Moshi adapter for the persisted entitlement (plan + start date), mirroring the Moshi idiom
    // used by core/data/local/Converters.kt and the other feature stores.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val entitlementAdapter = moshi.adapter(SubscriptionEntitlement::class.java)

    init {
        viewModelScope.launch {
            val features = repository.loadFeatures()
            val plans = repository.loadPlans()
            // Restore the tester's Pro entitlement; older builds persisted a bare boolean which no
            // longer parses, so runCatching simply falls back to "not subscribed".
            val saved = stateStore.read(KEY_ENTITLEMENT)
                ?.let { runCatching { entitlementAdapter.fromJson(it) }.getOrNull() }
            _ui.update {
                it.copy(
                    isLoading = false,
                    isProActive = saved?.isProActive ?: false,
                    activePlanId = saved?.planId,
                    subscribedOnIso = saved?.startedOnIso,
                    features = features,
                    plans = plans,
                    todayIso = LocalDate.now().toString(),
                    // Pre-select the best-value plan so the primary CTA always has a target.
                    selectedPlanId = plans.firstOrNull { plan -> plan.isBestValue }?.id
                        ?: plans.firstOrNull()?.id,
                )
            }
        }
    }

    fun selectPlan(planId: String) {
        // Ignore selection changes mid-purchase so the confirmed plan can't drift.
        if (_ui.value.isProcessing) return
        _ui.update { it.copy(selectedPlanId = planId) }
    }

    /**
     * Mock purchase — simulates a short billing round-trip (no billing client / network), then
     * flips local "Pro active" state and records which plan was bought + when, so the Pro banner
     * can show a real, computed renewal date.
     */
    fun subscribe() {
        val state = _ui.value
        val plan = state.selectedPlan ?: return
        if (state.isProActive || state.isProcessing) return
        viewModelScope.launch {
            _ui.update { it.copy(isProcessing = true) }
            delay(PURCHASE_DELAY_MS)
            val startedOn = LocalDate.now().toString()
            _ui.update {
                it.copy(
                    isProcessing = false,
                    isProActive = true,
                    activePlanId = plan.id,
                    subscribedOnIso = startedOn,
                )
            }
            persist(SubscriptionEntitlement(isProActive = true, planId = plan.id, startedOnIso = startedOn))
        }
    }

    /** Mock cancellation, returns to the paywall and clears the recorded plan/start date. */
    fun cancel() {
        if (_ui.value.isProcessing) return
        _ui.update { it.copy(isProActive = false, activePlanId = null, subscribedOnIso = null) }
        persist(SubscriptionEntitlement(isProActive = false))
    }

    private fun persist(entitlement: SubscriptionEntitlement) {
        viewModelScope.launch {
            stateStore.save(KEY_ENTITLEMENT, entitlementAdapter.toJson(entitlement))
        }
    }

    private companion object {
        const val KEY_ENTITLEMENT = "subscription_entitlement"
        const val PURCHASE_DELAY_MS = 650L
    }
}
