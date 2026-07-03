package com.jetsetter.pro.feature.departureoptimizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.jetsetter.pro.core.secrets.Secrets
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * In-app route guidance from home to the departure airport — the whole navigation experience
 * stays inside JetSetter Pro (no handoff to an external maps app). Renders on a real Google Map
 * when `MAPS_API_KEY` is configured (with the Compose-drawn map covering until tiles confirm,
 * mirroring Home's FlightMapCard), and on the code-drawn map otherwise, so the demo never
 * depends on a key or the network.
 *
 * "Start drive" plays a simulated ~20s run down the seeded Summerlin → Harry Reid Intl route:
 * the position marker moves along the polyline while the remaining time/distance and ETA count
 * down in sync with the optimizer's live drive estimate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapSheet(
    est: DepartureOptimizerEstimate,
    onDismiss: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var driving by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (driving) 1f else 0f,
        animationSpec = if (driving) tween(SIM_DRIVE_MS, easing = LinearEasing) else snap(),
        label = "routeProgress",
    )
    val arrived = driving && progress >= 1f

    val remainingMinutes = ceil(est.driveMinutes * (1f - progress).toDouble()).toInt()
    val remainingMiles = ROUTE_MILES * (1f - progress)
    val etaMinuteOfDay = est.nowMinuteOfDay + remainingMinutes

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
                .padding(horizontal = spacing.medium)
                .padding(bottom = spacing.large)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text("Route to airport", style = typography.pageTitle, color = colors.textPrimary)
            Text(
                "$ROUTE_START_LABEL → ${est.airport}",
                style = typography.caption,
                color = colors.textSecondary,
            )

            RouteMap(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                RouteStat(modifier = Modifier.weight(1f), label = "REMAINING", value = "$remainingMinutes min")
                RouteStat(
                    modifier = Modifier.weight(1f),
                    label = "DISTANCE",
                    value = String.format(Locale.US, "%.1f mi", remainingMiles),
                )
                RouteStat(modifier = Modifier.weight(1f), label = "ETA", value = routeClock(etaMinuteOfDay))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ConditionChip(
                    label = "Traffic ${est.trafficRisk.label.lowercase(Locale.US)}",
                    color = routeRiskColor(est.trafficRisk),
                )
                ConditionChip(label = est.weatherSummary, color = routeRiskColor(est.weatherRisk))
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    driving = !driving
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (driving) colors.surfaceElevated else colors.success,
                    contentColor = if (driving) colors.textPrimary else Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val (icon, label) = when {
                    arrived -> Icons.Filled.CheckCircle to "Arrived — end navigation"
                    driving -> Icons.Filled.Stop to "End navigation"
                    else -> Icons.Filled.Navigation to "Start drive"
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.small))
                Text(label, style = typography.label)
            }

            Text(
                "Guidance runs entirely inside JetSetter Pro. Live turn-by-turn from the device GPS " +
                    "activates with location access in production.",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** One stat cell of the REMAINING / DISTANCE / ETA row. */
@Composable
private fun RouteStat(modifier: Modifier, label: String, value: String) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = typography.caption, color = colors.textSecondary)
        Text(value, style = typography.cardTitle, color = colors.textPrimary)
    }
}

/** Small tinted condition capsule (traffic / weather). */
@Composable
private fun ConditionChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun routeRiskColor(risk: DepartureOptimizerRisk): Color {
    val colors = JetTheme.colors
    return when (risk) {
        DepartureOptimizerRisk.LOW -> colors.success
        DepartureOptimizerRisk.MODERATE -> colors.warning
        DepartureOptimizerRisk.HIGH -> colors.danger
    }
}

/** Real Google Map when keyed (Compose-drawn map covers until tiles load); code-drawn otherwise. */
@Composable
private fun RouteMap(progress: Float, modifier: Modifier = Modifier) {
    if (Secrets.isConfigured(Secrets.mapsApiKey)) {
        RouteGoogleMap(progress = progress, modifier = modifier)
    } else {
        RouteCanvas(progress = progress, modifier = modifier)
    }
}

