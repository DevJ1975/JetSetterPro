package com.jetsetter.pro.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.Flight
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.secrets.Secrets
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.atan2

/** Stateful entry point: owns the [HomeViewModel], collects state lifecycle-aware, delegates render. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenFlightTracker: () -> Unit = {},
    onOpenFeature: (String) -> Unit = {},
    onOpenExpenses: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onOpenFlightTracker = onOpenFlightTracker,
        onOpenFeature = onOpenFeature,
        onOpenExpenses = onOpenExpenses,
        onDismissAlert = viewModel::dismissAlert,
        onAlertClick = viewModel::onAlertClick,
    )
}

/** Stateless content: a pure function of [state], so it's trivially previewable and testable. */
@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenFlightTracker: () -> Unit = {},
    onOpenFeature: (String) -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onDismissAlert: (String) -> Unit = {},
    onAlertClick: (HomeAlert) -> Unit = {},
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + subtle slide-in on first composition (~250ms). In a @Preview
    // (no animation clock) we start "entered" so the static render shows the full content.
    var entered by remember { mutableStateOf(false) }
    val inInspection = LocalInspectionMode.current
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered || inInspection) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "homeEntrance",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // Layer is placed after the background so only the content fades/slides, never the
            // screen backdrop (no flash). Render-only transform — does not affect scroll.
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 12.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        HomeHeader()
        if (state.alerts.isNotEmpty()) {
            AlertsCard(alerts = state.alerts, onDismiss = onDismissAlert, onClick = onAlertClick)
        }
        NextFlightCard(state.nextFlight)
        FlightMapCard(flight = state.nextFlight, onClick = onOpenFlightTracker)
        QuickActionsRow(onOpenFeature = onOpenFeature, onOpenExpenses = onOpenExpenses)
        state.expenseSummary?.let { summary ->
            ExpenseSummaryCard(summary = summary, onClick = onOpenExpenses)
        }
        TravelIntelligenceCard(state.upcomingTrip?.destination)
    }
}

@Composable
private fun HomeHeader() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column {
        Text("Good morning", style = typography.bodyMedium, color = colors.textSecondary)
        Text("Welcome aboard", style = typography.displayTitle, color = colors.textPrimary)
    }
}

@Composable
private fun NextFlightCard(flight: Flight) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = colors.accent)
                Text(flight.ident, style = typography.cardTitle, color = colors.textPrimary)
            }
            AccentTag(text = flight.status, icon = Icons.Filled.Schedule)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AirportColumn(flight.origin.code, flight.origin.city, alignEnd = false)
            Icon(Icons.Filled.Flight, contentDescription = null, tint = colors.textSecondary)
            AirportColumn(flight.destination.code, flight.destination.city, alignEnd = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InfoPair("Gate", flight.gateOrigin ?: "—")
            InfoPair("Terminal", flight.terminalOrigin ?: "—")
            InfoPair("Airline", flight.operatorName ?: "—")
        }
    }
}

@Composable
private fun AirportColumn(code: String, city: String, alignEnd: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(code, style = typography.metric, color = colors.textPrimary)
        Text(city, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = typography.caption, color = colors.textSecondary)
        Text(value, style = typography.cardTitle, color = colors.textPrimary)
    }
}

