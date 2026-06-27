package com.jetsetter.pro.feature.travelwallet

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
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
 * Stateful entry point: owns the [TravelWalletViewModel], collects its [TravelWalletUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [TravelWalletContent].
 */
@Composable
fun TravelWalletScreen(viewModel: TravelWalletViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    TravelWalletContent(
        state = state,
        onToggleFavorite = viewModel::toggleFavorite,
        onDeletePass = viewModel::deletePass,
        onSelectFilter = viewModel::selectFilter,
        onShowAddSheet = viewModel::showAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onAddPass = viewModel::addPass,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TravelWalletContent(
    state: TravelWalletUiState,
    onToggleFavorite: (String) -> Unit,
    onDeletePass: (String) -> Unit,
    onSelectFilter: (TravelWalletPassType?) -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddPass: (TravelWalletPassType, String, String, String, String, String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: a quick fade + slight rise on first composition (~250ms). Applied to the
    // layer so it doesn't interfere with scrolling, and the solid background stays put behind it.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "walletEntrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 16.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Travel Wallet", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

        WalletSummaryCard(
            passCount = state.passes.size,
            pinnedCount = state.pinnedCount,
            typeCount = state.typeCount,
        )

        AddToWalletButton(onClick = onShowAddSheet)

        if (state.passes.isNotEmpty()) {
            TypeFilterRow(state = state, onSelectFilter = onSelectFilter)
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

            state.passes.isEmpty() -> EmptyWalletCard()

            state.isFilteredEmpty -> FilteredEmptyCard(
                type = state.selectedFilter,
                onClear = { onSelectFilter(null) },
            )

            else -> state.visiblePasses.forEach { pass ->
                PassCard(
                    pass = pass,
                    onToggleFavorite = { onToggleFavorite(pass.id) },
                    onDelete = { onDeletePass(pass.id) },
                )
            }
        }
    }

    if (state.showAddSheet) {
        AddPassSheet(
            isSaving = state.isSaving,
            onDismiss = onDismissAddSheet,
            onConfirm = onAddPass,
        )
    }
}

/** Distinct accent per pass type so chips and cards read at a glance — all from theme tokens. */
@Composable
private fun typeColor(type: TravelWalletPassType): Color {
    val colors = JetTheme.colors
    return when (type) {
        TravelWalletPassType.BOARDING_PASS -> colors.accent
        TravelWalletPassType.HOTEL -> colors.blue
        TravelWalletPassType.RENTAL_CAR -> colors.success
        TravelWalletPassType.EVENT_TICKET -> colors.warning
        TravelWalletPassType.INSURANCE -> colors.danger
    }
}

@Composable
private fun WalletSummaryCard(passCount: Int, pinnedCount: Int, typeCount: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("IN YOUR WALLET", style = typography.caption, color = colors.textSecondary)
            if (pinnedCount > 0) {
                AccentTag(text = "$pinnedCount pinned", icon = Icons.Filled.Star)
            }
        }
        Spacer(Modifier.height(JetTheme.spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.large),
            verticalAlignment = Alignment.Bottom,
        ) {
            SummaryMetric(value = "$passCount", label = "passes & docs", color = colors.textPrimary)
            SummaryMetric(value = "$typeCount", label = if (typeCount == 1) "category" else "categories", color = colors.accent)
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, color: Color) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column {
        Text(value, style = typography.metric, color = color)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterRow(
    state: TravelWalletUiState,
    onSelectFilter: (TravelWalletPassType?) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        WalletChip(
            label = "All",
            icon = null,
            color = colors.accent,
            selected = state.selectedFilter == null,
            count = state.passes.size,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectFilter(null)
            },
        )
        state.presentTypes.forEach { type ->
            WalletChip(
                label = type.label,
                icon = type.icon,
                color = typeColor(type),
                selected = state.selectedFilter == type,
                count = state.countFor(type),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectFilter(type)
                },
            )
        }
    }
    state.selectedFilter?.let { type ->
        Spacer(Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Showing ${state.visiblePasses.size} ${type.label.lowercase()} of ${state.passes.size}",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Text(
                "Clear",
                style = typography.label,
                color = colors.accent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .semantics { role = Role.Button }
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectFilter(null)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WalletChip(
    label: String,
    icon: ImageVector?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) color.copy(alpha = 0.55f) else colors.separator,
                shape = CircleShape,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) color else colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(label, style = typography.label, color = if (selected) color else colors.textPrimary)
        if (count != null) {
            Text(
                count.toString(),
                style = typography.label,
                color = if (selected) color else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AddToWalletButton(onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add to wallet", style = JetTheme.typography.label, color = Color.White)
    }
}

@Composable
private fun PassCard(
    pass: TravelWalletItem,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletChip(
                label = pass.type.label,
                icon = pass.type.icon,
                color = typeColor(pass.type),
                selected = true,
                onClick = {},
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (pass.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (pass.isFavorite) "Unpin pass" else "Pin pass",
                    tint = if (pass.isFavorite) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${pass.title}",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(pass.title, style = typography.cardTitle, color = colors.textPrimary)
        Text(pass.subtitle, style = typography.caption, color = colors.textSecondary)

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    pass.keyDetailLabel.uppercase(),
                    style = typography.caption,
                    color = colors.textSecondary,
                )
                Text(pass.keyDetailValue, style = typography.bodyMedium, color = colors.textPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("DATE", style = typography.caption, color = colors.textSecondary)
                Text(pass.date, style = typography.bodyMedium, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun EmptyWalletCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(JetTheme.spacing.small))
            Text("Your wallet is empty", style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap \"Add to wallet\" to store boarding passes, hotel and car reservations, tickets, and insurance.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FilteredEmptyCard(type: TravelWalletPassType?, onClear: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(JetTheme.spacing.small))
            Text(
                "No ${type?.label?.lowercase() ?: "matching"} passes",
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Clear the filter to see everything in your wallet.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(JetTheme.spacing.small))
            Text(
                "Show all passes",
                style = typography.label,
                color = colors.accent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .semantics { role = Role.Button }
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClear()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * "Add to wallet" modal bottom sheet. Inputs are local, transient draft state (kept across config
 * changes via [rememberSaveable]); the parent only tracks whether the sheet is shown and the
 * [isSaving] flag. Picking a type updates the suggested detail label so the form stays self-
 * consistent. On confirm the raw values are handed up to the ViewModel, which trims and persists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPassSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TravelWalletPassType, String, String, String, String, String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by rememberSaveable { mutableStateOf(TravelWalletPassType.BOARDING_PASS) }
    var title by rememberSaveable { mutableStateOf("") }
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var detailLabel by rememberSaveable { mutableStateOf(TravelWalletPassType.BOARDING_PASS.defaultDetailLabel) }
    var detailValue by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }

    val titleValid = title.isNotBlank()

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
            Text("Add to wallet", style = typography.pageTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Type")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                TravelWalletPassType.entries.forEach { type ->
                    WalletChip(
                        label = type.label,
                        icon = type.icon,
                        color = typeColor(type),
                        selected = selectedType == type,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            // Keep the suggested detail label in sync unless the user customised it.
                            if (detailLabel.isBlank() || detailLabel == selectedType.defaultDetailLabel) {
                                detailLabel = type.defaultDetailLabel
                            }
                            selectedType = type
                        },
                    )
                }
            }

            FieldLabel("Title")
            PremiumTextField(
                value = title,
                onValueChange = { title = it; titleTouched = true },
                placeholder = "DL 1423 · LAS → ATL",
                modifier = Modifier.fillMaxWidth(),
            )
            if (titleTouched && !titleValid) {
                Text("Title is required", style = typography.caption, color = colors.danger)
            }

            FieldLabel("Details")
            PremiumTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                placeholder = "Delta Air Lines · Main Cabin",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel(selectedType.defaultDetailLabel)
                    PremiumTextField(
                        value = detailValue,
                        onValueChange = { detailValue = it },
                        placeholder = selectedType.detailHint,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Date")
                    PremiumTextField(
                        value = date,
                        onValueChange = { date = it },
                        placeholder = "Jul 14, 2026 · 7:00 AM",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    if (titleValid) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm(selectedType, title, subtitle, detailLabel, detailValue, date)
                    }
                },
                enabled = titleValid && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to wallet", style = typography.cardTitle, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Preview(showBackground = true, name = "Travel Wallet – populated")
@Composable
private fun TravelWalletContentPreview() {
    JetSetterTheme {
        TravelWalletContent(
            state = TravelWalletUiState(
                passes = listOf(
                    TravelWalletItem(
                        id = "p1",
                        type = TravelWalletPassType.BOARDING_PASS,
                        title = "DL 1423 · LAS → ATL",
                        subtitle = "Delta Air Lines · Main Cabin",
                        keyDetailLabel = "Seat",
                        keyDetailValue = "12A · Zone 1",
                        date = "Jul 14 · 7:00 AM",
                        isFavorite = true,
                    ),
                    TravelWalletItem(
                        id = "p2",
                        type = TravelWalletPassType.HOTEL,
                        title = "Four Seasons Atlanta",
                        subtitle = "75 14th St NE · King Suite",
                        keyDetailLabel = "Confirmation",
                        keyDetailValue = "FS-8842193",
                        date = "Jul 14 – 16",
                    ),
                ),
            ),
            onToggleFavorite = {},
            onDeletePass = {},
            onSelectFilter = {},
            onShowAddSheet = {},
            onDismissAddSheet = {},
            onAddPass = { _, _, _, _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Travel Wallet – empty")
@Composable
private fun TravelWalletEmptyPreview() {
    JetSetterTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            TravelWalletContent(
                state = TravelWalletUiState(passes = emptyList()),
                onToggleFavorite = {},
                onDeletePass = {},
                onSelectFilter = {},
                onShowAddSheet = {},
                onDismissAddSheet = {},
                onAddPass = { _, _, _, _, _, _ -> },
            )
        }
    }
}
