package com.jetsetter.pro.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jetsetter.pro.R

/**
 * Posts flight-disruption alerts to the system shade — the Android counterpart of the iOS
 * `UNUserNotificationCenter` path. Stateless (`object`) so both the ViewModel layer and
 * WorkManager workers can call it without DI plumbing. Silently no-ops when the runtime
 * notification permission (API 33+) hasn't been granted or notifications are disabled.
 */
object JetNotifier {

    private const val CHANNEL_ID = "disruption_alerts"
    const val DISRUPTION_NOTIFICATION_ID = 1423

    /** True when a posted notification would actually be shown. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Posts (or updates) the disruption alert. Tapping it brings the app to the foreground via
     * the launcher intent. Safe to call unconditionally — permission is re-checked here.
     */
    @SuppressLint("MissingPermission") // canNotify() performs the POST_NOTIFICATIONS check.
    fun postDisruptionAlert(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flight)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .apply { contentIntent?.let(::setContentIntent) }
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(DISRUPTION_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Disruption alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Flight delays, cancellations, and rebooking updates"
            },
        )
    }
}
