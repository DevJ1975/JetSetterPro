package com.jetsetter.pro.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.NanoModelManager
import com.jetsetter.pro.core.backend.CloudBackend
import com.jetsetter.pro.core.data.demo.DemoSeeder
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-startup gate. [Loading] until the first DataStore emission arrives, then [Loaded]
 * with the resolved preferences. The root reads this to choose theme + onboarding-vs-app
 * without flashing onboarding at returning users on the first frame.
 */
sealed interface StartupState {
    data object Loading : StartupState
    data class Loaded(val preferences: UserPreferences) : StartupState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val stateStore: ModuleStateStore,
    private val nanoModelManager: NanoModelManager,
    private val backend: CloudBackend,
    private val demoSeeder: DemoSeeder,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> =
        prefsRepository.preferences
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    /** Eagerly collected so the launch splash can be held until prefs are ready. */
    val startup: StateFlow<StartupState> =
        prefsRepository.preferences
            .map<UserPreferences, StartupState> { StartupState.Loaded(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StartupState.Loading)

    // Live Features-menu filter. Persisted as JSON via ModuleStateStore (Moshi) — the same
    // idiom as Converters.kt / TraveljournalViewModel — so the last filter survives a restart.
    private val filterAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(MoreFilterState::class.java)

    private val _searchQuery = MutableStateFlow("")

    // ── Account section (plan B5b) ────────────────────────────────────────────
    // The session comes straight from the cloud seam; busy + notice are transient UI adornments.
    private val _accountBusy = MutableStateFlow(false)
    private val _accountNotice = MutableStateFlow<String?>(null)

    private val account: Flow<AccountUiState> =
        combine(backend.session, _accountBusy, _accountNotice) { session, busy, notice ->
            AccountUiState(
                isConfigured = backend.isConfigured,
                session = session,
                busy = busy,
                notice = notice,
            )
        }

    /** Combined screen state collected by [MoreScreen] via collectAsStateWithLifecycle. */
    val ui: StateFlow<MoreUiState> =
        combine(preferences, _searchQuery, nanoModelManager.state, account) { prefs, query, nano, acct ->
            MoreUiState(preferences = prefs, searchQuery = query, nanoState = nano, account = acct)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    init {
        // Restore the last-used filter (if any) before the user starts typing.
        viewModelScope.launch {
            stateStore.read(KEY_FILTER)
                ?.let { json -> runCatching { filterAdapter.fromJson(json) }.getOrNull() }
                ?.let { saved -> _searchQuery.value = saved.searchQuery }
        }
        // Refresh on-device-AI status so the IRIS section reflects reality on open.
        viewModelScope.launch { nanoModelManager.ensureReady() }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { prefsRepository.setTheme(theme) }
    fun setDisplayName(value: String) = viewModelScope.launch { prefsRepository.setDisplayName(value) }
    fun setHomeAirport(value: String) = viewModelScope.launch { prefsRepository.setHomeAirport(value) }
    fun setTtsEnabled(value: Boolean) = viewModelScope.launch { prefsRepository.setTtsEnabled(value) }

    /** Demo mode on/off. Enabling re-seeds everything and arms the scripted disruption push. */
    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        if (enabled) demoSeeder.enableDemoMode() else demoSeeder.disableDemoMode()
    }

    /** Restores the pristine demo dataset without touching the demo-mode switch. */
    fun resetDemoData() = viewModelScope.launch { demoSeeder.resetDemoData() }

    /** User-initiated download of the on-device Gemini Nano model (the only thing that starts it). */
    fun downloadOnDeviceModel() = viewModelScope.launch { nanoModelManager.requestDownload() }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
        viewModelScope.launch { stateStore.save(KEY_FILTER, filterAdapter.toJson(MoreFilterState(value))) }
    }

    // ── Account actions (plan B5b) ────────────────────────────────────────────

    /**
     * Runs one of the email/password flows against the cloud seam. The [CloudBackend] auth
     * methods throw on failure (the one deliberate exception to the best-effort doctrine, so
     * settings UIs like this one can surface the reason); outcome lands in the account notice.
     */
    fun submitAccountAuth(mode: AccountAuthMode, email: String, password: String) {
        viewModelScope.launch {
            _accountBusy.value = true
            val result = runCatching {
                when (mode) {
                    AccountAuthMode.SIGN_IN -> backend.signInWithEmail(email.trim(), password)
                    AccountAuthMode.CREATE -> backend.signUpWithEmail(email.trim(), password)
                    AccountAuthMode.LINK -> backend.linkEmailToAnonymous(email.trim(), password)
                }
            }
            _accountBusy.value = false
            _accountNotice.value = result.fold(
                onSuccess = { AccountLogic.authSuccessMessage(mode) },
                onFailure = { AccountLogic.authFailureMessage(mode, it) },
            )
        }
    }

    /** Signs out of the cloud session (best-effort, never throws). Local data stays. */
    fun signOutOfCloud() {
        viewModelScope.launch {
            backend.signOut()
            _accountNotice.value = "Signed out. Your data stays on this device."
        }
    }

    /**
     * Full account deletion via [CloudBackend.deleteAccount] — server-side first, then the local
     * wipe. On failure (edge function unreachable / not deployed) nothing is wiped and the
     * notice says so; on success [onDeleted] fires so the caller can navigate to a fresh state.
     */
    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _accountBusy.value = true
            val result = backend.deleteAccount()
            _accountBusy.value = false
            _accountNotice.value = AccountLogic.deleteResultMessage(result)
            if (result.isSuccess) onDeleted()
        }
    }

    fun dismissAccountNotice() {
        _accountNotice.value = null
    }

    private companion object {
        const val KEY_FILTER = "more_feature_filter"
    }
}
