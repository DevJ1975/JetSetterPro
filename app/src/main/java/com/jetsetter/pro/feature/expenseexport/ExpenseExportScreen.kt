package com.jetsetter.pro.feature.expenseexport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale

/**
 * Stateful entry point: owns the [ExpenseexportViewModel], collects its
 * [ExpenseexportUiState] lifecycle-aware, and forwards events. Holds no logic.
 */
@Composable
fun ExpenseExportScreen(viewModel: ExpenseexportViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    ExpenseExportContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onToggleItem = viewModel::toggleItem,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onToggleConnection = viewModel::toggleConnection,
        onExport = viewModel::export,
        onDismissResult = viewModel::dismissExportResult,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially
 * previewable and testable.
 */
@Composable
private fun ExpenseExportContent(
    state: ExpenseexportUiState,
    onSelectFormat: (ExpenseExportFormat) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleConnection: (String) -> Unit,
    onExport: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade the screen in on first composition (~250ms). Purely visual
    // and applied via a draw layer, so scrolling is unaffected.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "screenEnter",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .graphicsLayer { alpha = enterAlpha }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xsmall)) {
            Text("Expense Export", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            Text(
                "Pick the expenses, choose a format, and push to your accounting stack.",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }

        SummaryCard(count = state.itemCount, totalCount = state.totalCount, total = state.total)

        ExpensesCard(
            items = state.items,
            allSelected = state.allSelected,
            noneSelected = state.noneSelected,
            onToggleItem = onToggleItem,
            onToggleSelectAll = onToggleSelectAll,
        )

        FormatCard(
            selected = state.selectedFormat,
            itemCount = state.itemCount,
            onSelectFormat = onSelectFormat,
        )

        ConnectionsCard(
            connections = state.connections,
            connectedCount = state.connectedCount,
            onToggleConnection = onToggleConnection,
        )

        state.result?.let { result ->
            ExportResultCard(result = result, onDismiss = onDismissResult)
        }

        ExportButton(
            isExporting = state.isExporting,
            enabled = state.canExport,
            count = state.itemCount,
            format = state.selectedFormat,
            onExport = onExport,
        )

        if (state.noneSelected && !state.isExporting) {
            ValidationHint("Select at least one expense to export.")
        }
    }
}

@Composable
private fun SummaryCard(count: Int, totalCount: Int, total: Double) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography

    // Fraction of the trip's expenses currently selected — derived from the already-computed
    // counts (no math change). Eased in so it reveals smoothly and never snaps on toggle.
    val selectionFraction = if (totalCount > 0) count.toFloat() / totalCount else 0f
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val animatedFraction by animateFloatAsState(
        targetValue = if (revealed) selectionFraction else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "selectionFraction",
    )

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("READY TO EXPORT", style = typography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(money(total), style = typography.metric, color = colors.textPrimary)
            }
            AccentTag(text = "$count of $totalCount")
        }
        Spacer(Modifier.height(JetTheme.spacing.small))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = colors.accent,
            trackColor = colors.separator,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(JetTheme.spacing.small))
        Text(
            "Current trip · Atlanta Board Meeting",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ExpensesCard(
    items: List<ExpenseExportItem>,
    allSelected: Boolean,
    noneSelected: Boolean,
    onToggleItem: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text("EXPENSES", style = typography.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                text = if (allSelected) "Clear all" else "Select all",
                style = typography.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSelectAll()
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .minimumInteractiveComponentSize(),
            )
        }
        Spacer(Modifier.height(JetTheme.spacing.small))
        if (items.isEmpty()) {
            ExpensesEmptyState()
        } else {
            items.forEachIndexed { index, item ->
                ExpenseRow(item = item, onToggle = { onToggleItem(item.id) })
                if (index < items.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (noneSelected) {
                Spacer(Modifier.height(JetTheme.spacing.small))
                Text(
                    "Nothing selected — toggle a row above to include it.",
                    style = typography.caption,
                    color = colors.warning,
                )
            }
        }
    }
}

@Composable
private fun ExpensesEmptyState() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(32.dp),
        )
        Text("No expenses to export", style = typography.cardTitle, color = colors.textPrimary)
        Text(
            "Add expenses to this trip and they'll show up here ready to export.",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ExpenseRow(item: ExpenseExportItem, onToggle: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val primary = if (item.included) colors.textPrimary else colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = if (item.included) "Included" else "Excluded"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (item.included) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.included) colors.success else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.merchant, style = typography.bodyMedium, color = primary)
            Text("${item.category} · ${item.date}", style = typography.caption, color = colors.textSecondary)
        }
        Text(
            money(item.amount),
            style = typography.bodyMedium,
            color = primary,
            textDecoration = if (item.included) null else TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun FormatCard(
    selected: ExpenseExportFormat,
    itemCount: Int,
    onSelectFormat: (ExpenseExportFormat) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("FORMAT", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(spacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            FormatChip(
                label = "PDF",
                description = "Print-ready report",
                sizeLabel = formatBytes(estimatedExportBytes(itemCount, ExpenseExportFormat.PDF)),
                icon = Icons.Outlined.Description,
                selected = selected == ExpenseExportFormat.PDF,
                onClick = { onSelectFormat(ExpenseExportFormat.PDF) },
                modifier = Modifier.weight(1f),
            )
            FormatChip(
                label = "CSV",
                description = "Spreadsheet data",
                sizeLabel = formatBytes(estimatedExportBytes(itemCount, ExpenseExportFormat.CSV)),
                icon = Icons.Outlined.GridOn,
                selected = selected == ExpenseExportFormat.CSV,
                onClick = { onSelectFormat(ExpenseExportFormat.CSV) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FormatChip(
    label: String,
    description: String,
    sizeLabel: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) colors.accent else colors.separator
    val bg = if (selected) colors.accent.copy(alpha = 0.12f) else colors.surfaceElevated
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            label,
            style = typography.cardTitle,
            color = if (selected) colors.accent else colors.textPrimary,
        )
        Text(description, style = typography.caption, color = colors.textSecondary)
        Text("~ $sizeLabel", style = typography.caption, color = if (selected) colors.accent else colors.textSecondary)
    }
}

@Composable
private fun ConnectionsCard(
    connections: List<ExpenseExportConnection>,
    connectedCount: Int,
    onToggleConnection: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text("ACCOUNTING CONNECTIONS", style = typography.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                "$connectedCount connected",
                style = typography.caption,
                color = if (connectedCount > 0) colors.success else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(JetTheme.spacing.small))
        connections.forEachIndexed { index, connection ->
            ConnectionRow(connection = connection, onToggle = { onToggleConnection(connection.id) })
            if (index < connections.lastIndex) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ConnectionRow(connection: ExpenseExportConnection, onToggle: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(connection.name, style = typography.bodyMedium, color = colors.textPrimary)
            Text(connection.description, style = typography.caption, color = colors.textSecondary)
        }
        if (connection.isConnected) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.success),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Connected", style = typography.label, color = colors.success)
            }
        } else {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
            ) {
                Text("Connect", style = typography.label, color = Color.White)
            }
        }
    }
}

