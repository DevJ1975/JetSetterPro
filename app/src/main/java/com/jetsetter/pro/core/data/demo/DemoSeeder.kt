package com.jetsetter.pro.core.data.demo

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.work.DisruptionMonitorWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the whole app into a pristine, self-consistent "real traveler" state for presentations —
 * the Android counterpart of the iOS `DemoSeeder`. One call resets every data layer:
 *
 *  1. [ModuleStateStore] is wiped, so all 27 mock-first feature modules (check-in records,
 *     disruption decisions, wallet passes, journal entries, …) fall back to their curated seeds.
 *  2. Room trips + expenses are reset to [com.jetsetter.pro.core.data.mock.MockData].
 *  3. The traveler profile gets demo-friendly defaults without clobbering a name the presenter
 *     already typed, and onboarding is marked complete so the app opens straight to Home.
 *
 * Enabling demo mode also schedules the scripted disruption push (see [scheduleDisruptionAlert]):
 * ~25 seconds later a real notification lands in the shade — "DL 1423 delayed" — which is the
 * demo's opening beat.
 */
@Singleton
class DemoSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferencesRepository,
    private val moduleState: ModuleStateStore,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
) {

    /** Enables demo mode and resets all data to the curated traveler seed. */
    suspend fun enableDemoMode() {
        prefs.setDemoMode(true)
        resetDemoData()
        scheduleDisruptionAlert()
    }

    /** Leaves demo mode. Data stays as-is; the presenter can reset or keep exploring. */
    suspend fun disableDemoMode() {
        WorkManager.getInstance(context).cancelUniqueWork(DisruptionMonitorWorker.UNIQUE_NAME)
        prefs.setDemoMode(false)
    }

    /**
     * Restores every module to its pristine seed. Order matters: the DataStore wipe clears the
     * `*_seeded_*` flags, then the Room resets re-arm them — so nothing double-seeds later.
     */
    suspend fun resetDemoData() {
        moduleState.clearAll()
        tripRepository.resetToSeed()
        expenseRepository.resetToSeed()

        // Fill profile blanks with the demo persona; never overwrite what the presenter typed.
        prefs.setHasCompletedOnboarding(true)
        val snapshot = prefs.preferences.first()
        if (snapshot.displayName.isBlank()) prefs.setDisplayName(DEMO_TRAVELER_NAME)
        if (snapshot.homeAirport.isBlank()) prefs.setHomeAirport(DEMO_HOME_AIRPORT)
    }

    /**
     * Schedules the one-shot scripted disruption push via [DisruptionMonitorWorker]. REPLACE
     * policy: re-enabling demo mode re-arms the beat instead of stacking alerts.
     */
    fun scheduleDisruptionAlert(delaySeconds: Long = DISRUPTION_ALERT_DELAY_SECONDS) {
        val request = OneTimeWorkRequestBuilder<DisruptionMonitorWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    DisruptionMonitorWorker.KEY_TITLE to DisruptionMonitorWorker.DEFAULT_TITLE,
                    DisruptionMonitorWorker.KEY_TEXT to DisruptionMonitorWorker.DEFAULT_TEXT,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DisruptionMonitorWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val DEMO_TRAVELER_NAME = "Jordan Ellis"
        const val DEMO_HOME_AIRPORT = "LAS"
        const val DISRUPTION_ALERT_DELAY_SECONDS = 25L
    }
}
