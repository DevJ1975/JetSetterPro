package com.jetsetter.pro.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jetsetter.pro.ui.theme.JetTheme

@Composable
fun JetBottomBar(navController: NavHostController) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = colors.surface) {
        JetDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    if (currentRoute != destination.route) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.navigate(destination.route) {
                            // Deterministic tab roots: no save/restore of per-tab stacks, so a
                            // feature screen left stacked on a tab (e.g. Luggage Tracker) can
                            // never be resurrected by a later tab tap — tapping a tab always
                            // lands on that tab's root screen.
                            popUpTo(navController.graph.startDestinationId) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    indicatorColor = colors.accent.copy(alpha = 0.15f),
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
            )
        }
    }
}