@Composable
private fun QuickActionsRow(
    onOpenFeature: (String) -> Unit,
    onOpenExpenses: () -> Unit,
) {
    // Each tile carries its own destination so the row is fully wired to navigation.
    val actions = listOf(
        Triple("Check-In", Icons.Filled.HowToReg, { onOpenFeature("checkin") }),
        Triple("Disruptions", Icons.Filled.Warning, { onOpenFeature("disruption") }),
        Triple("Expenses", Icons.AutoMirrored.Filled.ReceiptLong, onOpenExpenses),
        Triple("Wallet", Icons.Filled.Wallet, { onOpenFeature("travelwallet") }),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEach { (label, icon, onClick) ->
            QuickAction(label, icon, onClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .background(colors.surface)
            .border(0.6.dp, colors.separator, RoundedCornerShape(16.dp))
            .semantics { role = Role.Button }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

/** Rolled-up "this trip" spend with the biggest category called out. Tap → Expenses. */
@Composable
private fun ExpenseSummaryCard(summary: ExpenseSummary, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = colors.accent)
                Text("This trip", style = typography.cardTitle, color = colors.textPrimary)
            }
            AccentTag(text = "Expenses")
        }
        Spacer(Modifier.height(12.dp))
        Text(formatMoney(summary.total, summary.currency), style = typography.metric, color = colors.textPrimary)
        Text("Total logged so far", style = typography.caption, color = colors.textSecondary)
        if (summary.topCategory != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Top category · ${summary.topCategory.label}",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    formatMoney(summary.topCategoryAmount, summary.currency),
                    style = typography.cardTitle,
                    color = colors.accent,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap to open Expenses", style = typography.caption, color = colors.accent)
    }
}

/** Proactive, dismissible heads-ups (gate changes, weather…) surfaced at the top of the dashboard. */
@Composable
private fun AlertsCard(
    alerts: List<HomeAlert>,
    onDismiss: (String) -> Unit,
    onClick: (HomeAlert) -> Unit = {},
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = colors.accent)
            Text("Alerts", style = typography.cardTitle, color = colors.textPrimary)
        }
        alerts.forEach { alert ->
            Spacer(Modifier.height(12.dp))
            AlertRow(alert = alert, onDismiss = { onDismiss(alert.id) }, onClick = { onClick(alert) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertRow(alert: HomeAlert, onDismiss: () -> Unit, onClick: () -> Unit = {}) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val tint = when (alert.severity) {
        AlertSeverity.WARNING -> colors.warning
        AlertSeverity.INFO -> colors.blue
    }
    val icon = when (alert.severity) {
        AlertSeverity.WARNING -> Icons.Filled.Warning
        AlertSeverity.INFO -> Icons.Filled.Info
    }
    // The body is tappable only when the alert carries an IRIS deep-link prompt.
    val clickableBody = if (alert.promptToIris != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).then(clickableBody)) {
            Text(alert.title, style = typography.bodyMedium, color = colors.textPrimary)
            Text(alert.message, style = typography.caption, color = colors.textSecondary)
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss ${alert.title}",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TravelIntelligenceCard(destination: String?) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accent)
            Text("Travel Intelligence", style = typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (destination != null) {
                "Your next trip is to $destination. IRIS will watch your flights and flag disruptions automatically."
            } else {
                "Add a trip and IRIS will start watching your flights, weather, and expenses."
            },
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/** Currency formatting that respects the expense currency code, falling back gracefully. */
private fun formatMoney(amount: Double, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(amount)
}

/**
 * "Live flight map": renders the flight route on a **real Google Map** when `MAPS_API_KEY` is
 * configured (Maps SDK for Android), and falls back to a Compose-drawn route arc otherwise so the
 * card always looks good. The aircraft sits along the path by flight progress. Tap → Flight Tracker.
 */
@Composable
private fun FlightMapCard(flight: Flight, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val accent = colors.accent
    val haptics = LocalHapticFeedback.current

    JetCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = accent)
                Text("Live Flight Map", style = typography.cardTitle, color = colors.textPrimary)
            }
            AccentTag(text = flight.status, icon = Icons.Filled.Schedule)
        }
        Spacer(Modifier.height(12.dp))
        if (Secrets.isConfigured(Secrets.mapsApiKey)) {
            FlightGoogleMap(
                flight = flight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            FlightMapCanvas(
                flight = flight,
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(flight.origin.code, style = typography.cardTitle, color = colors.textPrimary)
                Text(flight.origin.city, style = typography.caption, color = colors.textSecondary)
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(flight.destination.code, style = typography.cardTitle, color = colors.textPrimary)
                Text(flight.destination.city, style = typography.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap to open Flight Tracker", style = typography.caption, color = accent)
    }
}

/** Polished Compose-drawn route map — the fallback shown when Maps isn't available or hasn't loaded. */
@Composable
private fun FlightMapCanvas(flight: Flight, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val accent = colors.accent
    val land = colors.surfaceElevated
    val grid = colors.separator.copy(alpha = 0.5f)
    // Target stays exactly as computed; we just ease the aircraft from the origin to it once.
    val targetProgress = (flight.progressPercent / 100f).let { if (it <= 0f) 0.42f else it }.coerceIn(0.05f, 0.95f)
    val inInspection = LocalInspectionMode.current
    var animateIn by remember { mutableStateOf(inInspection) }
    LaunchedEffect(Unit) { animateIn = true }
    val progress by animateFloatAsState(
        targetValue = if (animateIn) targetProgress else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "flightProgress",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = land, cornerRadius = CornerRadius(16.dp.toPx()))
        var x = w / 6f
        while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += w / 6f }
        var y = h / 4f
        while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f); y += h / 4f }

        val p0 = Offset(w * 0.12f, h * 0.74f)
        val p1 = Offset(w * 0.88f, h * 0.50f)
        val ctrl = Offset(w * 0.50f, h * 0.08f)
        val route = Path().apply { moveTo(p0.x, p0.y); quadraticTo(ctrl.x, ctrl.y, p1.x, p1.y) }
        drawPath(route, color = accent, style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))

        drawCircle(accent, radius = 9.dp.toPx(), center = p0)
        drawCircle(Color.White, radius = 4.dp.toPx(), center = p0)
        drawCircle(accent, radius = 9.dp.toPx(), center = p1)
        drawCircle(Color.White, radius = 4.dp.toPx(), center = p1)

        val t = progress
        val mt = 1f - t
        val pos = Offset(
            mt * mt * p0.x + 2 * mt * t * ctrl.x + t * t * p1.x,
            mt * mt * p0.y + 2 * mt * t * ctrl.y + t * t * p1.y,
        )
        val tan = Offset(
            2 * mt * (ctrl.x - p0.x) + 2 * t * (p1.x - ctrl.x),
            2 * mt * (ctrl.y - p0.y) + 2 * t * (p1.y - ctrl.y),
        )
        val angle = Math.toDegrees(atan2(tan.y.toDouble(), tan.x.toDouble())).toFloat()
        rotate(degrees = angle, pivot = pos) {
            val s = 11.dp.toPx()
            val plane = Path().apply {
                moveTo(pos.x + s, pos.y)
                lineTo(pos.x - s, pos.y - s * 0.7f)
                lineTo(pos.x - s * 0.35f, pos.y)
                lineTo(pos.x - s, pos.y + s * 0.7f)
                close()
            }
            drawPath(plane, color = Color.White)
            drawPath(plane, color = accent, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

/**
 * Real Google Map (route polyline + endpoint/aircraft markers). [FlightMapCanvas] is overlaid
 * until [GoogleMap]'s `onMapLoaded` fires — so on a Maps auth failure (which never loads) the card
 * shows the polished fallback instead of broken grey tiles, and auto-upgrades the moment Maps works.
 */
@Composable
private fun FlightGoogleMap(flight: Flight, modifier: Modifier = Modifier) {
    val accent = JetTheme.colors.accent
    val origin = airportLatLng(flight.origin.code, fallback = LatLng(36.0840, -115.1537))   // fallback LAS
    val dest = airportLatLng(flight.destination.code, fallback = LatLng(33.6407, -84.4277)) // fallback ATL
    val t = (flight.progressPercent / 100f).let { if (it <= 0f) 0.42f else it }.coerceIn(0.05f, 0.95f)
    val plane = LatLng(
        origin.latitude + (dest.latitude - origin.latitude) * t,
        origin.longitude + (dest.longitude - origin.longitude) * t,
    )
    val mid = LatLng((origin.latitude + dest.latitude) / 2.0, (origin.longitude + dest.longitude) / 2.0)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(mid, 3.2f) }
    var mapLoaded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = camera,
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
            onMapLoaded = { mapLoaded = true },
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            ),
        ) {
            Polyline(points = listOf(origin, dest), color = accent, width = 8f)
            Marker(state = rememberMarkerState(position = origin), title = flight.origin.code)
            Marker(state = rememberMarkerState(position = dest), title = flight.destination.code)
            Marker(state = rememberMarkerState(position = plane), title = flight.ident, snippet = flight.status)
        }
        // Until the real map confirms it loaded, cover it with the polished Compose route so the
        // card is never a broken grey tile (covers the current auth-failure state too).
        if (!mapLoaded) {
            FlightMapCanvas(flight = flight, modifier = Modifier.matchParentSize())
        }
    }
}

