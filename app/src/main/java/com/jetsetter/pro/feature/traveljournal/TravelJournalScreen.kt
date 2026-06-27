package com.jetsetter.pro.feature.traveljournal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [TraveljournalViewModel], collects its [TraveljournalUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [TravelJournalContent].
 */
@Composable
fun TravelJournalScreen(viewModel: TraveljournalViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    TravelJournalContent(
        state = state,
        onDraftTitleChange = viewModel::onDraftTitleChange,
        onDraftBodyChange = viewModel::onDraftBodyChange,
        onDraftLocationChange = viewModel::onDraftLocationChange,
        onDraftMoodChange = viewModel::onDraftMoodChange,
        onMoodFilterChange = viewModel::onMoodFilterChange,
        onAddEntry = viewModel::addEntry,
        onDeleteEntry = viewModel::deleteEntry,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun TravelJournalContent(
    state: TraveljournalUiState,
    onDraftTitleChange: (String) -> Unit,
    onDraftBodyChange: (String) -> Unit,
    onDraftLocationChange: (String) -> Unit,
    onDraftMoodChange: (TripJournalMood) -> Unit,
    onMoodFilterChange: (TripJournalMood?) -> Unit,
    onAddEntry: () -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        // One-time entrance: fade + a small upward slide on first composition.
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { contentVisible = true }
        val entranceProgress by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "journalEntrance",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entranceProgress
                    translationY = (1f - entranceProgress) * 16.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("Trip Journal", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            SummaryCard(state = state)

            ComposerCard(
                state = state,
                onDraftTitleChange = onDraftTitleChange,
                onDraftBodyChange = onDraftBodyChange,
                onDraftLocationChange = onDraftLocationChange,
                onDraftMoodChange = onDraftMoodChange,
                onAddEntry = onAddEntry,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ENTRIES", style = JetTheme.typography.caption, color = colors.textSecondary)
                if (state.entries.isNotEmpty()) {
                    Text(
                        "${state.visibleEntries.size} of ${state.memoryCount}",
                        style = JetTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }

            if (state.entries.isNotEmpty()) {
                FilterRow(
                    moodCounts = state.moodCounts,
                    total = state.memoryCount,
                    selected = state.moodFilter,
                    onSelect = onMoodFilterChange,
                )
            }

            when {
                state.entries.isEmpty() -> EmptyState()
                state.visibleEntries.isEmpty() -> FilteredEmptyState(
                    mood = state.moodFilter,
                    onClearFilter = { onMoodFilterChange(null) },
                )
                else -> state.visibleEntries.forEach { entry ->
                    EntryCard(entry = entry, onDelete = { onDeleteEntry(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TraveljournalUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("THIS TRIP", style = typography.caption, color = colors.textSecondary)
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
        ) {
            StatCell(label = "Memories", value = state.memoryCount.toString(), icon = Icons.AutoMirrored.Filled.MenuBook)
            StatCell(label = "Places", value = state.placeCount.toString(), icon = Icons.Filled.Place)
            StatCell(label = "Words", value = state.wordCount.toString(), icon = Icons.Filled.Edit)
        }
        val dominant = state.dominantMood
        if (dominant != null) {
            Spacer(Modifier.height(12.dp))
            AccentTag(
                text = if (state.memoryCount == 1) "Feeling ${dominant.label.lowercase()}"
                else "Mostly ${dominant.label.lowercase()}",
                icon = moodIcon(dominant),
            )
        }
    }
}

@Composable
private fun RowScope.StatCell(label: String, value: String, icon: ImageVector) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(modifier = Modifier.weight(1f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.xsmall),
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Text(value, style = typography.pageTitle, color = colors.textPrimary)
        }
        Text(label.uppercase(), style = typography.caption, color = colors.textSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerCard(
    state: TraveljournalUiState,
    onDraftTitleChange: (String) -> Unit,
    onDraftBodyChange: (String) -> Unit,
    onDraftLocationChange: (String) -> Unit,
    onDraftMoodChange: (TripJournalMood) -> Unit,
    onAddEntry: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text("New entry", style = typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        PremiumTextField(
            value = state.draftTitle,
            onValueChange = onDraftTitleChange,
            placeholder = "Title — e.g. Sunset in Lisbon",
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.showTitleError) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Add a title to save this memory.",
                style = typography.caption,
                color = colors.danger,
            )
        }
        Spacer(Modifier.height(8.dp))
        PremiumTextField(
            value = state.draftLocation,
            onValueChange = onDraftLocationChange,
            placeholder = "Where? — e.g. Lisbon, Portugal",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PremiumTextField(
            value = state.draftBody,
            onValueChange = onDraftBodyChange,
            placeholder = "What happened today?",
            singleLine = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("HOW DID IT FEEL?", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(spacing.small))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            TripJournalMood.entries.forEach { mood ->
                JournalChip(
                    label = mood.label,
                    icon = moodIcon(mood),
                    selected = state.draftMood == mood,
                    onClick = { onDraftMoodChange(mood) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddEntry()
            },
            enabled = state.canAddEntry,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
                disabledContainerColor = colors.separator,
                disabledContentColor = colors.textSecondary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(spacing.small))
            Text("Add entry", style = typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    moodCounts: Map<TripJournalMood, Int>,
    total: Int,
    selected: TripJournalMood?,
    onSelect: (TripJournalMood?) -> Unit,
) {
    val spacing = JetTheme.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        JournalChip(
            label = "All · $total",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        // Only surface moods the traveler has actually logged, so a filter never lands empty.
        TripJournalMood.entries.forEach { mood ->
            val count = moodCounts[mood] ?: 0
            if (count > 0) {
                JournalChip(
                    label = "${mood.label} · $count",
                    icon = moodIcon(mood),
                    selected = selected == mood,
                    onClick = { onSelect(mood) },
                )
            }
        }
    }
}

/** Selectable capsule chip used for both the mood picker and the entries filter. */
@Composable
private fun JournalChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (selected) colors.accent.copy(alpha = 0.18f) else colors.surfaceElevated
    val borderColor = if (selected) colors.accent.copy(alpha = 0.45f) else colors.separator
    val foreground = if (selected) colors.accent else colors.textSecondary
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .border(0.8.dp, borderColor, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(13.dp))
        }
        Text(label, style = JetTheme.typography.label, color = foreground)
    }
}

@Composable
private fun EntryCard(entry: TripJournalEntry, onDelete: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.date, style = typography.caption, color = colors.textSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.xsmall),
            ) {
                AccentTag(text = entry.mood.label, icon = moodIcon(entry.mood))
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${entry.title}",
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(entry.title, style = typography.cardTitle, color = colors.textPrimary)
        if (entry.location.isNotBlank()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
                Text(entry.location, style = typography.caption, color = colors.textSecondary)
            }
        }
        if (entry.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                entry.body,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = JetTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = null,
                tint = colors.accent.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("No memories yet", style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Capture your first memory above — your stories will appear here.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FilteredEmptyState(mood: TripJournalMood?, onClearFilter: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = JetTheme.spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                mood?.let { moodIcon(it) } ?: Icons.Outlined.EditNote,
                contentDescription = null,
                tint = colors.accent.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(JetTheme.spacing.small))
            Text(
                "No ${mood?.label?.lowercase() ?: ""} entries",
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(JetTheme.spacing.xsmall))
            Text(
                "Nothing matches this mood yet.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(JetTheme.spacing.small))
            JournalChip(label = "Show all", selected = false, onClick = onClearFilter)
        }
    }
}

private fun moodIcon(mood: TripJournalMood): ImageVector = when (mood) {
    TripJournalMood.JOYFUL -> Icons.Filled.SentimentVerySatisfied
    TripJournalMood.REFLECTIVE -> Icons.Filled.Lightbulb
    TripJournalMood.ADVENTUROUS -> Icons.Filled.Explore
    TripJournalMood.RELAXED -> Icons.Filled.Spa
}

@Preview(showBackground = true, name = "Trip Journal – populated")
@Composable
private fun TravelJournalContentPreview() {
    JetSetterTheme {
        TravelJournalContent(
            state = TraveljournalUiState(
                entries = listOf(
                    TripJournalEntry(
                        date = "Jun 22, 2026",
                        title = "Sunrise over the caldera",
                        body = "Woke at 4:45 to climb above Oia before the crowds. The whole bay " +
                            "turned rose-gold and the windmills were still silhouettes.",
                        location = "Oia, Santorini",
                        mood = TripJournalMood.REFLECTIVE,
                    ),
                    TripJournalEntry(
                        date = "Jun 17, 2026",
                        title = "Hiking the fjord ridge",
                        body = "Eight miles up to the overlook with a cold drizzle the whole way.",
                        location = "Bergen, Norway",
                        mood = TripJournalMood.ADVENTUROUS,
                    ),
                ),
                draftTitle = "Gelato by the canal",
                draftLocation = "Venice, Italy",
                draftMood = TripJournalMood.JOYFUL,
            ),
            onDraftTitleChange = {},
            onDraftBodyChange = {},
            onDraftLocationChange = {},
            onDraftMoodChange = {},
            onMoodFilterChange = {},
            onAddEntry = {},
            onDeleteEntry = {},
        )
    }
}

@Preview(showBackground = true, name = "Trip Journal – empty")
@Composable
private fun TravelJournalEmptyPreview() {
    JetSetterTheme {
        TravelJournalContent(
            state = TraveljournalUiState(entries = emptyList()),
            onDraftTitleChange = {},
            onDraftBodyChange = {},
            onDraftLocationChange = {},
            onDraftMoodChange = {},
            onMoodFilterChange = {},
            onAddEntry = {},
            onDeleteEntry = {},
        )
    }
}
