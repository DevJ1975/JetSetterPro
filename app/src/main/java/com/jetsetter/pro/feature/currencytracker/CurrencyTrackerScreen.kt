package com.jetsetter.pro.feature.currencytracker

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * Stateful entry point: owns the [CurrencytrackerViewModel], collects its
 * [CurrencytrackerUiState] lifecycle-aware, and forwards events. Holds no logic —
 * see [CurrencyTrackerContent].
 */
@Composable
fun CurrencyTrackerScreen(viewModel: CurrencytrackerViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    CurrencyTrackerContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onSelectBase = viewModel::onSelectBase,
        onSelectTarget = viewModel::onSelectTarget,
        onSwap = viewModel::onSwapCurrencies,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun CurrencyTrackerContent(
    state: CurrencytrackerUiState,
    onAmountChange: (String) -> Unit,
    onSelectBase: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                // One-time entrance: fade + gentle slide-up on first composition. Purely visual —
                // a graphics layer over the scroll content, so scrolling stays untouched.
                var contentVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { contentVisible = true }
                val entrance by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "currencyEntrance",
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .graphicsLayer {
                            alpha = entrance
                            translationY = (1f - entrance) * 16.dp.toPx()
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.medium)
                        .padding(top = spacing.large, bottom = spacing.xlarge),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    Text("Currency", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

                    state.errorMessage?.let { ErrorCard(it) }

                    if (state.currencies.isEmpty()) {
                        EmptyState()
                    } else {
                        ConverterCard(
                            state = state,
                            onAmountChange = onAmountChange,
                            onSelectBase = onSelectBase,
                            onSelectTarget = onSelectTarget,
                            onSwap = onSwap,
                        )

                        RateList(
                            state = state,
                            onSelectTarget = onSelectTarget,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterCard(
    state: CurrencytrackerUiState,
    onAmountChange: (String) -> Unit,
    onSelectBase: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CONVERTER", style = typography.caption, color = colors.textSecondary)
            if (state.lastUpdated.isNotEmpty()) {
                // Bundled, offline FX table — not a live quote. The real key for live FX is a
                // future drop-in (CurrencytrackerFxData.API_FX_KEY is unset).
                AccentTag(text = CurrencytrackerFxData.APPROXIMATE_LABEL, icon = Icons.Filled.Schedule)
            }
        }

        Spacer(Modifier.height(spacing.medium))

        // Amount — sanitized to digits + a single dot in the ViewModel, so a text keyboard is fine.
        FieldLabel("Amount")
        Spacer(Modifier.height(6.dp))
        AmountField(
            value = state.amountInput,
            symbol = state.baseCurrency?.symbol ?: "",
            onValueChange = onAmountChange,
        )

        Spacer(Modifier.height(spacing.medium))

        // From / Swap / To selectors.
        SelectorBlock(label = "From", selectedCode = state.baseCurrencyCode, currencies = state.currencies, onSelect = onSelectBase)

        Spacer(Modifier.height(spacing.small))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SwapButton(onClick = onSwap)
        }
        Spacer(Modifier.height(spacing.small))

        SelectorBlock(label = "To", selectedCode = state.targetCurrencyCode, currencies = state.currencies, onSelect = onSelectTarget)

        Spacer(Modifier.height(spacing.medium))
        HorizontalDivider(color = colors.separator, thickness = 0.6.dp)
        Spacer(Modifier.height(spacing.medium))

        ResultBlock(state = state)
    }
}

@Composable
private fun ResultBlock(state: CurrencytrackerUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val target = state.targetCurrency ?: return
    val base = state.baseCurrency ?: return
    val converted = state.convertedAmount

    Text(
        "${target.flag}  ${target.name}",
        style = typography.caption,
        color = colors.textSecondary,
    )
    Spacer(Modifier.height(spacing.xsmall))

    if (converted == null) {
        // Validation / empty state: amount field is blank or unparseable.
        Text(
            "${target.symbol}—",
            style = typography.metric,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(spacing.xsmall))
        Text(
            "Enter an amount to convert",
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
    } else {
        Text(
            "${target.symbol}${formatAmount(converted)}",
            style = typography.metric,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(spacing.xsmall))
        Text(
            "${formatAmount(state.amount ?: 0.0)} ${base.code} = ${formatAmount(converted)} ${target.code}",
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
    }

    state.crossRate?.let { rate ->
        Spacer(Modifier.height(spacing.small))
        AccentTag(
            text = "1 ${base.code} = ${formatRate(rate)} ${target.code}",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
        )
    }
}

@Composable
private fun SelectorBlock(
    label: String,
    selectedCode: String,
    currencies: List<CurrencyRate>,
    onSelect: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val selected = currencies.firstOrNull { it.code == selectedCode }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(label)
        if (selected != null) {
            Text(
                selected.name,
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        currencies.forEach { currency ->
            CurrencyChip(
                currency = currency,
                selected = currency.code == selectedCode,
                onClick = { onSelect(currency.code) },
            )
        }
    }
}

@Composable
private fun AmountField(value: String, symbol: String, onValueChange: (String) -> Unit) {
    // Mirrors PremiumTextField styling but uses a numeric input field; the amount is the live
    // driver of the whole screen, so it gets the decimal keyboard via BasicTextField.
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (colors.isDark) Color(0xFF141726) else Color(0xFFF4F5FB))
            .border(0.5.dp, colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.06f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (symbol.isNotEmpty()) {
            Text(symbol, style = typography.cardTitle, color = colors.accent)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = typography.cardTitle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("0", style = typography.cardTitle, color = colors.textSecondary)
                }
                inner()
            },
        )
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.15f))
            .border(0.6.dp, colors.accent.copy(alpha = 0.40f), CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = "Swap currencies",
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CurrencyChip(currency: CurrencyRate, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (selected) colors.accent.copy(alpha = 0.18f) else colors.surfaceElevated
    val borderColor = if (selected) colors.accent.copy(alpha = 0.45f) else colors.separator
    val textColor = if (selected) colors.accent else colors.textPrimary
    Row(
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
                role = Role.Tab
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(currency.flag, style = JetTheme.typography.bodyMedium, color = textColor)
        Text(currency.code, style = JetTheme.typography.label, color = textColor)
    }
}

@Composable
private fun RateList(state: CurrencytrackerUiState, onSelectTarget: (String) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val base = state.baseCurrency ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "1 ${base.code} EQUALS",
            style = typography.caption,
            color = colors.textSecondary,
        )
        if (state.lastUpdated.isNotEmpty()) {
            Text(state.lastUpdated, style = typography.caption, color = colors.textSecondary)
        }
    }

    Spacer(Modifier.height(spacing.small))

    state.currencies
        .filter { it.code != base.code }
        .forEach { rate ->
            RateRowCard(
                rate = rate,
                base = base,
                selected = rate.code == state.targetCurrencyCode,
                onClick = { onSelectTarget(rate.code) },
            )
            Spacer(Modifier.height(spacing.small))
        }
}

@Composable
private fun RateRowCard(
    rate: CurrencyRate,
    base: CurrencyRate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current

    // Everything is derived from the USD anchors so it stays consistent with the chosen base.
    val cross = if (base.ratePerUsd == 0.0) 0.0 else rate.ratePerUsd / base.ratePerUsd
    val changeVsBase = base.dailyChangePct - rate.dailyChangePct
    val up = changeVsBase >= 0.0

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.Button
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(rate.flag, style = typography.pageTitle, color = colors.textPrimary)
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(rate.code, style = typography.cardTitle, color = colors.textPrimary)
                        if (selected) AccentTag(text = "To")
                    }
                    Text(rate.name, style = typography.caption, color = colors.textSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRate(cross), style = typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (up) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = if (up) colors.success else colors.danger,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "${formatSigned(changeVsBase)}%",
                        style = typography.caption,
                        color = if (up) colors.success else colors.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(18.dp),
            )
            Text("Couldn't load rates", style = JetTheme.typography.cardTitle, color = colors.danger)
        }
        Spacer(Modifier.height(spacing.xsmall))
        Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CurrencyExchange,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text("No rates available", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Currency rates couldn't be loaded right now.",
                style = JetTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

private fun formatAmount(value: Double): String = String.format(Locale.US, "%,.2f", value)

private fun formatRate(value: Double): String =
    if (value >= 100) String.format(Locale.US, "%,.2f", value)
    else String.format(Locale.US, "%,.4f", value)

private fun formatSigned(value: Double): String =
    (if (value >= 0) "+" else "") + String.format(Locale.US, "%.2f", value)

@Preview(showBackground = true, name = "Currency – populated")
@Composable
private fun CurrencyTrackerContentPreview() {
    JetSetterTheme {
        CurrencyTrackerContent(
            state = CurrencytrackerUiState(
                currencies = listOf(
                    CurrencyRate("USD", "US Dollar", "🇺🇸", "$", 1.0, 0.0),
                    CurrencyRate("EUR", "Euro", "🇪🇺", "€", 0.9214, -0.12),
                    CurrencyRate("GBP", "British Pound", "🇬🇧", "£", 0.7843, 0.08),
                    CurrencyRate("JPY", "Japanese Yen", "🇯🇵", "¥", 157.32, 0.45),
                ),
                amountInput = "100",
                baseCurrencyCode = "USD",
                targetCurrencyCode = "EUR",
                lastUpdated = "Updated Jun 26, 2026 · 09:30",
            ),
            onAmountChange = {},
            onSelectBase = {},
            onSelectTarget = {},
            onSwap = {},
        )
    }
}
