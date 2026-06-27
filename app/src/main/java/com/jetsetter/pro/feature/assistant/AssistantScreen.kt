package com.jetsetter.pro.feature.assistant

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * Stateful entry point: owns the [AssistantViewModel], collects its [AssistantUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [AssistantContent].
 */
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    AssistantContent(
        state = state,
        onSelectPrompt = viewModel::selectPrompt,
        onQueryChange = viewModel::onQueryChange,
        onClearResponse = viewModel::clearResponse,
        onClearRecents = viewModel::clearRecents,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun AssistantContent(
    state: AssistantUiState,
    onSelectPrompt: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearResponse: () -> Unit,
    onClearRecents: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time fade + slight slide-up on first composition. Purely visual: applied via a
    // graphicsLayer so it never affects scroll measurement, and settles to a no-op once shown.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "assistantEntrance",
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
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Assistant", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

        IntroCard()

        SearchField(query = state.query, onQueryChange = onQueryChange)

        when {
            state.isResponding -> ThinkingCard(
                title = state.prompts.firstOrNull { it.id == state.selectedPromptId }?.title,
            )

            state.response != null -> ResponseCard(
                response = state.response,
                prompts = state.prompts,
                onSelectPrompt = onSelectPrompt,
                onClear = onClearResponse,
            )
        }

        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier
                        .padding(top = spacing.large)
                        .align(Alignment.CenterHorizontally),
                )
            }

            else -> {
                if (state.query.isBlank() && state.recentPrompts.isNotEmpty()) {
                    RecentSection(
                        recents = state.recentPrompts,
                        onSelectPrompt = onSelectPrompt,
                        onClear = onClearRecents,
                    )
                }

                val filtered = state.filteredPrompts
                if (filtered.isEmpty()) {
                    NoResultsCard(query = state.query, onClear = { onQueryChange("") })
                } else {
                    AssistantPromptCategory.entries.forEach { category ->
                        val items = filtered.filter { it.category == category }
                        if (items.isNotEmpty()) {
                            PromptSection(
                                category = category,
                                prompts = items,
                                selectedId = state.selectedPromptId,
                                onSelectPrompt = onSelectPrompt,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        AccentTag(text = "Quick Actions", icon = Icons.Filled.AutoAwesome)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap a quick action for an instant, trip-aware answer.",
            style = typography.bodyMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "For free-form questions, chat with IRIS instead.",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        PremiumTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search quick actions",
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = colors.textSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .semantics { role = Role.Button }
                    .size(22.dp)
                    .padding(2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSection(
    recents: List<AssistantPrompt>,
    onSelectPrompt: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT", style = typography.caption, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "Clear",
                style = typography.label,
                color = colors.accent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
        ) {
            recents.forEach { prompt ->
                RecentChip(prompt = prompt, onClick = { onSelectPrompt(prompt.id) })
            }
        }
    }
}

@Composable
private fun RecentChip(prompt: AssistantPrompt, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.12f))
            .border(0.5.dp, colors.accent.copy(alpha = 0.30f), CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(12.dp),
        )
        Text(prompt.title, style = JetTheme.typography.label, color = colors.accent)
    }
}

@Composable
private fun PromptSection(
    category: AssistantPromptCategory,
    prompts: List<AssistantPrompt>,
    selectedId: String?,
    onSelectPrompt: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            category.label.uppercase(),
            style = JetTheme.typography.caption,
            color = colors.textSecondary,
        )
        prompts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                pair.forEach { prompt ->
                    PromptCard(
                        prompt = prompt,
                        selected = prompt.id == selectedId,
                        onClick = { onSelectPrompt(prompt.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    prompt: AssistantPrompt,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    JetCard(
        modifier = modifier
            .height(150.dp)
            .clip(shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .then(if (selected) Modifier.border(1.dp, colors.accent, shape) else Modifier),
    ) {
        IconBadge(icon = prompt.icon, highlighted = selected)
        Spacer(Modifier.height(10.dp))
        Text(
            prompt.title,
            style = typography.cardTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            prompt.subtitle,
            style = typography.caption,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconBadge(icon: ImageVector, highlighted: Boolean) {
    val colors = JetTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else colors.accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) Color.White else colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ThinkingCard(title: String?) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (title != null) "Looking into “$title”…" else "Thinking…",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResponseCard(
    response: AssistantResponse,
    prompts: List<AssistantPrompt>,
    onSelectPrompt: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentTag(text = "Assistant", icon = Icons.Filled.AutoAwesome)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss answer",
                tint = colors.textSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .semantics { role = Role.Button }
                    .size(22.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(response.promptTitle, style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(response.body, style = typography.bodyMedium, color = colors.textSecondary)

        if (response.metrics.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
            ) {
                response.metrics.forEach { MetricChip(it) }
            }
        }

        val followUps = response.followUpIds.mapNotNull { id -> prompts.firstOrNull { it.id == id } }
        if (followUps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("ASK NEXT", style = typography.caption, color = colors.textSecondary)
            Spacer(Modifier.height(JetTheme.spacing.small))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
            ) {
                followUps.forEach { followUp ->
                    FollowUpChip(text = followUp.title, onClick = { onSelectPrompt(followUp.id) })
                }
            }
        }
    }
}

@Composable
private fun MetricChip(metric: AssistantMetric) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val valueColor = when (metric.tone) {
        AssistantMetricTone.POSITIVE -> colors.success
        AssistantMetricTone.WARNING -> colors.warning
        AssistantMetricTone.NEUTRAL -> colors.textPrimary
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(0.5.dp, colors.separator, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(metric.label.uppercase(), style = typography.caption, color = colors.textSecondary)
        Text(metric.value, style = typography.label, color = valueColor)
    }
}

@Composable
private fun FollowUpChip(text: String, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.12f))
            .border(0.5.dp, colors.accent.copy(alpha = 0.30f), CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = JetTheme.typography.label, color = colors.accent)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun NoResultsCard(query: String, onClear: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Filled.SearchOff,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(JetTheme.spacing.small))
        Text("No matches", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing matches “${query.trim()}”. Try a different word, or clear the search.",
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.12f))
                .border(0.5.dp, colors.accent.copy(alpha = 0.30f), CircleShape)
                .clickable { onClear() }
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(12.dp),
            )
            Text("Clear search", style = typography.label, color = colors.accent)
        }
    }
}

@Preview(showBackground = true, name = "Assistant – with answer")
@Composable
private fun AssistantContentPreview() {
    JetSetterTheme {
        val prompts = listOf(
            AssistantPrompt(
                id = "summary",
                title = "Summarize my trip",
                subtitle = "3-day Atlanta board meeting",
                category = AssistantPromptCategory.TRIP,
                icon = AssistantIcons.summary,
                followUpIds = listOf("gate"),
            ),
            AssistantPrompt(
                id = "packing",
                title = "What should I pack?",
                subtitle = "Weather-aware packing list",
                category = AssistantPromptCategory.PACKING,
                icon = AssistantIcons.packing,
            ),
            AssistantPrompt(
                id = "gate",
                title = "Find my gate",
                subtitle = "Departure gate & boarding",
                category = AssistantPromptCategory.AIRPORT,
                icon = AssistantIcons.gate,
            ),
            AssistantPrompt(
                id = "expenses",
                title = "Track my expenses",
                subtitle = "Trip spend so far",
                category = AssistantPromptCategory.MONEY,
                icon = AssistantIcons.expenses,
            ),
        )
        AssistantContent(
            state = AssistantUiState(
                prompts = prompts,
                recentIds = listOf("gate"),
                selectedPromptId = "expenses",
                response = AssistantResponse(
                    promptId = "expenses",
                    promptTitle = "Track my expenses",
                    body = "You're at \$1,812.75 across 4 items this trip, led by the Delta airfare " +
                        "(\$1,290.00). That leaves \$687.25 of your \$2,500.00 budget.",
                    metrics = listOf(
                        AssistantMetric("Spent", "\$1,812.75"),
                        AssistantMetric("Budget", "\$2,500.00"),
                        AssistantMetric("Remaining", "\$687.25", AssistantMetricTone.POSITIVE),
                    ),
                    followUpIds = listOf("summary"),
                ),
            ),
            onSelectPrompt = {},
            onQueryChange = {},
            onClearResponse = {},
            onClearRecents = {},
        )
    }
}
