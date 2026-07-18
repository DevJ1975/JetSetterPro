package com.jetsetter.pro.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jetsetter.pro.BuildConfig
import com.jetsetter.pro.core.ai.IrisNavEvent
import com.jetsetter.pro.core.util.SmsComposer
import com.jetsetter.pro.ui.navigation.JetBottomBar
import com.jetsetter.pro.ui.navigation.JetDestination
import com.jetsetter.pro.ui.navigation.JetNavHost

/** Root app shell: a 5-tab bottom-navigation Scaffold hosting the feature screens. */
@Composable
fun JetSetterApp(appNavViewModel: AppNavViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Debug-only navigation trail: every destination change is logged so any future silent
    // back-stack anomaly (like the hands-free reset-to-Home bug) leaves evidence in logcat.
    if (BuildConfig.DEBUG) {
        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
                Log.d("JetNav", "destination -> ${destination.route} args=$arguments")
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose { navController.removeOnDestinationChangedListener(listener) }
        }
    }

    // IRIS tool side effects (spec §1.3): the ActionRouter is the only bridge between the tool
    // layer and real navigation — collected here because this composable owns the NavController.
    LaunchedEffect(navController) {
        appNavViewModel.actionRouter.navEvents.collect { event ->
            when (event) {
                is IrisNavEvent.OpenScreen ->
                    navController.navigate(event.screen.route) { launchSingleTop = true }

                is IrisNavEvent.TrackFlight ->
                    navController.navigate(
                        "flighttracker?ident=${Uri.encode(event.flightNumber)}",
                    ) { launchSingleTop = true }

                // The router already parked the prompt in its read-once slot; the IRIS
                // ViewModel consumes it on arrival — we only navigate.
                is IrisNavEvent.OpenIrisWithPrompt ->
                    navController.navigate(JetDestination.IRIS.route) { launchSingleTop = true }

                // Prefilled native composer, never auto-sent; SmsComposer no-ops gracefully
                // when the device has no messaging app.
                is IrisNavEvent.ComposeSms ->
                    SmsComposer.composeSms(context, event.phoneNumbers, event.body)
            }
        }
    }

    Scaffold(
        bottomBar = { JetBottomBar(navController) },
    ) { innerPadding ->
        JetNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}
