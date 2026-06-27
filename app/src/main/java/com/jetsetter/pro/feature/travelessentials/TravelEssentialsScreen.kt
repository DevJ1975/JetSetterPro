package com.jetsetter.pro.feature.travelessentials

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Stateful entry point: owns the [TravelessentialsViewModel], collects its
 * [TravelessentialsUiState] lifecycle-aware, and forwards events. Holds no logic —
 * see [TravelEssentialsContent].
 */
@Composable
fun TravelEssentialsScreen(viewModel: TravelessentialsViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    TravelEssentialsContent(
        state = state,
        onSelectDestination = viewModel::selectDestination,
        onTogglePhrases = viewModel::togglePhrases,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable. Search text is transient draft state kept locally (mirrors the Itinerary sheet
 * pattern); the persisted selection lives in the ViewModel.
 */
@Composable
private fun TravelEssentialsContent(
    state: TravelessentialsUiState,
    onSelectDestination: (String) -> Unit,
    onTogglePhrases: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()
    val filtered = remember(query, state.destinations) {
        if (query.isEmpty()) {
            state.destinations
        } else {
            state.destinations.filter {
                it.city.contains(query, ignoreCase = true) ||
                    it.country.contains(query, ignoreCase = true) ||
                    it.languages.contains(query, ignoreCase = true)
            }
        }
    }

    // One-time fade + gentle slide-in on first composition. Draw-only (graphicsLayer), so it
    // never touches layout or scrolling. The background sits to the left of the layer, so only
    // the content fades — no window flash behind it.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entrance by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "entrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 12.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                "Travel Essentials",
                style = JetTheme.typography.displayTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (state.destinations.isNotEmpty()) {
                AccentTag(text = "${state.destinations.size} cities", icon = Icons.Filled.Language)
            }
        }

        when {
            state.isLoading -> LoadingCard()

            state.destinations.isEmpty() -> EmptyDestinationsCard()

            else -> {
                SearchField(value = search, onValueChange = { search = it })

                if (filtered.isEmpty()) {
                    NoMatchesCard(query = query)
                } else {
                    DestinationSelector(
                        destinations = filtered,
                        selectedId = state.selectedDestinationId,
                        onSelect = onSelectDestination,
                    )
                }

                val selected = state.selectedDestination
                if (selected != null) {
                    DestinationHeaderCard(destination = selected)
                    LocalTimeCard(destination = selected)
                    CurrencyConverterCard(destination = selected)
                    EssentialsCard(destination = selected)
                    EmergencyCard(destination = selected)
                    TapWaterCard(destination = selected)
                    PhrasesCard(
                        destination = selected,
                        expanded = state.phrasesExpanded,
                        onToggle = onTogglePhrases,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = JetTheme.colors
    Box(modifier = Modifier.fillMaxWidth()) {
        PremiumTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "Search a city, country or language",
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DestinationSelector(
    destinations: List<TravelEssentialsDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
    ) {
        destinations.forEach { destination ->
            val isSelected = destination.id == selectedId
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.surface)
                    .border(
                        0.6.dp,
                        if (isSelected) colors.accent.copy(alpha = 0.45f) else colors.separator,
                        CircleShape,
                    )
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(destination.id)
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(destination.flag, style = JetTheme.typography.bodyMedium)
                Text(
                    destination.city,
                    style = JetTheme.typography.label,
                    color = if (isSelected) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DestinationHeaderCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(destination.flag, style = typography.metric)
            Column(modifier = Modifier.weight(1f)) {
                Text(destination.city, style = typography.cardTitle, color = colors.textPrimary)
                Text(destination.country, style = typography.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTag(text = destination.languages, icon = Icons.Filled.Language)
            AccentTag(
                text = "${destination.timeZoneAbbrev} · ${utcOffsetLabel(zoneOffsetMinutes(destination.zoneId))}",
                icon = Icons.Filled.Schedule,
            )
        }
    }
}

/**
 * Live local-time card. The clock ticks from the device wall-clock; the displayed time, date,
 * UTC label and "ahead/behind you" gap are all derived from the destination's
 * [TravelEssentialsDestination.zoneId]. Both the destination and home offsets are resolved
 * DST-aware via java.time at the current instant, so summer time can never throw the gap off by
 * an hour — and both sides stay consistent with one another.
 */
@Composable
private fun LocalTimeCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val nowMillis by rememberNowTicker()
    val destOffsetMinutes = zoneOffsetMinutes(destination.zoneId)
    val homeOffsetMinutes = remember {
        ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60
    }
    val diff = destOffsetMinutes - homeOffsetMinutes

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.AccessTime, colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text("Local time in ${destination.city}", style = typography.caption, color = colors.textSecondary)
                Text(
                    destinationClock(nowMillis, destOffsetMinutes),
                    style = typography.pageTitle,
                    color = colors.textPrimary,
                )
                Text(
                    destinationDate(nowMillis, destOffsetMinutes),
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            StatusChip(
                text = timeDifferenceLabel(diff),
                color = if (diff == 0) colors.success else colors.blue,
            )
        }
    }
}

/**
 * Currency converter. The result is a real calculation: USD amount × the destination's
 * [TravelEssentialsDestination.exchangeRatePerUsd]. Handles empty (hint), invalid (validation)
 * and valid (result) input states, and resets the draft amount when the city changes.
 */
@Composable
private fun CurrencyConverterCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing

    var amount by rememberSaveable(destination.id) { mutableStateOf("100") }
    val cleaned = amount.replace(",", "").trim()
    val usd = cleaned.toDoubleOrNull()
    val isValid = usd != null && usd >= 0

    JetCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            "CURRENCY",
            trailing = "1 USD = ${formatMoney(destination.exchangeRatePerUsd, destination.currencySymbol, destination.currencyDecimals)} ${destination.currencyCode}",
        )
        Spacer(Modifier.height(spacing.small))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.CurrencyExchange, colors.success)
            Column(modifier = Modifier.weight(1f)) {
                Text("Amount in USD", style = typography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(spacing.xsmall))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\$", style = typography.cardTitle, color = colors.textSecondary)
                    PremiumTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = "100",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(spacing.small))
        RowDivider()
        Spacer(Modifier.height(spacing.small))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.SwapHoriz, colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text("You get in ${destination.currencyCode}", style = typography.caption, color = colors.textSecondary)
                when {
                    cleaned.isEmpty() -> Text(
                        "Enter an amount to convert",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                    )

                    !isValid -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(14.dp),
                        )
                        Text("Enter a valid amount", style = typography.bodyMedium, color = colors.danger)
                    }

                    else -> Text(
                        "${formatMoney(usd * destination.exchangeRatePerUsd, destination.currencySymbol, destination.currencyDecimals)} ${destination.currencyCode}",
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EssentialsCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    val tippingColor = when (destination.tippingNorm) {
        "Expected" -> colors.warning
        "Not customary" -> colors.success
        else -> colors.blue
    }
    JetCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("THE BASICS")
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        InfoRow(Icons.Filled.Power, colors.blue, "Power plug", destination.plugType)
        RowDivider()
        InfoRow(Icons.Filled.Bolt, colors.warning, "Voltage", destination.voltage)
        RowDivider()
        InfoRow(
            Icons.Filled.Payments,
            colors.success,
            "Currency",
            "${destination.currency} · ${destination.currencyCode} (${destination.currencySymbol})",
        )
        RowDivider()
        InfoRow(Icons.Filled.Paid, colors.accent, "Tipping", destination.tipping) {
            StatusChip(text = destination.tippingNorm, color = tippingColor)
        }
    }
}

@Composable
private fun EmergencyCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("EMERGENCY")
        Spacer(Modifier.height(JetTheme.spacing.xsmall))
        InfoRow(Icons.Filled.Warning, colors.danger, "All emergencies", destination.emergencyGeneral)
        RowDivider()
        InfoRow(Icons.Filled.LocalPolice, colors.danger, "Police", destination.emergencyPolice)
        RowDivider()
        InfoRow(Icons.Filled.LocalHospital, colors.danger, "Ambulance / Medical", destination.emergencyMedical)
    }
}

