package com.jetsetter.pro.feature.expenses

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/** Pause in merchant typing before asking the on-device categorizer for a suggestion. */
private const val SUGGESTION_DEBOUNCE_MS = 500L

/**
 * Stateful entry point: owns the [ExpensesViewModel], collects its [ExpensesUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [ExpensesContent].
 */
@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val suggestedCategory by viewModel.suggestedCategory.collectAsStateWithLifecycle()
    ExpensesContent(
        state = state,
        suggestedCategory = suggestedCategory,
        onSelectCategory = viewModel::selectCategory,
        onAddExpense = viewModel::addExpense,
        onAddMileage = viewModel::addMileage,
        onSuggestCategory = viewModel::suggestCategory,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable. Transient form input (the add-expense sheet) is local UI state via [remember].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensesContent(
    state: ExpensesUiState,
    suggestedCategory: ExpenseCategory?,
    onSelectCategory: (ExpenseCategory?) -> Unit,
    onAddExpense: (amount: Double, merchant: String, category: ExpenseCategory, date: String) -> Unit,
    onAddMileage: (miles: Double, date: String) -> Unit,
    onSuggestCategory: (merchant: String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    var showSheet by remember { mutableStateOf(false) }

    // One-time entrance: fade + slight upward slide on first composition (~250ms). Purely visual.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "entranceAlpha",
    )
    val entranceOffset by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(durationMillis = 250),
        label = "entranceOffset",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entranceAlpha
                    translationY = entranceOffset
                }
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("Expenses", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            state.errorMessage?.let { ErrorCard(it) }

            SummaryCard(state = state)

            MileageQuickAddCard(onAddMileage = onAddMileage)

            if (state.categoryTotals.isNotEmpty()) {
                CategoryFilterRow(
                    selected = state.selectedCategory,
                    onSelectCategory = onSelectCategory,
                )
            }

            if (state.visibleExpenses.isEmpty()) {
                EmptyCard(isLoading = state.isLoading, filtered = state.selectedCategory != null)
            } else {
                state.visibleExpenses.forEach { expense ->
                    ExpenseRow(expense)
                }
            }
        }

        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSuggestCategory("") // clear any stale suggestion before the sheet opens
                showSheet = true
            },
            containerColor = colors.accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.medium),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add expense")
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            AddExpenseSheet(
                suggestedCategory = suggestedCategory,
                onSuggestCategory = onSuggestCategory,
                onConfirm = { amount, merchant, category, date ->
                    onAddExpense(amount, merchant, category, date)
                    showSheet = false
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(state: ExpensesUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("TRIP TOTAL", style = typography.caption, color = colors.textSecondary)
        Text(money(state.grandTotal), style = typography.metric, color = colors.textPrimary)

        val activeFilter = state.selectedCategory
        if (activeFilter != null) {
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "${activeFilter.label} · ${money(state.visibleTotal)}",
                style = typography.caption,
                color = colors.accent,
            )
        }

        if (state.categoryTotals.isNotEmpty()) {
            Spacer(Modifier.height(spacing.medium))
            Text("BY CATEGORY", style = typography.caption, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.small))
            state.categoryTotals.forEach { entry ->
                CategoryBreakdownRow(
                    entry = entry,
                    grandTotal = state.grandTotal,
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(entry: CategoryTotal, grandTotal: Double) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val tint = categoryColor(entry.category)
    val fraction = if (grandTotal > 0.0) (entry.total / grandTotal).toFloat().coerceIn(0f, 1f) else 0f
    // Ease the bar in toward the exact computed fraction instead of snapping; math is unchanged.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "breakdownFraction",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xsmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                Text(entry.category.label, style = typography.bodyMedium, color = colors.textPrimary)
            }
            Text(money(entry.total), style = typography.bodyMedium, color = colors.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.separator.copy(alpha = 0.4f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

@Composable
private fun MileageQuickAddCard(onAddMileage: (miles: Double, date: String) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val today = remember { LocalDate.now().toString() }

    var miles by remember { mutableStateOf("") }
    val milesValue = miles.toDoubleOrNull()
    val reimbursement = (milesValue ?: 0.0) * ExpensesViewModel.IRS_MILEAGE_RATE

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MILEAGE QUICK-ADD", style = typography.caption, color = colors.textSecondary)
            AccentTag(text = "${money(ExpensesViewModel.IRS_MILEAGE_RATE)}/mi", icon = Icons.Filled.Speed)
        }
        Spacer(Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumTextField(
                value = miles,
                onValueChange = { miles = sanitizeDecimal(it) },
                placeholder = "Miles driven",
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                label = "Add ${money(reimbursement)}",
                enabled = milesValue != null && milesValue > 0.0,
                onClick = {
                    milesValue?.let {
                        onAddMileage(it, today)
                        miles = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: ExpenseCategory?,
    onSelectCategory: (ExpenseCategory?) -> Unit,
) {
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        FilterChip(label = "All", selected = selected == null, onClick = { onSelectCategory(null) })
        ExpenseCategory.entries.forEach { category ->
            FilterChip(
                label = category.label,
                selected = selected == category,
                onClick = { onSelectCategory(category) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = JetTheme.typography.label, color = textColor)
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val tint = categoryColor(expense.category)

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(expense.category),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(expense.merchant, style = typography.bodyMedium, color = colors.textPrimary)
                    Text(
                        "${expense.category.label} · ${expense.date}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Text(money(expense.amount), style = typography.cardTitle, color = colors.textPrimary)
        }
    }
}

@Composable
private fun AddExpenseSheet(
    suggestedCategory: ExpenseCategory?,
    onSuggestCategory: (merchant: String) -> Unit,
    onConfirm: (amount: Double, merchant: String, category: ExpenseCategory, date: String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var categoryPickedByUser by remember { mutableStateOf(false) }

    // Debounced on-device categorization (spec §3.1): re-keyed per keystroke, so only a pause in
    // typing the merchant reaches the categorizer.
    LaunchedEffect(merchant) {
        if (merchant.isBlank()) return@LaunchedEffect
        delay(SUGGESTION_DEBOUNCE_MS)
        onSuggestCategory(merchant)
    }

    // Prefill only: the suggestion moves the selected chip unless the user has already picked one.
    LaunchedEffect(suggestedCategory) {
        if (suggestedCategory != null && !categoryPickedByUser) category = suggestedCategory
    }

    val amountValue = amount.toDoubleOrNull()
    val valid = amountValue != null && amountValue > 0.0 && merchant.isNotBlank() && date.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = spacing.medium)
            .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text("Add expense", style = typography.pageTitle, color = colors.textPrimary)

        FieldLabel("AMOUNT")
        PremiumTextField(
            value = amount,
            onValueChange = { amount = sanitizeDecimal(it) },
            placeholder = "0.00",
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("MERCHANT")
        PremiumTextField(
            value = merchant,
            onValueChange = { merchant = it },
            placeholder = "e.g. The Ritz-Carlton",
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel("CATEGORY")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            ExpenseCategory.entries.forEach { entry ->
                FilterChip(
                    label = entry.label,
                    selected = entry == category,
                    onClick = {
                        categoryPickedByUser = true
                        category = entry
                    },
                )
            }
        }

        FieldLabel("DATE")
        PremiumTextField(
            value = date,
            onValueChange = { date = it },
            placeholder = "YYYY-MM-DD",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(spacing.small))
        PrimaryButton(
            label = "Add expense",
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
            onClick = { amountValue?.let { onConfirm(it, merchant, category, date) } },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.caption, color = JetTheme.colors.textSecondary)
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    val background = if (enabled) colors.accent else colors.accent.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = JetTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

@Composable
private fun EmptyCard(isLoading: Boolean, filtered: Boolean) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                when {
                    isLoading -> "Loading expenses…"
                    filtered -> "No expenses in this category"
                    else -> "No expenses yet"
                },
                style = JetTheme.typography.cardTitle,
                color = colors.textPrimary,
            )
            Text(
                when {
                    isLoading -> "Fetching your trip spend."
                    filtered -> "Switch the filter above or tap + to add one."
                    else -> "Tap + to log your first expense."
                },
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't load expenses", style = JetTheme.typography.cardTitle, color = colors.danger)
        Spacer(Modifier.height(4.dp))
        Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

/** Map each category to a JetSetter theme token so the breakdown bars / row icons stay on-brand. */
@Composable
private fun categoryColor(category: ExpenseCategory): Color {
    val colors = JetTheme.colors
    return when (category) {
        ExpenseCategory.FOOD -> colors.warning
        ExpenseCategory.TRANSPORT -> colors.blue
        ExpenseCategory.ACCOMMODATION -> colors.accent
        ExpenseCategory.ENTERTAINMENT -> colors.primary
        ExpenseCategory.BUSINESS -> colors.success
        ExpenseCategory.SHOPPING -> colors.danger
        ExpenseCategory.MEDICAL -> colors.danger
        ExpenseCategory.MILEAGE -> colors.blue
        ExpenseCategory.OTHER -> colors.textSecondary
    }
}

private fun categoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FOOD -> Icons.Filled.Restaurant
    ExpenseCategory.TRANSPORT -> Icons.Filled.DirectionsCar
    ExpenseCategory.ACCOMMODATION -> Icons.Filled.Hotel
    ExpenseCategory.ENTERTAINMENT -> Icons.Filled.Celebration
    ExpenseCategory.BUSINESS -> Icons.Filled.BusinessCenter
    ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingBag
    ExpenseCategory.MEDICAL -> Icons.Filled.LocalHospital
    ExpenseCategory.MILEAGE -> Icons.Filled.Speed
    ExpenseCategory.OTHER -> Icons.Filled.Category
}

/** Keep only digits and a single decimal point, so a text keyboard still yields a parseable amount. */
private fun sanitizeDecimal(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot == -1) return filtered
    val head = filtered.substring(0, firstDot + 1)
    val tail = filtered.substring(firstDot + 1).replace(".", "")
    return head + tail
}

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

@Preview(showBackground = true, name = "Expenses – populated")
@Composable
private fun ExpensesContentPreview() {
    JetSetterTheme {
        ExpensesContent(
            state = ExpensesUiState(
                expenses = listOf(
                    Expense(amount = 412.55, category = ExpenseCategory.ACCOMMODATION, merchant = "The Ritz-Carlton", date = "2026-07-14"),
                    Expense(amount = 86.20, category = ExpenseCategory.FOOD, merchant = "Bacchanalia", date = "2026-07-15"),
                    Expense(amount = 24.00, category = ExpenseCategory.TRANSPORT, merchant = "Uber", date = "2026-07-14"),
                    Expense(amount = 1290.00, category = ExpenseCategory.BUSINESS, merchant = "Delta Air Lines", date = "2026-07-10"),
                ),
            ),
            suggestedCategory = null,
            onSelectCategory = {},
            onAddExpense = { _, _, _, _ -> },
            onAddMileage = { _, _ -> },
            onSuggestCategory = {},
        )
    }
}