@Composable
private fun RouteGoogleMap(progress: Float, modifier: Modifier = Modifier) {
    val accent = JetTheme.colors.accent
    val points = remember { ROUTE_WAYPOINTS.map { LatLng(it.first, it.second) } }
    val center = remember {
        LatLng(
            ROUTE_WAYPOINTS.sumOf { it.first } / ROUTE_WAYPOINTS.size,
            ROUTE_WAYPOINTS.sumOf { it.second } / ROUTE_WAYPOINTS.size,
        )
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, 11.2f) }
    val car = routePointAt(progress)
    var mapLoaded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = camera,
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
            Polyline(points = points, color = accent, width = 10f)
            Marker(state = MarkerState(position = points.first()), title = ROUTE_START_LABEL)
            Marker(state = MarkerState(position = points.last()), title = "Harry Reid Intl (LAS)")
            Marker(
                state = MarkerState(position = LatLng(car.first, car.second)),
                title = "You",
            )
        }
        // Never show broken grey tiles: keep the code-drawn map on top until Maps confirms load.
        if (!mapLoaded) {
            RouteCanvas(progress = progress, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * Code-drawn city route map, matching the app's design language (same idiom as Home's
 * FlightMapCanvas): street grid, the route polyline projected from the real waypoints, endpoint
 * pins, and the traveler's position dot moving with [progress].
 */
@Composable
private fun RouteCanvas(progress: Float, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val accent = colors.accent
    val land = colors.surfaceElevated
    val grid = colors.separator.copy(alpha = 0.5f)
    val success = colors.success

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = land, cornerRadius = CornerRadius(16.dp.toPx()))
        var x = w / 7f
        while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += w / 7f }
        var y = h / 5f
        while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f); y += h / 5f }

        // Project the real waypoints into the canvas with a padded bounding box.
        val minLat = ROUTE_WAYPOINTS.minOf { it.first }
        val maxLat = ROUTE_WAYPOINTS.maxOf { it.first }
        val minLng = ROUTE_WAYPOINTS.minOf { it.second }
        val maxLng = ROUTE_WAYPOINTS.maxOf { it.second }
        val pad = 0.12f
        fun project(lat: Double, lng: Double): Offset {
            val fx = ((lng - minLng) / (maxLng - minLng)).toFloat()
            val fy = ((maxLat - lat) / (maxLat - minLat)).toFloat()
            return Offset(
                (pad + fx * (1f - 2f * pad)) * w,
                (pad + fy * (1f - 2f * pad)) * h,
            )
        }

        val screenPoints = ROUTE_WAYPOINTS.map { project(it.first, it.second) }
        val route = Path().apply {
            moveTo(screenPoints.first().x, screenPoints.first().y)
            screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(route, color = accent, style = Stroke(width = 5.dp.toPx()))

        // Endpoints: home pin + airport pin.
        drawCircle(accent, radius = 9.dp.toPx(), center = screenPoints.first())
        drawCircle(Color.White, radius = 4.dp.toPx(), center = screenPoints.first())
        drawCircle(success, radius = 9.dp.toPx(), center = screenPoints.last())
        drawCircle(Color.White, radius = 4.dp.toPx(), center = screenPoints.last())

        // The traveler, moving along the polyline by distance fraction.
        val car = routePointAt(progress)
        val carOffset = project(car.first, car.second)
        drawCircle(Color.White, radius = 8.dp.toPx(), center = carOffset)
        drawCircle(accent, radius = 5.5.dp.toPx(), center = carOffset)
    }
}

/**
 * Interpolates a position [fraction] of the way along the route, by cumulative segment length
 * (Euclidean in degree space — fine at city scale), so the marker's speed matches the drawn path.
 */
internal fun routePointAt(fraction: Float): Pair<Double, Double> {
    val f = fraction.coerceIn(0f, 1f).toDouble()
    val pts = ROUTE_WAYPOINTS
    if (f <= 0.0) return pts.first()
    if (f >= 1.0) return pts.last()

    val lengths = pts.zipWithNext { a, b -> hypot(b.first - a.first, b.second - a.second) }
    val total = lengths.sum()
    if (total <= 0.0) return pts.first()

    var target = total * f
    for ((i, len) in lengths.withIndex()) {
        if (target <= len) {
            val t = if (len > 0.0) target / len else 0.0
            val (aLat, aLng) = pts[i]
            val (bLat, bLng) = pts[i + 1]
            return (aLat + (bLat - aLat) * t) to (aLng + (bLng - aLng) * t)
        }
        target -= len
    }
    return pts.last()
}

/** Formats minutes-since-midnight as a 12-hour clock, e.g. 322 -> "5:22 AM". */
private fun routeClock(minutesSinceMidnight: Int): String {
    val m = ((minutesSinceMidnight % 1440) + 1440) % 1440
    val hour24 = m / 60
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    return String.format(Locale.US, "%d:%02d %s", hour12, m % 60, if (hour24 < 12) "AM" else "PM")
}

/** Where the demo traveler starts the drive. */
internal const val ROUTE_START_LABEL = "Home · Summerlin"

/** Straight-line-ish route length used for the countdown display. */
internal const val ROUTE_MILES = 13.8f

/** Simulated drive duration for the demo run. */
private const val SIM_DRIVE_MS = 20_000

/**
 * The seeded drive: Summerlin → I-215/I-15 → Harry Reid Intl (LAS), as (lat, lng) waypoints.
 * Shared by the Google-Maps polyline and the code-drawn projection so both render the same route.
 */
internal val ROUTE_WAYPOINTS: List<Pair<Double, Double>> = listOf(
    36.1672 to -115.3150, // Home · Summerlin
    36.1580 to -115.2900,
    36.1420 to -115.2610, // I-215 East
    36.1250 to -115.2280,
    36.1130 to -115.1980,
    36.1050 to -115.1750, // I-15 South interchange
    36.0980 to -115.1560,
    36.0900 to -115.1500,
    36.0840 to -115.1537, // Harry Reid Intl (LAS)
)
