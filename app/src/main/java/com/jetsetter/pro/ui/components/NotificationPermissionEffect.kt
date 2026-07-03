package com.jetsetter.pro.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the POST_NOTIFICATIONS runtime permission once when it enters composition (API 33+;
 * a no-op below that, where the permission doesn't exist). Drop this into any screen whose flow
 * posts a notification — e.g. Trip Disruption — so the system prompt appears in context rather
 * than at app launch. Denial is respected: notifications are simply skipped downstream
 * (see `JetNotifier.canNotify`), never retried in a loop.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result handled implicitly: JetNotifier re-checks before every post */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
