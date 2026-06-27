package com.jetsetter.pro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
) : ViewModel() {

    /**
     * Marks first-run onboarding complete. Persisting this flips the DataStore flag, which
     * the root [com.jetsetter.pro.feature.more.StartupState] observes and recomposes into the
     * main tab shell — so the screen needs no completion callback of its own.
     */
    fun complete() = viewModelScope.launch { prefsRepository.setHasCompletedOnboarding(true) }
}
