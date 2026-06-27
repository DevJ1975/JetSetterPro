package com.jetsetter.pro.feature.subscription

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stateful entry point: owns the [SubscriptionViewModel], collects its [SubscriptionUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [SubscriptionContent].
 */
@Composable
fun SubscriptionScreen(viewModel: SubscriptionViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    SubscriptionContent(
        state = state,
        onSelectPlan = viewModel::selectPlan,
        onSubscribe = viewModel::subscribe,
        onCancel = viewModel::cancel,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun SubscriptionContent(
    state: SubscriptionUiState,
    onSelectPlan: (String) -> Unit,
    onSubscribe: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    // One-time entrance: fade + gentle slide-up on first composition (~250ms). Purely visual —
    // applied via graphicsLayer so it never interferes with scrolling.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "subscriptionContentAlpha",
    )
    val contentTranslationY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 24f,
        animationSpec = tween(durationMillis = 250),
        label = "subscriptionContentSlide",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentTranslationY
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("JetSetter Pro", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            if (state.isProActive) {
                ProActiveBanner(
                    plan = state.activePlan,
                    renewalDateIso = state.activeRenewalDateIso,
                )
            }

            HeroCard(betaUnlocked = state.betaUnlocked, isProActive = state.isProActive)

            FeatureListCard(features = state.features)

            if (state.plans.isNotEmpty()) {
                Text(
                    if (state.isProActive) "MANAGE PLAN" else "CHOOSE A PLAN",
                    style = JetTheme.typography.label,
                    color = colors.textSecondary,
                )

                val today = runCatching { LocalDate.parse(state.todayIso) }.getOrElse { LocalDate.now() }
                state.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        quote = SubscriptionMath.quote(plan, state.plans, today),
                        selected = plan.id == state.selectedPlanId,
                        isActive = state.isProActive && plan.id == state.activePlanId,
                        enabled = !state.isProcessing,
                        onClick = { onSelectPlan(plan.id) },
                    )
                }
            }

            if (!state.isProActive) {
                state.selectedQuote?.let { OrderSummaryCard(it) }
            }

            if (state.isProActive) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
                    enabled = !state.isProcessing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Cancel Pro (mock)", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSubscribe()
                    },
                    enabled = state.selectedPlan != null && !state.isProcessing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White,
                        disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f),
                    ),
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Processing…", style = JetTheme.typography.cardTitle, color = Color.White)
                    } else {
                        Text(subscribeLabel(state), style = JetTheme.typography.cardTitle, color = Color.White)
                    }
                }
            }

            Text(
                "Beta build — billing is simulated. No card is charged and every Pro feature is already unlocked.",
                style = JetTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

private fun subscribeLabel(state: SubscriptionUiState): String {
    val plan = state.selectedPlan ?: return "Subscribe"
    return "Subscribe · ${money(plan.price)}${plan.pricePeriod}"
}

@Composable
private fun ProActiveBanner(plan: SubscriptionPlan?, renewalDateIso: String?) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Pro active", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Text(
                    if (plan != null) {
                        "${plan.title} plan · ${money(plan.price)}${plan.pricePeriod}"
                    } else {
                        "Every premium feature is on."
                    },
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            AccentTag(text = "ACTIVE", icon = Icons.Filled.Bolt)
        }
        renewalDateIso?.let { iso ->
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Renews ${formatDate(iso)}",
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(betaUnlocked: Boolean, isProActive: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Travel without limits", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "Unlock the full concierge — IRIS, vaults, and exports.",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
        if (betaUnlocked || isProActive) {
            Spacer(Modifier.height(12.dp))
            AccentTag(
                text = if (isProActive) "PRO UNLOCKED" else "BETA — ALL FEATURES UNLOCKED",
                icon = Icons.Filled.LockOpen,
            )
        }
    }
}

@Composable
private fun FeatureListCard(features: List<SubscriptionFeature>) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Text("What you get", style = typography.cardTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AccentTag(text = "${features.size} INCLUDED")
        }
        if (features.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "Feature list is on its way.",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        } else {
            features.forEachIndexed { index, feature ->
                if (index == 0) Spacer(Modifier.height(12.dp)) else Divider()
                FeatureRow(feature)
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(0.6.dp)
            .background(JetTheme.colors.separator),
    )
}

