@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.jetsetter.pro.feature.luggagetracker

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Stateful entry point: owns the [LuggagetrackerViewModel], collects its
 * [LuggagetrackerUiState] lifecycle-aware, and forwards events. Holds no logic —
 * see [LuggageTrackerContent].
 */
@Composable
fun LuggageTrackerScreen(viewModel: LuggagetrackerViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LuggageTrackerContent(
        state = state,
        onSelectBag = viewModel::onSelectBag,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onStartRename = viewModel::onStartRename,
        onDismissRename = viewModel::onDismissRename,
        onSaveNickname = viewModel::onSaveNickname,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun LuggageTrackerContent(
    state: LuggagetrackerUiState,
    onSelectBag: (LuggageTrackerBag) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onStartRename: (LuggageTrackerBag) -> Unit,
    onDismissRename: () -> Unit,
    onSaveNickname: (tagId: String, nickname: String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + gentle slide-up on first composition. Purely visual; the
    // flag never resets, so it runs once and leaves scrolling untouched afterwards.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "luggageEntrance",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val selected = state.selectedBag
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = entranceProgress
                        translationY = (1f - entranceProgress) * 16.dp.toPx()
                    },
                contentPadding = PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = spacing.large,
                    bottom = spacing.xlarge,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item { Header(state) }

                state.errorMessage?.let { message ->
                    item { NoticeCard(message) }
                }

                if (state.statusCounts.isNotEmpty()) {
                    item { StatusSummary(state.statusCounts) }
                }

                if (state.bags.isNotEmpty()) {
                    item {
                        SearchField(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onClear = onClearQuery,
                        )
                    }
                }

                when {
                    state.bags.isEmpty() -> item {
                        EmptyCard(
                            title = "No bags registered",
                            body = "Bags appear here once a tag is scanned at check-in.",
                        )
                    }

                    state.hasNoSearchResults -> item {
                        EmptyCard(
                            title = "No matches",
                            body = "Nothing matches “${state.query.trim()}”. Try a different tag or name.",
                            icon = Icons.Filled.Search,
                        )
                    }

                    else -> {
                        item {
                            SectionLabel("REGISTERED BAGS · ${state.visibleBags.size}")
                        }
                        items(state.visibleBags, key = { it.tagId }) { bag ->
                            BagCard(
                                bag = bag,
                                isSelected = bag.tagId == selected?.tagId,
                                nowMillis = state.nowMillis,
                                onClick = { onSelectBag(bag) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                if (selected != null) {
                    item { SectionLabel("SCAN HISTORY · ${selected.tagId}") }
                    item {
                        ScanHistoryCard(
                            bag = selected,
                            nowMillis = state.nowMillis,
                            onRename = { onStartRename(selected) },
                        )
                    }
                }
            }
        }
    }

    val renaming = state.renamingBag
    if (renaming != null) {
        RenameBagSheet(
            bag = renaming,
            onDismiss = onDismissRename,
            onSave = onSaveNickname,
        )
    }
}

@Composable
private fun Header(state: LuggagetrackerUiState) {
    val colors = JetTheme.colors
    Column {
        Text("Luggage Tracker", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
        val subtitle = buildString {
            append("${state.bags.size} registered")
            state.mostRecentScanMillis?.let { append(" · last seen ${relativeTime(it, state.nowMillis)}") }
        }
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = JetTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun StatusSummary(counts: List<Pair<LuggageTrackerStatus, Int>>) {
    val spacing = JetTheme.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        counts.forEach { (status, count) ->
            StatusChip(status = status, count = count)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
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
            modifier = Modifier.size(18.dp),
        )
        PremiumTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search tag, bag, or name",
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BagCard(
    bag: LuggageTrackerBag,
    isSelected: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val statusColor = statusColor(bag.status)
    val haptics = LocalHapticFeedback.current

    JetCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isSelected) "Selected" else "Not selected"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Luggage,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bag.displayName,
                    style = typography.cardTitle,
                    color = if (isSelected) colors.accent else colors.textPrimary,
                )
                Text(bag.tagId, style = typography.caption, color = colors.textSecondary)
            }
            StatusChip(status = bag.status)
        }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(10.dp))

        val lastScan = bag.lastScan
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (lastScan != null) {
                    Text(lastScan.location, style = typography.bodyMedium, color = colors.textPrimary)
                    Text(
                        "Last seen · ${lastScan.clockLabel()} · ${lastScan.relativeLabel(nowMillis)}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                } else {
                    Text("No scans yet", style = typography.bodyMedium, color = colors.textSecondary)
                }
            }
            Text(
                "${bag.scanCount} scans",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }

        if (isSelected) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text("Viewing scan history below", style = typography.label, color = colors.accent)
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    bag: LuggageTrackerBag,
    nowMillis: Long,
    onRename: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bag.displayName, style = typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(bag.tagId, style = typography.caption, color = colors.textSecondary)
            }
            IconButton(onClick = onRename, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Rename ${bag.tagId}",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(status = bag.status)
            AccentTag(text = "${bag.scanCount} scans", icon = Icons.Filled.Place)
        }

        bag.lastScan?.let { lastScan ->
            Spacer(Modifier.height(12.dp))
            Text("LAST SEEN", style = typography.label, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(lastScan.location, style = typography.bodyMedium, color = colors.textPrimary)
            Text(
                "${lastScan.clockLabel()} · ${lastScan.relativeLabel(nowMillis)}",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))
        Divider()
        Spacer(Modifier.height(14.dp))

        bag.scanHistory.forEachIndexed { index, scan ->
            ScanRow(
                scan = scan,
                isLatest = index == 0,
                isLast = index == bag.scanHistory.lastIndex,
                nowMillis = nowMillis,
            )
        }
    }
}

@Composable
private fun ScanRow(
    scan: LuggageTrackerScan,
    isLatest: Boolean,
    isLast: Boolean,
    nowMillis: Long,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Timeline rail: a dot for the scan plus a connector down to the next one.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(if (isLatest) 11.dp else 9.dp)
                    .clip(CircleShape)
                    .background(if (isLatest) colors.accent else colors.separator),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .background(colors.separator),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
        ) {
            Text(scan.location, style = typography.bodyMedium, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(scan.detail, style = typography.caption, color = colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                scan.clockLabel(),
                style = typography.caption,
                color = if (isLatest) colors.accent else colors.textPrimary,
            )
            Text(scan.relativeLabel(nowMillis), style = typography.caption, color = colors.textSecondary)
        }
    }
}

/**
 * Rename sheet. The draft is local, transient state seeded from the bag's current nickname;
 * the parent only tracks which bag is being renamed. Validation lives here: an over-long name
 * disables Save, and a blank name clears the nickname (falling back to the description).
 */
@Composable
private fun RenameBagSheet(
    bag: LuggageTrackerBag,
    onDismiss: () -> Unit,
    onSave: (tagId: String, nickname: String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by rememberSaveable(bag.tagId) { mutableStateOf(bag.nickname.orEmpty()) }
    val trimmed = name.trim()
    val maxLength = LuggagetrackerViewModel.MAX_NICKNAME_LENGTH
    val tooLong = trimmed.length > maxLength

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.separator) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text("Name this bag", style = typography.pageTitle, color = colors.textPrimary)
            Text(
                "Give ${bag.tagId} a nickname so it's easy to spot.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(spacing.xsmall))

            Text("Nickname", style = typography.label, color = colors.textSecondary)
            PremiumTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = bag.description,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (tooLong) "Keep it under $maxLength characters." else "${trimmed.length}/$maxLength",
                style = typography.caption,
                color = if (tooLong) colors.danger else colors.textSecondary,
            )

            Spacer(Modifier.height(spacing.xsmall))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave(bag.tagId, name)
                },
                enabled = !tooLong,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (trimmed.isEmpty()) "Use default name" else "Save name",
                    style = typography.cardTitle,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: LuggageTrackerStatus, count: Int? = null) {
    val color = statusColor(status)
    val text = if (count != null) "${status.label} · $count" else status.label
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = JetTheme.typography.caption, color = JetTheme.colors.textSecondary)
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(JetTheme.colors.separator),
    )
}

