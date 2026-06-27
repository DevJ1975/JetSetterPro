package com.jetsetter.pro.feature.loyaltyvault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import java.util.Locale

/** Privacy placeholder shown wherever a number is hidden. */
private const val MASK = "••••••"

/**
 * Stateful entry point: owns the [LoyaltyvaultViewModel], collects its [LoyaltyvaultUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [LoyaltyVaultContent].
 */
@Composable
fun LoyaltyVaultScreen(viewModel: LoyaltyvaultViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LoyaltyVaultContent(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onCycleSort = viewModel::cycleSort,
        onToggleReveal = viewModel::toggleReveal,
        onTogglePrivacy = viewModel::togglePrivacy,
        onTogglePin = viewModel::togglePin,
        onShowAddSheet = viewModel::showAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onAddAccount = viewModel::addAccount,
        onDeleteAccount = viewModel::deleteAccount,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun LoyaltyVaultContent(
    state: LoyaltyvaultUiState,
    onSelectFilter: (LoyaltyVaultFilter) -> Unit,
    onCycleSort: () -> Unit,
    onToggleReveal: (String) -> Unit,
    onTogglePrivacy: () -> Unit,
    onTogglePin: (String) -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddAccount: (LoyaltyVaultProgramType, String, String, String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    // One-time entrance: fade + gentle slide-in on first composition. Purely visual; runs once and
    // leaves scrolling untouched (a graphics layer over the list, not a wrapping container).
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

            else -> {
                val visible = state.visibleAccounts
                LazyColumn(
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
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    item {
                        Header(hideBalances = state.hideBalances, onTogglePrivacy = onTogglePrivacy)
                    }
                    item {
                        SummaryCard(summary = state.summary, hideBalances = state.hideBalances)
                    }
                    item {
                        ControlsRow(
                            filter = state.filter,
                            sort = state.sort,
                            onSelectFilter = onSelectFilter,
                            onCycleSort = onCycleSort,
                        )
                    }
                    when {
                        state.isEmptyVault -> item { EmptyVaultCard() }
                        visible.isEmpty() -> item { NoMatchesCard(filter = state.filter) }
                        else -> items(visible, key = { it.id }) { account ->
                            AccountCard(
                                account = account,
                                revealed = state.revealedIds.contains(account.id),
                                hideBalances = state.hideBalances,
                                onToggleReveal = { onToggleReveal(account.id) },
                                onTogglePin = { onTogglePin(account.id) },
                                onDelete = { onDeleteAccount(account.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onShowAddSheet()
            },
            containerColor = colors.accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.medium),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add loyalty program")
        }
    }

    if (state.showAddSheet) {
        AddAccountSheet(
            isSaving = state.isSaving,
            onDismiss = onDismissAddSheet,
            onConfirm = onAddAccount,
        )
    }
}

@Composable
private fun Header(hideBalances: Boolean, onTogglePrivacy: () -> Unit) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Loyalty Vault", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
        PrivacyToggle(hideBalances = hideBalances, onClick = onTogglePrivacy)
    }
}

@Composable
private fun PrivacyToggle(hideBalances: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val tint = if (hideBalances) colors.accent else colors.textSecondary
    val bg = if (hideBalances) colors.accent.copy(alpha = 0.15f) else colors.surface
    val borderColor = if (hideBalances) colors.accent.copy(alpha = 0.40f) else colors.separator
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            .border(0.6.dp, borderColor, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "Hide balances"
                stateDescription = if (hideBalances) "On" else "Off"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (hideBalances) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(if (hideBalances) "Hidden" else "Visible", style = JetTheme.typography.label, color = tint)
    }
}

@Composable
private fun SummaryCard(summary: LoyaltyVaultSummary, hideBalances: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ESTIMATED VALUE · ${summary.scopeLabel.uppercase(Locale.US)}",
                style = typography.caption,
                color = colors.textSecondary,
            )
            AccentTag(
                text = if (summary.programCount == 1) "1 program" else "${summary.programCount} programs",
                icon = Icons.Filled.WorkspacePremium,
            )
        }
        Spacer(Modifier.height(spacing.xsmall))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (hideBalances) "$$MASK" else formatUsd(summary.totalValueUsd),
                style = typography.metric,
                color = colors.textPrimary,
            )
            Icon(
                Icons.Filled.TrendingUp,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(20.dp).padding(bottom = 6.dp),
            )
        }

        if (summary.totalValueUsd > 0L && !hideBalances) {
            Spacer(Modifier.height(spacing.medium))
            ValueBreakdownBar(airlineShare = summary.airlineValueShare)
            Spacer(Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                LegendDot(color = colors.blue, label = "Air ${formatUsd(summary.airlineValueUsd)}")
                LegendDot(color = colors.accent, label = "Hotel ${formatUsd(summary.hotelValueUsd)}")
            }
        }

        Spacer(Modifier.height(spacing.medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SummaryStat(
                label = "Airline miles",
                value = if (hideBalances) MASK else formatPoints(summary.airlineMiles),
                tint = colors.blue,
                modifier = Modifier.weight(1f),
            )
            SummaryStat(
                label = "Hotel points",
                value = if (hideBalances) MASK else formatPoints(summary.hotelPoints),
                tint = colors.accent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ValueBreakdownBar(airlineShare: Float) {
    val colors = JetTheme.colors
    val hotelShare = 1f - airlineShare
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(colors.separator),
    ) {
        if (airlineShare > 0f) {
            Box(Modifier.weight(airlineShare).fillMaxHeight().background(colors.blue))
        }
        if (hotelShare > 0f) {
            Box(Modifier.weight(hotelShare).fillMaxHeight().background(colors.accent))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = JetTheme.typography.caption, color = JetTheme.colors.textSecondary)
    }
}

@Composable
private fun SummaryStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(modifier = modifier) {
        Text(value, style = typography.cardTitle, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ControlsRow(
    filter: LoyaltyVaultFilter,
    sort: LoyaltyVaultSort,
    onSelectFilter: (LoyaltyVaultFilter) -> Unit,
    onCycleSort: () -> Unit,
) {
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            LoyaltyVaultFilter.entries.forEach { f ->
                SelectChip(label = f.label, selected = f == filter, onClick = { onSelectFilter(f) })
            }
        }
        SortChip(sort = sort, onClick = onCycleSort)
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface
    val borderColor = if (selected) colors.accent.copy(alpha = 0.40f) else colors.separator
    val textColor = if (selected) colors.accent else colors.textSecondary
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .border(0.6.dp, borderColor, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        Text(label, style = JetTheme.typography.label, color = textColor)
    }
}

@Composable
private fun SortChip(sort: LoyaltyVaultSort, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(colors.surface)
            .border(0.6.dp, colors.separator, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Sort by ${sort.label}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.SwapVert, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(sort.label, style = JetTheme.typography.label, color = colors.textSecondary)
    }
}

@Composable
private fun AccountCard(
    account: LoyaltyVaultAccount,
    revealed: Boolean,
    hideBalances: Boolean,
    onToggleReveal: () -> Unit,
    onTogglePin: () -> Unit,
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (account.type == LoyaltyVaultProgramType.AIRLINE) Icons.Filled.Flight else Icons.Filled.Hotel,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.programName,
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(account.type.label, style = typography.caption, color = colors.textSecondary)
                }
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTogglePin()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (account.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (account.isPinned) "Unpin ${account.programName}" else "Pin ${account.programName}",
                    tint = if (account.isPinned) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
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
                    contentDescription = "Remove ${account.programName}",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(spacing.small))
        AccentTag(text = account.tier, icon = Icons.Filled.WorkspacePremium)

        Spacer(Modifier.height(spacing.medium))
        Text(account.unitLabel.uppercase(Locale.US), style = typography.caption, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (hideBalances) MASK else formatPoints(account.balance),
                style = typography.metric,
                color = colors.textPrimary,
            )
            if (!hideBalances) {
                Text(
                    account.unitLabel,
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Text(
            if (hideBalances) "Worth $$MASK" else "Worth ${formatUsd(account.estimatedValueUsd)} · ${formatCents(account.centsPerUnit)}/${account.unitLabel.trimEnd('s')}",
            style = typography.caption,
            color = colors.success,
        )

        Spacer(Modifier.height(spacing.medium))
        TierProgress(account = account)

        Spacer(Modifier.height(spacing.medium))
        MembershipRow(
            account = account,
            revealed = revealed,
            hideBalances = hideBalances,
            onToggleReveal = onToggleReveal,
        )
    }
}

@Composable
private fun TierProgress(account: LoyaltyVaultAccount) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    if (account.isTopTier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
            Text("Top tier reached · requalified", style = typography.caption, color = colors.success)
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(fraction = 1f, color = colors.success)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Next: ${account.nextTier}", style = typography.caption, color = colors.textSecondary)
        Text("${(account.tierFraction * 100).toInt()}%", style = typography.caption, color = colors.accent)
    }
    Spacer(Modifier.height(6.dp))
    ProgressTrack(fraction = account.tierFraction, color = colors.accent)
    Spacer(Modifier.height(4.dp))
    Text(
        "${formatPoints(account.tierRemaining.toLong())} ${account.tierUnit} to go",
        style = typography.caption,
        color = colors.textSecondary,
    )
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color) {
    val colors = JetTheme.colors
    // Ease the fill in toward the exact computed fraction (no math change), so the bar grows
    // smoothly on first show instead of snapping to its final width.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "progressFraction",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.separator),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun MembershipRow(
    account: LoyaltyVaultAccount,
    revealed: Boolean,
    hideBalances: Boolean,
    onToggleReveal: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val locked = hideBalances
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (locked) {
                    Modifier
                } else {
                    Modifier
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleReveal()
                        }
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button }
                },
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("MEMBERSHIP", style = typography.caption, color = colors.textSecondary)
            Text(
                when {
                    locked -> maskFull(account.membershipNumber)
                    revealed -> account.membershipNumber
                    else -> maskMembership(account.membershipNumber)
                },
                style = typography.bodyMedium,
                color = colors.textPrimary,
            )
        }
        Icon(
            imageVector = when {
                locked -> Icons.Filled.Lock
                revealed -> Icons.Filled.VisibilityOff
                else -> Icons.Filled.Visibility
            },
            contentDescription = when {
                locked -> "Locked by privacy mode"
                revealed -> "Hide membership number"
                else -> "Reveal membership number"
            },
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyVaultCard() {
    EmptyStateCard(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "Your vault is empty",
        message = "Tap + to add an airline or hotel program. We'll total the points and estimate what they're worth.",
    )
}

@Composable
private fun NoMatchesCard(filter: LoyaltyVaultFilter) {
    EmptyStateCard(
        icon = Icons.Outlined.SearchOff,
        title = "No ${filter.label.lowercase()} yet",
        message = "Switch the filter above, or tap + to add a ${filter.label.lowercase().trimEnd('s')} program.",
    )
}

/** Shared, centered empty/no-match placeholder: accent glyph over a title + supporting line. */
@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
) {
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(
                title,
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                message,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * "Add program" modal bottom sheet. Inputs are local, transient draft state (kept across config
 * changes via [rememberSaveable]); the parent only tracks whether the sheet is shown. On confirm
 * the raw strings are handed up to the ViewModel, which trims, parses and persists them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LoyaltyVaultProgramType, String, String, String, String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    var type by rememberSaveable { mutableStateOf(LoyaltyVaultProgramType.AIRLINE) }
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var tier by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }

    val parsedBalance = balance.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val canSave = name.isNotBlank() && number.isNotBlank() && !isSaving

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
            Text("Add program", style = typography.pageTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Program type")
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                LoyaltyVaultProgramType.entries.forEach { t ->
                    SelectChip(label = t.label, selected = t == type, onClick = { type = t })
                }
            }

            FieldLabel("Program name")
            PremiumTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = if (type == LoyaltyVaultProgramType.AIRLINE) "Delta SkyMiles" else "Marriott Bonvoy",
                modifier = Modifier.fillMaxWidth(),
            )
            if (name.isBlank()) RequiredHint()

            FieldLabel("Membership number")
            PremiumTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = "9087654321",
                modifier = Modifier.fillMaxWidth(),
            )
            if (number.isBlank()) RequiredHint()

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Tier (optional)")
                    PremiumTextField(
                        value = tier,
                        onValueChange = { tier = it },
                        placeholder = "Gold",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Balance")
                    PremiumTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        placeholder = "0",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                "Tracked as ${formatPoints(parsedBalance)} ${type.defaultUnitLabel} · ≈ ${formatUsd(Math.round(parsedBalance * type.defaultCentsPerUnit / 100.0))}",
                style = typography.caption,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(type, name, number, tier, balance)
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isSaving) "Saving…" else "Add to vault", style = typography.cardTitle)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun RequiredHint() {
    Text("Required", style = JetTheme.typography.caption, color = JetTheme.colors.danger)
}