@Composable
private fun FeatureRow(feature: SubscriptionFeature) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                featureIcon(feature.id),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, style = typography.bodyMedium, color = colors.textPrimary)
            Text(feature.description, style = typography.caption, color = colors.textSecondary)
        }
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun featureIcon(id: String): ImageVector = when (id) {
    "tracking" -> Icons.Filled.FlightTakeoff
    "disruption" -> Icons.Filled.Bolt
    "vaults" -> Icons.Filled.Lock
    "exports" -> Icons.AutoMirrored.Filled.ReceiptLong
    else -> Icons.Filled.Check
}

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    quote: SubscriptionQuote,
    selected: Boolean,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val borderColor = if (selected) colors.accent else colors.separator
    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .border(if (selected) 1.5.dp else 0.6.dp, borderColor, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.title, style = typography.cardTitle, color = colors.textPrimary)
                    if (isActive) {
                        AccentTag(text = "CURRENT")
                    } else if (plan.isBestValue) {
                        AccentTag(text = "BEST VALUE")
                    }
                }
                Text(plan.tagline, style = typography.caption, color = colors.textSecondary)
                // Per-month equivalent is only meaningful when the cycle isn't already monthly.
                if (plan.cycle.months > 1) {
                    Text(
                        "${money(quote.pricePerMonth)}/mo billed ${plan.cycle.label.lowercase(Locale.US)}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(plan.price) + plan.pricePeriod,
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                if (quote.hasSavings) {
                    Text(
                        "Save ${quote.savingsPercent}%",
                        style = typography.caption,
                        color = colors.success,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryCard(quote: SubscriptionQuote) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Order summary", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        SummaryRow(
            icon = Icons.Filled.Payments,
            label = "Due today",
            value = money(quote.dueToday) + quote.plan.pricePeriod,
        )
        Spacer(Modifier.height(10.dp))
        SummaryRow(
            icon = Icons.Filled.CalendarMonth,
            label = "Renews ${formatDate(quote.renewalDateIso)}",
            value = money(quote.dueToday) + quote.plan.pricePeriod,
        )
        if (quote.hasSavings) {
            Spacer(Modifier.height(10.dp))
            SummaryRow(
                icon = Icons.Filled.LocalOffer,
                label = "You save vs. monthly",
                value = "${money(quote.savingsPerYear)}/yr",
                valueColor = colors.success,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Cancel anytime before ${formatDate(quote.renewalDateIso)} and you won't be charged again.",
            style = typography.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = JetTheme.colors.textPrimary,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

private fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
}.getOrDefault(iso)

@Preview(showBackground = true, name = "Subscription – paywall")
@Composable
private fun SubscriptionContentPreview() {
    JetSetterTheme {
        SubscriptionContent(
            state = SubscriptionUiState(
                features = listOf(
                    SubscriptionFeature("tracking", "Unlimited flight tracking", "Follow every leg in real time."),
                    SubscriptionFeature("disruption", "Disruption auto-response", "IRIS rebooks the moment a flight slips."),
                    SubscriptionFeature("vaults", "Document vaults", "Encrypted passports, visas, and receipts."),
                    SubscriptionFeature("exports", "Expense exports", "One-tap exports to Brex, Ramp, and more."),
                ),
                plans = listOf(
                    SubscriptionPlan(
                        id = "monthly",
                        cycle = SubscriptionBillingCycle.MONTHLY,
                        title = "Monthly",
                        price = 9.99,
                        tagline = "Flexible — cancel anytime.",
                    ),
                    SubscriptionPlan(
                        id = "annual",
                        cycle = SubscriptionBillingCycle.ANNUAL,
                        title = "Annual",
                        price = 79.99,
                        tagline = "Billed once a year.",
                        isBestValue = true,
                    ),
                ),
                selectedPlanId = "annual",
                todayIso = "2026-06-26",
            ),
            onSelectPlan = {},
            onSubscribe = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true, name = "Subscription – Pro active")
@Composable
private fun SubscriptionProActivePreview() {
    JetSetterTheme {
        SubscriptionContent(
            state = SubscriptionUiState(
                isProActive = true,
                features = listOf(
                    SubscriptionFeature("tracking", "Unlimited flight tracking", "Follow every leg in real time."),
                    SubscriptionFeature("vaults", "Document vaults", "Encrypted passports, visas, and receipts."),
                ),
                plans = listOf(
                    SubscriptionPlan(
                        id = "annual",
                        cycle = SubscriptionBillingCycle.ANNUAL,
                        title = "Annual",
                        price = 79.99,
                        tagline = "Billed once a year.",
                        isBestValue = true,
                    ),
                ),
                selectedPlanId = "annual",
                activePlanId = "annual",
                subscribedOnIso = "2026-06-26",
                todayIso = "2026-06-26",
            ),
            onSelectPlan = {},
            onSubscribe = {},
            onCancel = {},
        )
    }
}