@Composable
private fun TapWaterCard(destination: TravelEssentialsDestination) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Icons.Filled.WaterDrop, if (destination.tapWaterSafe) colors.blue else colors.warning)
            Column(modifier = Modifier.weight(1f)) {
                Text("Tap water", style = typography.caption, color = colors.textSecondary)
                Text(destination.tapWaterNote, style = typography.bodyMedium, color = colors.textPrimary)
            }
            StatusChip(
                text = if (destination.tapWaterSafe) "Safe" else "Bottled",
                color = if (destination.tapWaterSafe) colors.success else colors.warning,
                icon = Icons.Outlined.LocalDrink,
            )
        }
    }
}

@Composable
private fun PhrasesCard(
    destination: TravelEssentialsDestination,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Icons.Filled.Translate, colors.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("Useful phrases", style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${destination.phrases.size} to know in ${destination.languages}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            destination.phrases.forEachIndexed { index, phrase ->
                if (index > 0) RowDivider()
                PhraseRow(phrase)
            }
        }
    }
}

@Composable
private fun PhraseRow(phrase: TravelEssentialsPhrase) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(phrase.phrase, style = typography.bodyMedium, color = colors.textPrimary)
            if (phrase.pronunciation != "—") {
                Text(phrase.pronunciation, style = typography.caption, color = colors.textSecondary)
            }
        }
        Text(phrase.meaning, style = typography.caption, color = colors.textSecondary)
    }
}

