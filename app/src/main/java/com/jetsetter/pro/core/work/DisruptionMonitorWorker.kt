package com.jetsetter.pro.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jetsetter.pro.core.notifications.JetNotifier

/**
 * Background flight-disruption monitor — the Android counterpart of the iOS
 * `DisruptionMonitorService` (which used BGTaskScheduler). Mock-first, like every repository:
 * with no FlightAware key configured it raises the alert for the seeded monitored flight
 * (DL 1423), which is exactly what demo mode schedules a one-shot of; when live flight APIs
 * come online, `doWork` grows a polling branch in front of the notification.
 *
 * Input data ([KEY_TITLE]/[KEY_TEXT]) lets the scheduler shape the alert copy; absent keys fall
 * back to the seeded DL 1423 disruption so the worker never posts an empty notification.
 */
class DisruptionMonitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: DEFAULT_TITLE
        val text = inputData.getString(KEY_TEXT) ?: DEFAULT_TEXT
        JetNotifier.postDisruptionAlert(applicationContext, title, text)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "disruption_monitor"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"

        // Mirrors the seeded DisruptionRepository flight (DL 1423 delayed 1h 35m, 3 alternatives)
        // so the shade alert and the Trip Disruption screen tell the same story.
        const val DEFAULT_TITLE = "DL 1423 delayed 1h 35m"
        const val DEFAULT_TEXT =
            "Las Vegas → Atlanta now departs 8:35 AM. IRIS found 3 rebooking options — " +
                "open Trip Disruption to confirm one."
    }
}
