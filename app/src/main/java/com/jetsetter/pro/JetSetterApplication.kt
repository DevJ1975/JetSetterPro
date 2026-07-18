package com.jetsetter.pro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jetsetter.pro.core.ai.NanoModelManager
import com.jetsetter.pro.core.data.remote.FlightAwareService
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.kb.KbSeeder
import com.jetsetter.pro.core.work.DisruptionMonitorWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * WorkManager is on manual initialization (the default `WorkManagerInitializer` is removed in
 * the manifest, plan R6) so [Configuration.Provider] can hand it the injected
 * [HiltWorkerFactory] — that's what lets `@HiltWorker`s like [DisruptionMonitorWorker]
 * constructor-inject their dependencies.
 */
@HiltAndroidApp
class JetSetterApplication : Application(), Configuration.Provider {

    @Inject lateinit var kbSeeder: KbSeeder
    @Inject lateinit var nanoModelManager: NanoModelManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var flightAwareService: FlightAwareService
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Seed IRIS's on-device RAG knowledge base once per KB version. Fire-and-forget on the
        // application IO scope so startup never blocks; failures leave IRIS un-grounded, not broken.
        appScope.launch { kbSeeder.seedIfNeeded() }
        // Warm up the on-device Gemini Nano model if it's already present (never auto-downloads).
        appScope.launch { nanoModelManager.ensureReady() }
        scheduleDisruptionMonitor()
    }

    /**
     * Schedules the 15-minute background disruption poll (plan B6) — only when FlightAware is
     * configured; without a key the worker could only ever no-op, so nothing is enqueued (and a
     * schedule left over from a since-removed key is cancelled). Unique + KEEP preserves the
     * existing cadence across app restarts.
     */
    private fun scheduleDisruptionMonitor() {
        val workManager = WorkManager.getInstance(this)
        if (!flightAwareService.isConfigured) {
            workManager.cancelUniqueWork(DisruptionMonitorWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DisruptionMonitorWorker>(
            DisruptionMonitorWorker.INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            DisruptionMonitorWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