// --- Small reusable pieces -------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    val colors = JetTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = JetTheme.typography.label, color = colors.textSecondary, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = JetTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.caption, color = colors.textSecondary)
            Text(value, style = typography.bodyMedium, color = colors.textPrimary)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        thickness = 0.5.dp,
        color = JetTheme.colors.separator.copy(alpha = 0.5f),
    )
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Colored capsule tag — like [AccentTag] but tintable, for status (Safe / Bottled / time gap). */
@Composable
private fun StatusChip(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(text, style = JetTheme.typography.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoadingCard() {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(20.dp))
            Text("Loading destination essentials…", style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun EmptyDestinationsCard() {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.Language, colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text("No destinations yet", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(JetTheme.spacing.xsmall))
                Text(
                    "Destination essentials will appear here once they're available.",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun NoMatchesCard(query: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Filled.SearchOff, colors.textSecondary)
            Column(modifier = Modifier.weight(1f)) {
                Text("No cities match", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Text(
                    "Nothing found for \"$query\". Try another city, country or language.",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// --- Pure helpers (no Compose) — easy to reason about and self-consistent --------------------

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * DST-aware UTC offset (in minutes) for an IANA [zoneId], resolved at the current instant via
 * java.time — so e.g. Paris is +120 in summer (CEST) and +60 in winter (CET). Both the destination
 * clock and the home zone use this, keeping the "ahead/behind you" gap correct year-round.
 */
internal fun zoneOffsetMinutes(zoneId: String): Int =
    ZoneId.of(zoneId).rules.getOffset(Instant.now()).totalSeconds / 60

/** "UTC", "UTC+9", "UTC-5:30" — derived from the stored offset so it can't drift from the clock. */
internal fun utcOffsetLabel(offsetMinutes: Int): String {
    if (offsetMinutes == 0) return "UTC"
    val sign = if (offsetMinutes > 0) "+" else "-"
    val magnitude = abs(offsetMinutes)
    val hours = magnitude / 60
    val minutes = magnitude % 60
    return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
}

/** Wall-clock time at [offsetMinutes] for the instant [nowMillis], e.g. "3:45 PM". */
internal fun destinationClock(nowMillis: Long, offsetMinutes: Int): String =
    formatInUtc(nowMillis + offsetMinutes * MILLIS_PER_MINUTE, "h:mm a")

/** Wall-clock date at [offsetMinutes], e.g. "Fri, Jun 26". */
internal fun destinationDate(nowMillis: Long, offsetMinutes: Int): String =
    formatInUtc(nowMillis + offsetMinutes * MILLIS_PER_MINUTE, "EEE, MMM d")

private fun formatInUtc(shiftedMillis: Long, pattern: String): String {
    val formatter = SimpleDateFormat(pattern, Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(shiftedMillis))
}

/** Human gap between the destination and the device's own zone, e.g. "9h ahead of you". */
internal fun timeDifferenceLabel(diffMinutes: Int): String {
    if (diffMinutes == 0) return "Same time as you"
    val magnitude = abs(diffMinutes)
    val hours = magnitude / 60
    val minutes = magnitude % 60
    val gap = when {
        minutes == 0 -> "${hours}h"
        hours == 0 -> "${minutes}m"
        else -> "${hours}h ${minutes}m"
    }
    return if (diffMinutes > 0) "$gap ahead of you" else "$gap behind you"
}

/** Formats a converted amount with grouping and the right number of fraction digits. */
internal fun formatMoney(amount: Double, symbol: String, decimals: Int): String =
    symbol + String.format(Locale.US, "%,.${decimals}f", amount)

/** Emits the current time once per second so the local-time clock stays live. */
@Composable
private fun rememberNowTicker(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1_000)
    }
}

@Preview(showBackground = true, name = "Travel Essentials – Tokyo")
@Composable
private fun TravelEssentialsContentPreview() {
    val tokyo = TravelEssentialsDestination(
        id = "tokyo",
        city = "Tokyo",
        country = "Japan",
        flag = "🇯🇵",
        languages = "Japanese",
        timeZoneAbbrev = "JST",
        zoneId = "Asia/Tokyo",
        plugType = "Type A / Type B",
        voltage = "100 V · 50/60 Hz",
        currency = "Japanese Yen",
        currencyCode = "JPY",
        currencySymbol = "¥",
        currencyDecimals = 0,
        exchangeRatePerUsd = 157.0,
        emergencyGeneral = "110 / 119",
        emergencyPolice = "110",
        emergencyMedical = "119 (fire & ambulance)",
        tippingNorm = "Not customary",
        tipping = "Not customary — tipping can even be seen as rude.",
        tapWaterSafe = true,
        tapWaterNote = "Tap water is clean and safe to drink nationwide.",
        phrases = listOf(
            TravelEssentialsPhrase("Konnichiwa", "kon-nee-chee-wah", "Hello"),
            TravelEssentialsPhrase("Arigatou gozaimasu", "ah-ree-gah-toh go-zai-mas", "Thank you"),
        ),
    )
    val paris = tokyo.copy(
        id = "paris",
        city = "Paris",
        country = "France",
        flag = "🇫🇷",
        languages = "French",
        timeZoneAbbrev = "CET",
        zoneId = "Europe/Paris",
        currency = "Euro",
        currencyCode = "EUR",
        currencySymbol = "€",
        currencyDecimals = 2,
        exchangeRatePerUsd = 0.92,
        tippingNorm = "Optional",
    )
    JetSetterTheme {
        TravelEssentialsContent(
            state = TravelessentialsUiState(
                destinations = listOf(tokyo, paris),
                selectedDestinationId = "tokyo",
                phrasesExpanded = true,
            ),
            onSelectDestination = {},
            onTogglePhrases = {},
        )
    }
}
