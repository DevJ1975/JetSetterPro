package com.jetsetter.pro.feature.visalookup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
 * Stateful entry point: owns the [VisalookupViewModel], collects its [VisalookupUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [VisaLookupContent].
 */
@Composable
fun VisaLookupScreen(viewModel: VisalookupViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    VisaLookupContent(
        state = state,
        onSelectDestination = viewModel::selectDestination,
        onClearSelection = viewModel::clearSelection,
        onQueryChange = viewModel::onQueryChange,
        onTripLengthChange = viewModel::onTripLengthChange,
        onPassportExpiryChange = viewModel::onPassportExpiryChange,
        onToggleDocument = viewModel::toggleDocument,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun VisaLookupContent(
    state: VisalookupUiState,
    onSelectDestination: (String) -> Unit,
    onClearSelection: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTripLengthChange: (String) -> Unit,
    onPassportExpiryChange: (String) -> Unit,
    onToggleDocument: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + subtle slide-in on first composition (~250ms). Purely visual;
    // applied above the scroll modifier so gestures and content are unaffected once settled.
    // Start visible inside @Preview/inspection where the animation clock doesn't advance.
    val inInspection = LocalInspectionMode.current
    var contentVisible by remember { mutableStateOf(inInspection) }
    LaunchedEffect(Unit) { contentVisible = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "visaEntranceAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .graphicsLayer {
                alpha = entranceAlpha
                translationY = (1f - entranceAlpha) * 12.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Visa & Entry", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
        Text(
            "Entry requirements for U.S. passport holders",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier
                    .padding(top = spacing.large)
                    .align(Alignment.CenterHorizontally),
            )
        } else {
            DestinationPicker(
                destinations = state.filteredDestinations,
                selectedId = state.selectedId,
                query = state.query,
                onQueryChange = onQueryChange,
                onSelect = onSelectDestination,
            )

            val selected = state.selected
            if (selected == null) {
                SelectDestinationEmptyState()
            } else {
                EntryRequirementCard(item = selected, onChange = onClearSelection)
                TripCheckCard(
                    tripLengthDays = state.tripLengthDays,
                    passportExpiry = state.passportExpiry,
                    stayCheck = state.stayCheck,
                    passportCheck = state.passportCheck,
                    onTripLengthChange = onTripLengthChange,
                    onPassportExpiryChange = onPassportExpiryChange,
                )
                state.readiness?.let { readiness ->
                    DocumentChecklistCard(
                        documents = selected.documents,
                        checked = state.selectedCheckedDocs,
                        readiness = readiness,
                        country = selected.country,
                        onToggle = onToggleDocument,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationPicker(
    destinations: List<VisaEntryItem>,
    selectedId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("DESTINATION", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        PremiumTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search country or region",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.small))
        if (destinations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    "No countries match \"${query.trim()}\".",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            destinations.forEach { item ->
                DestinationRow(
                    item = item,
                    selected = item.id == selectedId,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun DestinationRow(item: VisaEntryItem, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.flag, style = typography.cardTitle)
        Column(modifier = Modifier.weight(1f)) {
            Text(item.country, style = typography.bodyMedium, color = colors.textPrimary)
            Text(
                "${item.region} · ${item.status.label}",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SelectDestinationEmptyState() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text("Select a destination", style = typography.cardTitle, color = colors.textPrimary)
            Text(
                "Pick a country above to see its entry rules, visa cost, and a personalized document " +
                    "checklist for your U.S. passport.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EntryRequirementCard(item: VisaEntryItem, onChange: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.flag, style = typography.metric)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.country, style = typography.cardTitle, color = colors.textPrimary)
                Text(item.region, style = typography.caption, color = colors.textSecondary)
            }
            IconButton(onClick = onChange, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Change destination",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTag(text = item.status.label, icon = statusIcon(item.status))
            if (item.onwardTicketRequired) {
                AccentTag(text = "Onward ticket", icon = Icons.Outlined.FlightTakeoff)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Prominent visa required yes/no indicator, derived from the status.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val required = item.status.requiresVisa
            Icon(
                imageVector = if (required) Icons.Outlined.Cancel else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (required) colors.warning else colors.success,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (required) "Visa required: Yes" else "Visa required: No",
                style = typography.bodyMedium,
                color = if (required) colors.warning else colors.success,
            )
        }

        Spacer(Modifier.height(14.dp))
        Divider()
        Spacer(Modifier.height(14.dp))

        DetailRow(icon = Icons.Outlined.Schedule, label = "Maximum stay", value = item.maxStayText)
        Spacer(Modifier.height(12.dp))
        DetailRow(icon = Icons.Outlined.Badge, label = "Passport validity", value = item.passportValidityText)
        Spacer(Modifier.height(12.dp))
        DetailRow(icon = Icons.Filled.Payments, label = "Visa fee", value = item.feeText)
        Spacer(Modifier.height(12.dp))
        DetailRow(icon = Icons.Outlined.AccessTime, label = "Processing", value = item.processingText)
        Spacer(Modifier.height(12.dp))
        DetailRow(
            icon = Icons.Outlined.FlightTakeoff,
            label = "Onward ticket",
            value = if (item.onwardTicketRequired) "Required" else "Not required",
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.blue,
                modifier = Modifier.size(16.dp),
            )
            Text(item.notes, style = typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun TripCheckCard(
    tripLengthDays: String,
    passportExpiry: String,
    stayCheck: VisaCheck?,
    passportCheck: VisaCheck?,
    onTripLengthChange: (String) -> Unit,
    onPassportExpiryChange: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("CHECK YOUR TRIP", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Trip length (days)")
                Spacer(Modifier.height(6.dp))
                PremiumTextField(
                    value = tripLengthDays,
                    onValueChange = onTripLengthChange,
                    placeholder = "e.g. 10",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Passport expiry")
                Spacer(Modifier.height(6.dp))
                PremiumTextField(
                    value = passportExpiry,
                    onValueChange = onPassportExpiryChange,
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        stayCheck?.let { CheckRow(it) }
        passportCheck?.let {
            Spacer(Modifier.height(10.dp))
            CheckRow(it)
        }
    }
}

@Composable
private fun DocumentChecklistCard(
    documents: List<String>,
    checked: List<String>,
    readiness: VisaReadiness,
    country: String,
    onToggle: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DOCUMENT CHECKLIST",
                style = typography.caption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${readiness.ready} of ${readiness.total} ready · ${readiness.percent}%",
                style = typography.caption,
                color = if (readiness.allReady) colors.success else colors.accent,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressTrack(fraction = readiness.fraction, complete = readiness.allReady)
        Spacer(Modifier.height(8.dp))

        documents.forEach { doc ->
            DocumentRow(
                text = doc,
                checked = doc in checked,
                onClick = { onToggle(doc) },
            )
        }

        if (readiness.allReady) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "You're document-ready for $country.",
                    style = typography.bodyMedium,
                    color = colors.success,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: VisaCheck) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val tint = when (check.severity) {
        CheckSeverity.PASS -> colors.success
        CheckSeverity.WARN -> colors.warning
        CheckSeverity.FAIL -> colors.danger
        CheckSeverity.NEUTRAL -> colors.textSecondary
    }
    val icon = when (check.severity) {
        CheckSeverity.PASS -> Icons.Outlined.CheckCircle
        CheckSeverity.WARN -> Icons.Filled.WarningAmber
        CheckSeverity.FAIL -> Icons.Outlined.Cancel
        CheckSeverity.NEUTRAL -> Icons.Outlined.Info
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(check.message, style = typography.bodyMedium, color = tint, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = typography.caption, color = colors.textSecondary)
            Text(value, style = typography.bodyMedium, color = colors.textPrimary)
        }
    }
}

@Composable
private fun DocumentRow(text: String, checked: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = if (checked) "Ready" else "Not ready"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) colors.success else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = typography.bodyMedium,
            color = if (checked) colors.textSecondary else colors.textPrimary,
        )
    }
}

@Composable
private fun ProgressTrack(fraction: Float, complete: Boolean) {
    val colors = JetTheme.colors
    // Ease the fill toward the exact computed fraction so it reveals instead of snapping.
    // Target is the same coerced value used before — the readiness math is unchanged.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "visaReadinessFraction",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.separator),
    ) {
        if (animatedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (complete) colors.success else colors.accent),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun Divider() {
    val colors = JetTheme.colors
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
}

private fun statusIcon(status: VisaRequirementStatus): ImageVector = when (status) {
    VisaRequirementStatus.VISA_FREE -> Icons.Filled.CheckCircle
    VisaRequirementStatus.VISA_ON_ARRIVAL -> Icons.Outlined.FlightTakeoff
    VisaRequirementStatus.ETA_REQUIRED -> Icons.Outlined.Language
    VisaRequirementStatus.VISA_REQUIRED -> Icons.Outlined.Description
}

private val previewDestinations = listOf(
    VisaEntryItem(
        id = "jp",
        country = "Japan",
        flag = "🇯🇵",
        region = "Asia",
        status = VisaRequirementStatus.VISA_FREE,
        maxStayDays = 90,
        passportValidityMonths = 0,
        visaFeeUsd = 0,
        processingDays = 0,
        onwardTicketRequired = true,
        notes = "Short-term tourism is visa-exempt. Keep proof of onward travel handy.",
        documents = listOf("Passport valid for your stay", "Return or onward ticket", "Proof of accommodation"),
    ),
    VisaEntryItem(
        id = "br",
        country = "Brazil",
        flag = "🇧🇷",
        region = "South America",
        status = VisaRequirementStatus.ETA_REQUIRED,
        maxStayDays = 90,
        passportValidityMonths = 6,
        visaFeeUsd = 81,
        processingDays = 5,
        onwardTicketRequired = true,
        notes = "An e-Visa is required for U.S. citizens — apply before travel.",
        documents = listOf("Approved e-Visa", "Passport valid 6 months", "Recent passport photo"),
    ),
)

@Preview(showBackground = true, name = "Visa & Entry – selected")
@Composable
private fun VisaLookupSelectedPreview() {
    JetSetterTheme {
        VisaLookupContent(
            state = VisalookupUiState(
                destinations = previewDestinations,
                selectedId = "br",
                tripLengthDays = "100",
                passportExpiry = "2026-09-30",
                checkedDocs = mapOf("br" to listOf("Approved e-Visa")),
            ),
            onSelectDestination = {},
            onClearSelection = {},
            onQueryChange = {},
            onTripLengthChange = {},
            onPassportExpiryChange = {},
            onToggleDocument = {},
        )
    }
}

@Preview(showBackground = true, name = "Visa & Entry – empty")
@Composable
private fun VisaLookupEmptyPreview() {
    JetSetterTheme {
        VisaLookupContent(
            state = VisalookupUiState(destinations = previewDestinations, selectedId = null),
            onSelectDestination = {},
            onClearSelection = {},
            onQueryChange = {},
            onTripLengthChange = {},
            onPassportExpiryChange = {},
            onToggleDocument = {},
        )
    }
}
