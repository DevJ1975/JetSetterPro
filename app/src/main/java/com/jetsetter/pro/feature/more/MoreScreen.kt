package com.jetsetter.pro.feature.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.BuildConfig
import com.jetsetter.pro.core.model.ThemePreference
import com.jetsetter.pro.core.model.UserPreferences
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.navigation.FeatureEntry
import com.jetsetter.pro.ui.navigation.featureCatalog
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [SettingsViewModel], collects its [MoreUiState] lifecycle-aware,
 * and forwards events. Holds no logic — see [MoreContent].
 */
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenFeature: (String) -> Unit = {},
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    MoreContent(
        state = state,
        onSelectTheme = viewModel::setTheme,
        onDisplayNameChange = viewModel::setDisplayName,
        onHomeAirportChange = viewModel::setHomeAirport,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenFeature = onOpenFeature,
        onSetDemoMode = viewModel::setDemoMode,
        onResetDemoData = viewModel::resetDemoData,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable.
 * The Features list is filtered by [MoreUiState.searchQuery] (case-insensitive on title) and
 * re-grouped on the fly.
 */
@Composable
private fun MoreContent(
    state: MoreUiState,
    onSelectTheme: (ThemePreference) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onHomeAirportChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFeature: (String) -> Unit,
    onSetDemoMode: (Boolean) -> Unit = {},
    onResetDemoData: () -> Unit = {},
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val prefs = state.preferences

    // One-time entrance: fade + slight rise on first composition (~250ms). Purely visual;
    // applied via graphicsLayer so it never interferes with the vertical scroll below.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "moreEntrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 16.dp.toPx()
            }
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xsmall)) {
            Text("More", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            Text(
                "Preferences, profile, and every JetSetter tool.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        Section(title = "Appearance") {
            JetCard(modifier = Modifier.fillMaxWidth()) {
                ThemeSelector(selected = prefs.theme, onSelect = onSelectTheme)
            }
        }

        Section(title = "Profile") {
            JetCard(modifier = Modifier.fillMaxWidth()) {
                LabeledField("Display name", prefs.displayName, "Your name", onDisplayNameChange)
                Spacer(Modifier.height(spacing.medium))
                LabeledField("Home airport", prefs.homeAirport, "e.g. LAS", onHomeAirportChange)
            }
        }

        Section(title = "Presentation") {
            JetCard(modifier = Modifier.fillMaxWidth()) {
                DemoModeSection(
                    demoMode = prefs.demoMode,
                    onSetDemoMode = onSetDemoMode,
                    onResetDemoData = onResetDemoData,
                )
            }
        }

        FeaturesSection(
            query = state.searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenFeature = onOpenFeature,
        )

        Section(title = "About") {
            JetCard(modifier = Modifier.fillMaxWidth()) {
                Text("JetSetter Pro for Android", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(spacing.xsmall))
                Text("Version ${BuildConfig.VERSION_NAME}", style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
                Text("Trainovate Technologies LLC", style = JetTheme.typography.caption, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Demo-mode controls: the master switch plus a "reset demo data" action behind a confirmation
 * dialog (it wipes tester-entered data). Flipping the switch on first requests the notification
 * permission (API 33+) so the scripted disruption push — armed by the seeder ~25s later — can
 * actually land in the shade during a presentation.
 */
@Composable
private fun DemoModeSection(
    demoMode: Boolean,
    onSetDemoMode: (Boolean) -> Unit,
    onResetDemoData: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    // Ask for POST_NOTIFICATIONS before enabling, then enable regardless of the answer —
    // JetNotifier silently skips posting when the permission is denied.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onSetDemoMode(true) }
    val enableDemo = {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSetDemoMode(true)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Demo mode", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "Curated traveler data for live presentations — includes a scripted flight-disruption alert.",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(spacing.small))
        Switch(
            checked = demoMode,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (enabled) enableDemo() else onSetDemoMode(false)
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = Color.White,
            ),
        )
    }

    Spacer(Modifier.height(spacing.medium))
    Text(
        "Reset demo data",
        style = JetTheme.typography.bodyMedium,
        color = colors.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { showResetDialog = true }
            .semantics { role = Role.Button }
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = colors.surface,
            title = { Text("Reset demo data?", color = colors.textPrimary) },
            text = {
                Text(
                    "Every module returns to its pristine demo dataset. Trips, expenses, check-ins, " +
                        "and any changes you made will be replaced.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetDemoData()
                }) { Text("Reset", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }
}

/** Section wrapper: an uppercase header above its content, with consistent vertical rhythm. */
@Composable
private fun Section(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title.uppercase(), style = JetTheme.typography.label, color = colors.textSecondary)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun FeaturesSection(
    query: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    val trimmed = query.trim()
    val grouped = remember(trimmed) {
        featureCatalog
            .filter { trimmed.isBlank() || it.title.contains(trimmed, ignoreCase = true) }
            .groupBy { it.group }
    }
    val matchCount = remember(grouped) { grouped.values.sumOf { it.size } }

    Section(
        title = "Features",
        trailing = { AccentTag(text = "$matchCount") },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            SearchField(query = query, onValueChange = onSearchQueryChange)

            if (grouped.isEmpty()) {
                JetCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            "No matches",
                            style = JetTheme.typography.cardTitle,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "No features match “$trimmed”.",
                            style = JetTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                grouped.forEach { (group, entries) ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        Text(
                            group.uppercase(),
                            style = JetTheme.typography.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = spacing.xsmall),
                        )
                        JetCard(modifier = Modifier.fillMaxWidth()) {
                            entries.forEachIndexed { index, entry ->
                                if (index > 0) RowDivider()
                                FeatureRow(entry = entry, onClick = { onOpenFeature(entry.route) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onValueChange: (String) -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        PremiumTextField(
            value = query,
            onValueChange = onValueChange,
            placeholder = "Search features",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeSelector(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
        ThemePreference.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                style = JetTheme.typography.label,
                color = if (isSelected) Color.White else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accent else colors.surfaceElevated)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    }
                    .semantics {
                        role = Role.RadioButton
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val colors = JetTheme.colors
    Column {
        Text(label.uppercase(), style = JetTheme.typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        PremiumTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FeatureRow(entry: FeatureEntry, onClick: () -> Unit) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onClick() }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(entry.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Text(
            entry.title,
            style = JetTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    val colors = JetTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(colors.separator),
    )
}

@Preview(showBackground = true, name = "More – full menu")
@Composable
private fun MoreContentPreview() {
    JetSetterTheme {
        MoreContent(
            state = MoreUiState(
                preferences = UserPreferences(
                    displayName = "Jamil Jones",
                    homeAirport = "LAS",
                    theme = ThemePreference.DARK,
                ),
            ),
            onSelectTheme = {},
            onDisplayNameChange = {},
            onHomeAirportChange = {},
            onSearchQueryChange = {},
            onOpenFeature = {},
        )
    }
}

@Preview(showBackground = true, name = "More – filtered")
@Composable
private fun MoreContentFilteredPreview() {
    JetSetterTheme {
        MoreContent(
            state = MoreUiState(
                preferences = UserPreferences(theme = ThemePreference.DARK),
                searchQuery = "flight",
            ),
            onSelectTheme = {},
            onDisplayNameChange = {},
            onHomeAirportChange = {},
            onSearchQueryChange = {},
            onOpenFeature = {},
        )
    }
}
