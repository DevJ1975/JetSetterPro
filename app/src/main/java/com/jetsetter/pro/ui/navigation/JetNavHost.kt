package com.jetsetter.pro.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jetsetter.pro.feature.expenses.ExpensesScreen
import com.jetsetter.pro.feature.home.HomeScreen
import com.jetsetter.pro.feature.iris.IrisChatScreen
import com.jetsetter.pro.feature.itinerary.ItineraryScreen
import com.jetsetter.pro.feature.more.MoreScreen
import com.jetsetter.pro.ui.components.FeatureScaffold

@Composable
fun JetNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = JetDestination.HOME.route,
        modifier = modifier,
        // Tab switches crossfade; feature screens (below) override with a horizontal slide.
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(JetDestination.HOME.route) {
            HomeScreen(
                onOpenFlightTracker = { navController.navigate("flighttracker") },
                onOpenFeature = { route -> navController.navigate(route) },
                onOpenExpenses = { navController.navigate(JetDestination.EXPENSES.route) },
            )
        }
        composable(JetDestination.ITINERARY.route) { ItineraryScreen() }
        composable(JetDestination.IRIS.route) { IrisChatScreen() }
        composable(JetDestination.EXPENSES.route) { ExpensesScreen() }
        composable(JetDestination.MORE.route) {
            MoreScreen(onOpenFeature = { route -> navController.navigate(route) })
        }
        // Every ported feature module, reached from the More → Features menu. These push in
        // from the right and slide back out on Back, so drilling in feels like a stack.
        // Flight Tracker additionally accepts an optional ?ident= query arg (IRIS `trackFlight`
        // deep link); plain "flighttracker" navigation still matches via the null default.
        featureCatalog.forEach { entry ->
            val isFlightTracker = entry.route == "flighttracker"
            composable(
                route = if (isFlightTracker) "flighttracker?ident={ident}" else entry.route,
                arguments = if (isFlightTracker) {
                    listOf(
                        navArgument("ident") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    )
                } else {
                    emptyList()
                },
                enterTransition = { slideIntoContainer(SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
                popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(260)) + fadeOut(tween(200)) },
            ) {
                FeatureScaffold(onBack = { navController.popBackStack() }) { entry.screen() }
            }
        }
    }
}
