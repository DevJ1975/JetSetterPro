package com.jetsetter.pro.feature.documentvault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val documentDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * Stateful entry point: owns the [DocumentvaultViewModel], collects its [DocumentvaultUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [DocumentVaultContent].
 */
@Composable
fun DocumentVaultScreen(viewModel: DocumentvaultViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    DocumentVaultContent(
        state = state,
        onToggleReveal = viewModel::toggleReveal,
        onSelectFilter = viewModel::selectFilter,
        onSelectStatus = viewModel::selectStatus,
        onOpenDocument = viewModel::selectDocument,
        onDismissDetail = viewModel::dismissDetail,
        onShowAddSheet = viewModel::showAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onAddDocument = viewModel::addDocument,
        onDeleteDocument = viewModel::deleteDocument,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun DocumentVaultContent(
    state: DocumentvaultUiState,
    onToggleReveal: (String) -> Unit,
    onSelectFilter: (DocumentVaultType?) -> Unit,
    onSelectStatus: (DocumentVaultStatus?) -> Unit,
    onOpenDocument: (String) -> Unit,
    onDismissDetail: () -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddDocument: (
        title: String,
        type: DocumentVaultType,
        number: String,
        holder: String,
        authority: String,
        expiry: String,
    ) -> Unit,
    onDeleteDocument: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    // One-time entrance: fade + a small upward settle on first composition (~250ms).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "vaultEntrance",
    )
    val slidePx = with(LocalDensity.current) { spacing.medium.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = spacing.medium, vertical = spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    Header()
                    ErrorCard(state.errorMessage)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .graphicsLayer {
                            alpha = entrance
                            translationY = (1f - entrance) * slidePx
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
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            Header()
                            Text(
                                "Keep passports, visas, and health records ready — tap a card for details, or a number to reveal it.",
                                style = JetTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    state.attention?.let { entry ->
                        item { AttentionBanner(entry) }
                    }

                    item {
                        SummaryCard(state = state, onSelectStatus = onSelectStatus)
                    }

                    item {
                        FilterRow(selected = state.selectedFilter, onSelect = onSelectFilter)
                    }

                    if (state.documents.isEmpty()) {
                        item { EmptyState(hasActiveFilter = state.hasActiveFilter) }
                    } else {
                        items(state.documents, key = { it.item.id }) { entry ->
                            DocumentCard(
                                entry = entry,
                                revealed = entry.item.id in state.revealedIds,
                                onToggleReveal = { onToggleReveal(entry.item.id) },
                                onOpen = { onOpenDocument(entry.item.id) },
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
            Icon(Icons.Filled.Add, contentDescription = "Add document")
        }
    }

    if (state.showAddSheet) {
        AddDocumentSheet(onDismiss = onDismissAddSheet, onConfirm = onAddDocument)
    }

    state.selectedDocument?.let { entry ->
        DocumentDetailSheet(
            entry = entry,
            onDismiss = onDismissDetail,
            onDelete = { onDeleteDocument(entry.item.id) },
        )
    }
}

@Composable
private fun Header() {
    Text(
        "Document Vault",
        style = JetTheme.typography.displayTitle,
        color = JetTheme.colors.textPrimary,
    )
}

/**
 * Banner highlighting the single most urgent document. Copy and color are derived from the same
 * status/day math as the cards, so the headline can never disagree with the list.
 */
@Composable
private fun AttentionBanner(entry: DocumentVaultEntry) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val color = entry.status.color()
    val days = entry.daysUntilExpiry
    val message = when {
        days < 0L -> "${entry.item.title} expired ${-days} ${dayWord(-days)} ago"
        days == 0L -> "${entry.item.title} expires today"
        else -> "${entry.item.title} expires in $days ${dayWord(days)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.6.dp, color.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(entry.status.icon(), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Needs attention", style = typography.label, color = color)
            Spacer(Modifier.height(2.dp))
            Text(message, style = typography.bodyMedium, color = colors.textPrimary)
        }
    }
}

@Composable
private fun SummaryCard(
    state: DocumentvaultUiState,
    onSelectStatus: (DocumentVaultStatus?) -> Unit,
) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("OVERVIEW", style = JetTheme.typography.caption, color = colors.textSecondary)
            Text(
                "${state.totalCount} ${if (state.totalCount == 1) "document" else "documents"}",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryStat(
                value = state.validCount,
                label = "Valid",
                color = colors.success,
                selected = state.selectedStatus == DocumentVaultStatus.VALID,
                onClick = { onSelectStatus(DocumentVaultStatus.VALID) },
            )
            SummaryStat(
                value = state.expiringCount,
                label = "Expiring",
                color = colors.warning,
                selected = state.selectedStatus == DocumentVaultStatus.EXPIRING_SOON,
                onClick = { onSelectStatus(DocumentVaultStatus.EXPIRING_SOON) },
            )
            SummaryStat(
                value = state.expiredCount,
                label = "Expired",
                color = colors.danger,
                selected = state.selectedStatus == DocumentVaultStatus.EXPIRED,
                onClick = { onSelectStatus(DocumentVaultStatus.EXPIRED) },
            )
        }
        if (state.selectedStatus != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Showing ${state.selectedStatus.label.lowercase(Locale.US)} only — tap again to clear.",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** A tappable summary stat that doubles as a status filter; highlights when its status is active. */
@Composable
private fun RowScope.SummaryStat(
    value: Int,
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) color.copy(alpha = 0.55f) else colors.separator,
                shape = shape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), style = JetTheme.typography.metric, color = color)
        Text(label, style = JetTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun FilterRow(selected: DocumentVaultType?, onSelect: (DocumentVaultType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(label = "All", selected = selected == null, onClick = { onSelect(null) })
        DocumentVaultType.entries.forEach { type ->
            FilterChip(label = type.label, selected = selected == type, onClick = { onSelect(type) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (selected) colors.accent else colors.surfaceElevated
    val foreground = if (selected) Color.White else colors.textSecondary
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = JetTheme.typography.label, color = foreground)
    }
}

@Composable
private fun DocumentCard(
    entry: DocumentVaultEntry,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val item = entry.item

    JetCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.type.icon(), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = typography.cardTitle, color = colors.textPrimary)
                Text(item.issuingAuthority, style = typography.caption, color = colors.textSecondary)
            }
            StatusChip(entry.status)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleReveal()
                }
                .semantics {
                    role = Role.Button
                    stateDescription = if (revealed) "Revealed" else "Hidden"
                }
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("DOCUMENT NO.", style = typography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (revealed) item.documentNumber else item.maskedNumber,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
            Icon(
                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (revealed) "Hide document number" else "Reveal document number",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(colors.separator))
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "Expires ${item.expiryDate.format(documentDateFormatter)}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            AccentTag(text = expiryHint(entry), icon = Icons.Filled.Schedule)
        }
    }
}

@Composable
private fun StatusChip(status: DocumentVaultStatus) {
    val color = status.color()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(status.icon(), contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(status.label, style = JetTheme.typography.label, color = color)
    }
}

/**
 * Detail / selected state — a read-only deep-dive opened by tapping a card. Shows the full number,
 * a live day countdown, and (for tester-added documents) a delete action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailSheet(
    entry: DocumentVaultEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val item = entry.item
    val color = entry.status.color()
    val days = entry.daysUntilExpiry

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
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.type.icon(), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = typography.pageTitle, color = colors.textPrimary)
                    Text(item.type.label, style = typography.caption, color = colors.textSecondary)
                }
                StatusChip(entry.status)
            }

            Spacer(Modifier.height(spacing.xsmall))

            // Live countdown — the headline metric, color-matched to status.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.10f))
                    .border(0.6.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (days < 0L) (-days).toString() else days.toString(),
                    style = typography.metric,
                    color = color,
                )
                Column {
                    Text(
                        when {
                            days < 0L -> "${dayWord(-days)} overdue"
                            days == 0L -> "expires today"
                            else -> "${dayWord(days)} until expiry"
                        },
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                    )
                    Text(
                        "Expires ${item.expiryDate.format(documentDateFormatter)}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }

            DetailRow(Icons.Filled.CreditCard, "Document number", item.documentNumber)
            DetailRow(Icons.Filled.Person, "Holder", item.holder)
            DetailRow(Icons.Filled.AccountBalance, "Issuing authority", item.issuingAuthority)

            if (item.isUserAdded) {
                Spacer(Modifier.height(spacing.small))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger.copy(alpha = 0.14f),
                        contentColor = colors.danger,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete document", style = typography.cardTitle)
                }
            } else {
                Spacer(Modifier.height(spacing.xsmall))
                Text(
                    "Sample document — included to demonstrate the vault.",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(Locale.US), style = typography.caption, color = colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(value, style = typography.bodyMedium, color = colors.textPrimary)
        }
    }
}

/**
 * "Add document" sheet. Inputs are local draft state ([rememberSaveable]); the type is chosen via
 * chips and the expiry date is validated live (ISO yyyy-MM-dd) with a status preview, so the
 * tester sees exactly how the new document will be classified before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDocumentSheet(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        type: DocumentVaultType,
        number: String,
        holder: String,
        authority: String,
        expiry: String,
    ) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var holder by rememberSaveable { mutableStateOf("") }
    var authority by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(DocumentVaultType.PASSPORT) }

    val parsedDate: LocalDate? = remember(expiry) { parseExpiry(expiry) }
    val dateError = expiry.isNotBlank() && parsedDate == null
    val valid = title.isNotBlank() && number.isNotBlank() && parsedDate != null

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
            Text("Add document", style = typography.pageTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Type")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DocumentVaultType.entries.forEach { entryType ->
                    FilterChip(
                        label = entryType.label,
                        selected = type == entryType,
                        onClick = { type = entryType },
                    )
                }
            }

            FieldLabel("Title")
            PremiumTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "e.g. Canadian Passport",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Document number")
            PremiumTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = "AB 1234 5678",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Issuing authority")
            PremiumTextField(
                value = authority,
                onValueChange = { authority = it },
                placeholder = "Optional",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Holder")
            PremiumTextField(
                value = holder,
                onValueChange = { holder = it },
                placeholder = "Optional",
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Expiry date (YYYY-MM-DD)")
            PremiumTextField(
                value = expiry,
                onValueChange = { expiry = it },
                placeholder = exampleExpiry(),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                dateError -> Text(
                    "Use the format YYYY-MM-DD, e.g. ${exampleExpiry()}.",
                    style = typography.caption,
                    color = colors.danger,
                )
                parsedDate != null -> {
                    val previewStatus = documentVaultStatus(parsedDate, LocalDate.now())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Will be saved as", style = typography.caption, color = colors.textSecondary)
                        StatusChip(previewStatus)
                    }
                }
            }

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(title, type, number, holder, authority, expiry)
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to vault", style = typography.cardTitle)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun EmptyState(hasActiveFilter: Boolean) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    if (hasActiveFilter) "No matches" else "Your vault is empty",
                    style = JetTheme.typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    if (hasActiveFilter) "Try a different filter to see your stored documents." else "Tap + to add your first document.",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't load documents", style = JetTheme.typography.cardTitle, color = colors.danger)
        Spacer(Modifier.height(4.dp))
        Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

private fun expiryHint(entry: DocumentVaultEntry): String {
    val days = entry.daysUntilExpiry
    return when {
        days < 0L -> "${-days}d overdue"
        days == 0L -> "Expires today"
        days < 30L -> "${days}d left"
        days < 365L -> "${days / 30L}mo left"
        else -> "${days / 365L}y left"
    }
}

private fun dayWord(days: Long): String = if (days == 1L) "day" else "days"

/** Parses an ISO `yyyy-MM-dd` string, returning null on anything malformed. */
private fun parseExpiry(text: String): LocalDate? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return try {
        LocalDate.parse(trimmed)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** A realistic future placeholder/example date (~5 years out) so testers know the format. */
private fun exampleExpiry(): String = LocalDate.now().plusYears(5).toString()

private fun DocumentVaultType.icon(): ImageVector = when (this) {
    DocumentVaultType.PASSPORT -> Icons.Filled.Book
    DocumentVaultType.VISA -> Icons.Filled.CreditCard
    DocumentVaultType.VACCINATION -> Icons.Filled.Vaccines
    DocumentVaultType.INSURANCE -> Icons.Filled.Shield
}

@Composable
private fun DocumentVaultStatus.color(): Color = when (this) {
    DocumentVaultStatus.VALID -> JetTheme.colors.success
    DocumentVaultStatus.EXPIRING_SOON -> JetTheme.colors.warning
    DocumentVaultStatus.EXPIRED -> JetTheme.colors.danger
}

private fun DocumentVaultStatus.icon(): ImageVector = when (this) {
    DocumentVaultStatus.VALID -> Icons.Filled.CheckCircle
    DocumentVaultStatus.EXPIRING_SOON -> Icons.Filled.Schedule
    DocumentVaultStatus.EXPIRED -> Icons.Filled.ErrorOutline
}

@Preview(showBackground = true, name = "Document Vault – populated")
@Composable
private fun DocumentVaultContentPreview() {
    val today = LocalDate.now()
    fun entry(
        id: String,
        title: String,
        type: DocumentVaultType,
        number: String,
        authority: String,
        expiry: LocalDate,
    ): DocumentVaultEntry {
        val item = DocumentVaultItem(
            id = id,
            title = title,
            type = type,
            documentNumber = number,
            holder = "Jamil Jones",
            issuingAuthority = authority,
            expiryDate = expiry,
        )
        return DocumentVaultEntry(item, item.status(today), item.daysUntilExpiry(today))
    }

    val entries = listOf(
        entry("insurance-allianz", "Allianz Travel Insurance", DocumentVaultType.INSURANCE, "AZ 9931 2050", "Allianz Global Assistance", today.minusDays(9)),
        entry("visa-schengen", "Schengen Visa", DocumentVaultType.VISA, "FR 0044 9182", "Consulate General of France", today.plusDays(41)),
        entry("passport-us", "U.S. Passport", DocumentVaultType.PASSPORT, "X 5839 2271", "U.S. Department of State", today.plusYears(4)),
    )

    JetSetterTheme {
        DocumentVaultContent(
            state = DocumentvaultUiState(
                documents = entries,
                revealedIds = setOf("passport-us"),
                totalCount = 3,
                validCount = 1,
                expiringCount = 1,
                expiredCount = 1,
                attention = entries.first(),
            ),
            onToggleReveal = {},
            onSelectFilter = {},
            onSelectStatus = {},
            onOpenDocument = {},
            onDismissDetail = {},
            onShowAddSheet = {},
            onDismissAddSheet = {},
            onAddDocument = { _, _, _, _, _, _ -> },
            onDeleteDocument = {},
        )
    }
}