private fun formatPoints(value: Long): String = String.format(Locale.US, "%,d", value)

private fun formatUsd(value: Long): String = "$" + String.format(Locale.US, "%,d", value)

private fun formatCents(centsPerUnit: Double): String =
    String.format(Locale.US, "%.1f¢", centsPerUnit)

private fun maskMembership(number: String): String {
    val cleaned = number.filter { it.isLetterOrDigit() }
    val last4 = cleaned.takeLast(4)
    return "••••$last4"
}

private fun maskFull(number: String): String {
    val len = number.filter { it.isLetterOrDigit() }.length.coerceIn(4, 12)
    return "•".repeat(len)
}

@Preview(showBackground = true, name = "Loyalty Vault – populated")
@Composable
private fun LoyaltyVaultContentPreview() {
    JetSetterTheme {
        LoyaltyVaultContent(
            state = LoyaltyvaultUiState(
                accounts = listOf(
                    LoyaltyVaultAccount(
                        id = "delta",
                        programName = "Delta SkyMiles",
                        membershipNumber = "9087654321",
                        tier = "Platinum Medallion",
                        balance = 248_530,
                        unitLabel = "miles",
                        type = LoyaltyVaultProgramType.AIRLINE,
                        centsPerUnit = 1.2,
                        nextTier = "Diamond Medallion",
                        tierProgress = 14_200,
                        tierTarget = 28_000,
                        tierUnit = "MQDs",
                        isPinned = true,
                    ),
                    LoyaltyVaultAccount(
                        id = "marriott",
                        programName = "Marriott Bonvoy",
                        membershipNumber = "BV559013344",
                        tier = "Titanium Elite",
                        balance = 318_940,
                        unitLabel = "points",
                        type = LoyaltyVaultProgramType.HOTEL,
                        centsPerUnit = 0.84,
                        nextTier = null,
                    ),
                ),
                revealedIds = setOf("delta"),
            ),
            onSelectFilter = {},
            onCycleSort = {},
            onToggleReveal = {},
            onTogglePrivacy = {},
            onTogglePin = {},
            onShowAddSheet = {},
            onDismissAddSheet = {},
            onAddAccount = { _, _, _, _, _ -> },
            onDeleteAccount = {},
        )
    }
}