@Composable
private fun EmptyCard(
    title: String,
    body: String,
    icon: ImageVector = Icons.Filled.Luggage,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(title, style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(body, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun NoticeCard(message: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun statusColor(status: LuggageTrackerStatus): Color {
    val colors = JetTheme.colors
    return when (status) {
        LuggageTrackerStatus.CHECKED_IN -> colors.blue
        LuggageTrackerStatus.IN_TRANSIT -> colors.accent
        LuggageTrackerStatus.ARRIVED -> colors.success
        LuggageTrackerStatus.DELAYED -> colors.danger
    }
}

private fun statusIcon(status: LuggageTrackerStatus): ImageVector = when (status) {
    LuggageTrackerStatus.CHECKED_IN -> Icons.Filled.Luggage
    LuggageTrackerStatus.IN_TRANSIT -> Icons.Filled.LocalShipping
    LuggageTrackerStatus.ARRIVED -> Icons.Filled.CheckCircle
    LuggageTrackerStatus.DELAYED -> Icons.Filled.WarningAmber
}

private const val MINUTE_MILLIS = 60_000L

@Preview(showBackground = true, name = "Luggage Tracker – populated")
@Composable
private fun LuggageTrackerContentPreview() {
    val now = 1_700_000_000_000L
    JetSetterTheme {
        LuggageTrackerContent(
            state = LuggagetrackerUiState(
                nowMillis = now,
                selectedTagId = "DL 0042 1788",
                bags = listOf(
                    LuggageTrackerBag(
                        tagId = "DL 0042 1788",
                        description = "Black Tumi roller · 26\"",
                        nickname = "My carry-on",
                        status = LuggageTrackerStatus.IN_TRANSIT,
                        scanHistory = listOf(
                            LuggageTrackerScan("ATL · Concourse B ramp", "Loaded onto DL 1423 · ULD AKE2291", now - 18 * MINUTE_MILLIS),
                            LuggageTrackerScan("ATL · Sortation T3", "Routed to gate C22", now - 52 * MINUTE_MILLIS),
                            LuggageTrackerScan("LAS · Check-in desk 14", "Tag printed and accepted", now - 455 * MINUTE_MILLIS),
                        ),
                    ),
                    LuggageTrackerBag(
                        tagId = "DL 0042 1791",
                        description = "Olive duffel · carry-on",
                        status = LuggageTrackerStatus.ARRIVED,
                        scanHistory = listOf(
                            LuggageTrackerScan("ATL · Carousel 11", "Delivered to claim — ready for pickup", now - 3 * MINUTE_MILLIS),
                        ),
                    ),
                    LuggageTrackerBag(
                        tagId = "DL 0042 1790",
                        description = "Garment bag · navy",
                        status = LuggageTrackerStatus.DELAYED,
                        scanHistory = listOf(
                            LuggageTrackerScan("SLC · Mishandled office", "Missed connection — rebooking on DL 2207", now - 78 * MINUTE_MILLIS),
                        ),
                    ),
                ),
            ),
            onSelectBag = {},
            onQueryChange = {},
            onClearQuery = {},
            onStartRename = {},
            onDismissRename = {},
            onSaveNickname = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Luggage Tracker – empty")
@Composable
private fun LuggageTrackerEmptyPreview() {
    JetSetterTheme {
        LuggageTrackerContent(
            state = LuggagetrackerUiState(bags = emptyList(), nowMillis = 1_700_000_000_000L),
            onSelectBag = {},
            onQueryChange = {},
            onClearQuery = {},
            onStartRename = {},
            onDismissRename = {},
            onSaveNickname = { _, _ -> },
        )
    }
}