/** Approximate coordinates for common airport codes so the map markers/polyline match their
 *  labels; unknown codes fall back to the supplied default (LAS for origin, ATL for dest). */
private fun airportLatLng(code: String, fallback: LatLng): LatLng = when (code.uppercase()) {
    "LAS" -> LatLng(36.0840, -115.1537)
    "ATL" -> LatLng(33.6407, -84.4277)
    "LAX" -> LatLng(33.9416, -118.4085)
    "JFK" -> LatLng(40.6413, -73.7781)
    "ORD" -> LatLng(41.9742, -87.9073)
    "DFW" -> LatLng(32.8998, -97.0403)
    "SFO" -> LatLng(37.6213, -122.3790)
    "SEA" -> LatLng(47.4502, -122.3088)
    else -> fallback
}

@Preview(showBackground = true, name = "Home – Dark")
@Composable
private fun HomeContentPreview() {
    JetSetterTheme(darkTheme = true) {
        HomeContent(
            state = HomeUiState(
                upcomingTrip = Trip(
                    name = "Atlanta Board Meeting",
                    destination = "Atlanta, GA",
                    startDate = "2026-07-14",
                    endDate = "2026-07-16",
                ),
                expenseSummary = ExpenseSummary(
                    total = 1812.75,
                    currency = "USD",
                    topCategory = ExpenseCategory.BUSINESS,
                    topCategoryAmount = 1290.00,
                ),
                alerts = listOf(
                    HomeAlert(
                        id = "gate-change",
                        title = "Gate change",
                        message = "DL1423 now departs from gate D14 (was C22).",
                        severity = AlertSeverity.WARNING,
                    ),
                    HomeAlert(
                        id = "weather",
                        title = "Weather watch",
                        message = "Afternoon thunderstorms forecast in Atlanta. IRIS is watching for delays.",
                        severity = AlertSeverity.INFO,
                    ),
                ),
            ),
        )
    }
}
