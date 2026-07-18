package com.jetsetter.pro.feature.more

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.ai.NanoModelManager
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
 * and forwards events. Holds no logic — see [MoreContent]. [onAccountDeleted] fires only after a
 * fully successful account deletion (cloud + local wipe) so the host can navigate to a fresh
 * Home; a failed deletion never fires it (nothing was wiped — the card's notice explains).
 */
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenFeature: (String) -> Unit = {},
    onAccountDeleted: () -> Unit = {},
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    MoreContent(
        state = state,
        onSelectTheme = viewModel::setTheme,
        onDisplayNameChange = viewModel::setDisplayName,
        onHomeAirportChange = viewModel::setHomeAirport,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenFeature = onOpenFeature,
        onSetTtsEnabled = viewModel::setTtsEnabled,
        onDownloadOnDeviceAi = viewModel::downloadOnDeviceModel,
        onSubmitAccountAuth = viewModel::submitAccountAuth,
        onSignOutCloud = viewModel::signOutOfCloud,
        onDeleteAccount = { viewModel.deleteAccount(onDeleted = onAccountDeleted) },
        onDismissAccountNotice = viewModel::dismissAccountNotice,
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
    onSetTtsEnabled: (Boolean) -> Unit = {},
    onDownloadOnDeviceAi: () -> Unit = {},
    onSubmitAccountAuth: (AccountAuthMode, String, String) -> Unit = { _, _, _ -> },
    onSignOutCloud: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onDismissAccountNotice: () -> Unit = {},
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

        Section(title = "Account") {
            AccountCard(
                state = state.account,
                onSubmitAuth = onSubmitAccountAuth,
                onSignOut = onSignOutCloud,
                onDeleteAccount = onDeleteAccount,
                onDismissNotice = onDismissAccountNotice,
            )
        }

        Section(title = "IRIS") {
            JetCard(modifier = Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "Speak replies aloud",
                    subtitle = "IRIS reads its answers using on-device text-to-speech.",
                    checked = prefs.ttsEnabled,
                    onCheckedChange = onSetTtsEnabled,
                )
                RowDivider()
                NanoRow(state = state.nanoState, onDownload = onDownloadOnDeviceAi)
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
                Text("Version 0.1.0", style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
                Text("Trainovate Technologies LLC", style = JetTheme.typography.caption, color = colors.textSecondary)
            }
        }
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

/** A labelled on/off setting row with a Material switch. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = JetTheme.typography.bodyMedium, color = colors.textPrimary)
            Text(subtitle, style = JetTheme.typography.caption, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
        )
    }
}

/** On-device AI (Gemini Nano) status + a tap-to-download action when the model is available. */
@Composable
private fun NanoRow(state: NanoModelManager.State, onDownload: () -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val actionable = state is NanoModelManager.State.Downloadable || state is NanoModelManager.State.Failed
    val status = when (state) {
        is NanoModelManager.State.Ready -> "Ready — runs on this device, offline"
        is NanoModelManager.State.Downloading -> "Downloading…"
        is NanoModelManager.State.Downloadable -> "Available to download — tap to enable"
        is NanoModelManager.State.Failed -> "Download failed — tap to retry"
        is NanoModelManager.State.Unavailable -> "Not supported on this device (uses cloud IRIS)"
        is NanoModelManager.State.Unknown -> "Checking availability…"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .then(if (actionable) Modifier.clickable(role = Role.Button) { onDownload() } else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("On-device AI (Gemini Nano)", style = JetTheme.typography.bodyMedium, color = colors.textPrimary)
            Text(status, style = JetTheme.typography.caption, color = colors.textSecondary)
        }
        if (actionable) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
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
