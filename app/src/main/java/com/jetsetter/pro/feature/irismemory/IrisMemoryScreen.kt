package com.jetsetter.pro.feature.irismemory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.intelligence.IrisPreference
import com.jetsetter.pro.core.intelligence.IrisPreferenceCategory
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [IrisMemoryViewModel], collects its [IrisMemoryUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [IrisMemoryContent].
 */
@Composable
fun IrisMemoryScreen(viewModel: IrisMemoryViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    IrisMemoryContent(
        state = state,
        onDeletePreference = viewModel::deletePreference,
        onShowForgetDialog = viewModel::showForgetDialog,
        onDismissForgetDialog = viewModel::dismissForgetDialog,
        onForgetEverything = viewModel::forgetEverything,
        onSetLearningEnabled = viewModel::setLearningEnabled,
        onSetLearnFromReceipts = viewModel::setLearnFromReceipts,
        onSetLearnFromCheckIns = viewModel::setLearnFromCheckIns,
        onSetLearnFromTrips = viewModel::setLearnFromTrips,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun IrisMemoryContent(
    state: IrisMemoryUiState,
    onDeletePreference: (String) -> Unit,
    onShowForgetDialog: () -> Unit,
    onDismissForgetDialog: () -> Unit,
    onForgetEverything: () -> Unit,
    onSetLearningEnabled: (Boolean) -> Unit,
    onSetLearnFromReceipts: (Boolean) -> Unit,
    onSetLearnFromCheckIns: (Boolean) -> Unit,
    onSetLearnFromTrips: (Boolean) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + gentle slide-in on first composition. Purely visual; runs once
    // and leaves scrolling untouched (a graphics layer over the list, not a wrapping container).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "contentAlpha",
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 24f,
        animationSpec = tween(durationMillis = 250),
        label = "contentOffsetY",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffsetY
                    },
                contentPadding = PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = spacing.large,
                    bottom = spacing.xlarge,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item { Header(preferenceCount = state.preferences.size) }
                item {
                    ConsentCard(
                        state = state,
                        onSetLearningEnabled = onSetLearningEnabled,
                        onSetLearnFromReceipts = onSetLearnFromReceipts,
                        onSetLearnFromCheckIns = onSetLearnFromCheckIns,
                        onSetLearnFromTrips = onSetLearnFromTrips,
                    )
                }
                item { SectionLabel("What IRIS remembers") }
                if (state.isEmpty) {
                    item { EmptyMemoryCard() }
                } else {
                    state.grouped.forEach { (category, preferences) ->
                        item(key = "header-${category.name}") {
                            CategoryHeader(category = category, count = preferences.size)
                        }
                        items(preferences, key = { it.id }) { preference ->
                            PreferenceCard(
                                preference = preference,
                                onDelete = { onDeletePreference(preference.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item { ForgetEverythingButton(onClick = onShowForgetDialog) }
                }
            }
        }
    }

    if (state.showForgetDialog) {
        ForgetEverythingDialog(
            onConfirm = onForgetEverything,
            onDismiss = onDismissForgetDialog,
        )
    }
}

@Composable
private fun Header(preferenceCount: Int) {
    val colors = JetTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.xsmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("IRIS Memory", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            AccentTag(
                text = if (preferenceCount == 1) "1 memory" else "$preferenceCount memories",
                icon = Icons.Filled.Psychology,
            )
        }
        Text(
            "Everything IRIS remembers stays on this device and is never shared with third parties.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = JetTheme.typography.label,
        color = JetTheme.colors.textSecondary,
        modifier = Modifier.padding(start = JetTheme.spacing.xsmall, top = JetTheme.spacing.small),
    )
}

/**
 * Learning consent (spec §1.6): the master switch plus the three per-source switches. The three
 * are visually disabled while the master is off — the underlying store already treats them as
 * silent no-ops in that state.
 */
@Composable
private fun ConsentCard(
    state: IrisMemoryUiState,
    onSetLearningEnabled: (Boolean) -> Unit,
    onSetLearnFromReceipts: (Boolean) -> Unit,
    onSetLearnFromCheckIns: (Boolean) -> Unit,
    onSetLearnFromTrips: (Boolean) -> Unit,
) {
    JetCard(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            title = "Learn from my travel",
            subtitle = "Master switch — off means IRIS records nothing new.",
            checked = state.learningEnabled,
            onCheckedChange = onSetLearningEnabled,
        )
        RowDivider()
        ToggleRow(
            title = "Receipts & expenses",
            subtitle = "Learn spending patterns from logged expenses and scanned receipts.",
            checked = state.learnFromReceipts,
            onCheckedChange = onSetLearnFromReceipts,
            enabled = state.learningEnabled,
        )
        RowDivider()
        ToggleRow(
            title = "Check-ins",
            subtitle = "Learn seat preferences from flight check-ins.",
            checked = state.learnFromCheckIns,
            onCheckedChange = onSetLearnFromCheckIns,
            enabled = state.learningEnabled,
        )
        RowDivider()
        ToggleRow(
            title = "Trips & places",
            subtitle = "Learn destinations and travel rhythm from completed trips.",
            checked = state.learnFromTrips,
            onCheckedChange = onSetLearnFromTrips,
            enabled = state.learningEnabled,
        )
    }
}

/**
 * A labelled on/off setting row with a Material switch — the app's settings idiom (mirrors
 * More → IRIS). [enabled] = false ghosts the row and ignores taps (per-source switches while
 * the master toggle is off).
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
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
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
        )
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(JetTheme.colors.separator),
    )
}

@Composable
private fun CategoryHeader(category: IrisPreferenceCategory, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = JetTheme.spacing.xsmall, top = JetTheme.spacing.xsmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.label.uppercase(),
            style = JetTheme.typography.caption,
            color = JetTheme.colors.textSecondary,
        )
        Text(
            if (count == 1) "1 item" else "$count items",
            style = JetTheme.typography.caption,
            color = JetTheme.colors.textSecondary,
        )
    }
}

/** One remembered preference: value, confidence bar, and a per-row delete icon. */
@Composable
private fun PreferenceCard(
    preference: IrisPreference,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    JetCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                preference.value,
                style = typography.cardTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Forget \"${preference.value}\"",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(spacing.xsmall))
        ConfidenceBar(confidence = preference.confidence)
    }
}

/** Confidence 0..1 as a slim progress bar with a percent caption. */
@Composable
private fun ConfidenceBar(confidence: Double) {
    val colors = JetTheme.colors
    val fraction = confidence.toFloat().coerceIn(0f, 1f)
    // Ease the fill in toward the exact stored confidence (no math change), so the bar grows
    // smoothly on first show instead of snapping to its final width.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 450),
        label = "confidenceFraction",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
    ) {
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = colors.accent,
            trackColor = colors.separator,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            "${(fraction * 100).toInt()}%",
            style = JetTheme.typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyMemoryCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(
                "Nothing remembered yet",
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Ask IRIS to remember a preference — like \"remember I prefer window seats\" — and it will appear here.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ForgetEverythingButton(onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.danger,
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Forget everything", style = JetTheme.typography.cardTitle)
    }
}

@Composable
private fun ForgetEverythingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = JetTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("Forget everything?") },
        text = {
            Text("IRIS will permanently delete every remembered preference. This can't be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Forget everything", color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}

/** Human-readable label for a preference category (wire names stay camelCase for iOS parity). */
private val IrisPreferenceCategory.label: String
    get() = when (this) {
        IrisPreferenceCategory.DIETARY -> "Dietary"
        IrisPreferenceCategory.SEATING -> "Seating"
        IrisPreferenceCategory.HOTEL_STYLE -> "Hotel style"
        IrisPreferenceCategory.AIRLINE_PREFERENCE -> "Airlines"
        IrisPreferenceCategory.TRANSPORTATION -> "Transportation"
        IrisPreferenceCategory.DESTINATIONS -> "Destinations"
        IrisPreferenceCategory.ACTIVITIES -> "Activities"
        IrisPreferenceCategory.GENERAL -> "General"
    }

@Preview(showBackground = true, name = "IRIS Memory – populated")
@Composable
private fun IrisMemoryContentPreview() {
    JetSetterTheme {
        IrisMemoryContent(
            state = IrisMemoryUiState(
                preferences = listOf(
                    IrisPreference(
                        id = "p1",
                        category = IrisPreferenceCategory.SEATING,
                        value = "Window seat",
                        createdAt = "2026-07-01T12:00:00Z",
                        lastReinforcedAt = "2026-07-10T12:00:00Z",
                        confidence = 0.9,
                    ),
                    IrisPreference(
                        id = "p2",
                        category = IrisPreferenceCategory.DIETARY,
                        value = "Vegetarian meals",
                        createdAt = "2026-07-05T12:00:00Z",
                        lastReinforcedAt = "2026-07-05T12:00:00Z",
                        confidence = 0.7,
                    ),
                ),
            ),
            onDeletePreference = {},
            onShowForgetDialog = {},
            onDismissForgetDialog = {},
            onForgetEverything = {},
            onSetLearningEnabled = {},
            onSetLearnFromReceipts = {},
            onSetLearnFromCheckIns = {},
            onSetLearnFromTrips = {},
        )
    }
}

@Preview(showBackground = true, name = "IRIS Memory – empty, master off")
@Composable
private fun IrisMemoryContentEmptyPreview() {
    JetSetterTheme {
        IrisMemoryContent(
            state = IrisMemoryUiState(learningEnabled = false),
            onDeletePreference = {},
            onShowForgetDialog = {},
            onDismissForgetDialog = {},
            onForgetEverything = {},
            onSetLearningEnabled = {},
            onSetLearnFromReceipts = {},
            onSetLearnFromCheckIns = {},
            onSetLearnFromTrips = {},
        )
    }
}