@Composable
private fun ExportButton(
    isExporting: Boolean,
    enabled: Boolean,
    count: Int,
    format: ExpenseExportFormat,
    onExport: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onExport()
        },
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = Color.White,
            disabledContainerColor = colors.accent.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (isExporting) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(spacing.small))
            Text("Exporting…", style = JetTheme.typography.cardTitle, color = Color.White)
        } else {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(spacing.small))
            Text(
                "Export $count ${pluralExpenses(count)} as ${format.label}",
                style = JetTheme.typography.cardTitle,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ValidationHint(message: String) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(message, style = JetTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun ExportResultCard(result: ExpenseExportResult, onDismiss: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Export complete", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${result.itemCount} ${pluralExpenses(result.itemCount)} · ${money(result.total)}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ResultDetailRow(label = "File", value = "${result.fileName} · ${result.sizeLabel}")
        Spacer(Modifier.height(6.dp))
        ResultDetailRow(
            label = "Destination",
            value = if (result.syncedTo.isEmpty()) "Saved to device" else "Synced to ${result.syncedTo.joinToString(", ")}",
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done", style = typography.label.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        }
    }
}

@Composable
private fun ResultDetailRow(label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = typography.caption, color = colors.textSecondary, modifier = Modifier.width(76.dp))
        Text(value, style = typography.caption, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

private fun pluralExpenses(count: Int): String = if (count == 1) "expense" else "expenses"

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

@Preview(showBackground = true, name = "Expense Export")
@Composable
private fun ExpenseExportContentPreview() {
    JetSetterTheme {
        ExpenseExportContent(
            state = ExpenseexportUiState(
                items = listOf(
                    ExpenseExportItem("1", "Delta Air Lines", "Airfare", "2026-07-14", 1290.00),
                    ExpenseExportItem("2", "Four Seasons Atlanta", "Lodging", "2026-07-14", 412.55),
                    ExpenseExportItem("3", "Uber", "Ground Transport", "2026-07-15", 38.20, included = false),
                ),
                connections = listOf(
                    ExpenseExportConnection("brex", "Brex", "Corporate cards & spend management", isConnected = true),
                    ExpenseExportConnection("ramp", "Ramp", "Finance automation platform"),
                    ExpenseExportConnection("divvy", "Divvy", "BILL spend & expense"),
                    ExpenseExportConnection("expensify", "Expensify", "Receipt & expense reports"),
                ),
                selectedFormat = ExpenseExportFormat.PDF,
            ),
            onSelectFormat = {},
            onToggleItem = {},
            onToggleSelectAll = {},
            onToggleConnection = {},
            onExport = {},
            onDismissResult = {},
        )
    }
}
