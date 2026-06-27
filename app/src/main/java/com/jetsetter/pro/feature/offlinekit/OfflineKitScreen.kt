package com.jetsetter.pro.feature.offlinekit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sd
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import java.util.Locale

/**
 * Stateful entry point: owns the [OfflinekitViewModel], collects its [OfflinekitUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [OfflineKitContent].
 */
@Composable
fun OfflineKitScreen(viewModel: OfflinekitViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    OfflineKitContent(
        state = state,
        onToggleDownload = viewModel::toggleDownload,
        onDownloadAll = viewModel::downloadAll,
        onRemoveAll = viewModel::removeAll,
        onQueryChange = viewModel::onQueryChange,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun OfflineKitContent(
    state: OfflinekitUiState,
    onToggleDownload: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onRemoveAll: () -> Unit,
    onQueryChange: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        // One-time fade + slight rise as the content first appears (after the loading gate).
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val entranceAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "entranceAlpha",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entranceAlpha
                    translationY = (1f - entranceAlpha) * 16.dp.toPx()
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.medium)
                .padding(top = spacing.large, bottom = spacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text("Offline Kit", style = JetTheme.typography.displayTitle, color = colors.textPrimary)

            StorageSummaryCard(state = state)

            if (state.bundles.isNotEmpty()) {
                DownloadAllButton(state = state, onDownloadAll = onDownloadAll)
                if (state.downloadedCount > 0) {
                    RemoveAllButton(usedMb = state.storageUsedMb, onRemoveAll = onRemoveAll)
                }

                PremiumTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Search trips or destinations",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TRIP BUNDLES", style = JetTheme.typography.caption, color = colors.textSecondary)
                if (state.bundles.isNotEmpty()) {
                    Text(
                        "${state.downloadedCount}/${state.bundles.size} offline",
                        style = JetTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }

            when {
                state.bundles.isEmpty() -> EmptyState()
                state.visibleBundles.isEmpty() -> NoResultsState(query = state.query)
                else -> state.visibleBundles.forEach { bundle ->
                    BundleCard(bundle = bundle, onToggleDownload = { onToggleDownload(bundle.id) })
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(state: OfflinekitUiState) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val barColor = when {
        state.storageFraction >= 0.9f -> colors.danger
        state.storageFraction >= 0.7f -> colors.warning
        else -> colors.accent
    }
    // Ease the bar fills toward their computed targets instead of snapping. Targets are the
    // exact derived fractions — only the visual transition is animated, the math is unchanged.
    val animatedFraction by animateFloatAsState(
        targetValue = state.storageFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "storageFraction",
    )
    val animatedProjectedFraction by animateFloatAsState(
        targetValue = state.projectedFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "projectedFraction",
    )
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("OFFLINE STORAGE", style = typography.caption, color = colors.textSecondary)
                Text(formatSize(state.storageUsedMb), style = typography.metric, color = colors.textPrimary)
                Text(
                    "of ${formatSize(state.storageQuotaMb)} reserved",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = barColor,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Track with two fills: a faint "projected" segment (everything downloaded) under the
        // solid "actual" usage, so the headroom a Download-all would consume is visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.separator),
        ) {
            if (state.pendingCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProjectedFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor.copy(alpha = 0.28f)),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${state.downloadedCount} of ${state.bundles.size} trips available offline",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Text(
                "${formatSize(state.storageFreeMb)} free",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
        if (state.pendingCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                "+${formatSize(state.pendingDownloadMb)} to cache everything",
                style = typography.caption,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun DownloadAllButton(state: OfflinekitUiState, onDownloadAll: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val label = when {
        state.allDownloaded -> "All trips downloaded"
        state.downloadedCount == 0 -> "Download all · +${formatSize(state.pendingDownloadMb)}"
        else -> "Download remaining ${state.pendingCount} · +${formatSize(state.pendingDownloadMb)}"
    }
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onDownloadAll()
        },
        enabled = !state.allDownloaded,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = Color.White,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textSecondary,
        ),
    ) {
        Icon(
            imageVector = if (state.allDownloaded) Icons.Filled.DownloadDone else Icons.Filled.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = JetTheme.typography.label)
    }
}

@Composable
private fun RemoveAllButton(usedMb: Double, onRemoveAll: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onRemoveAll()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Remove all downloads · free ${formatSize(usedMb)}", style = JetTheme.typography.label)
    }
}

@Composable
private fun BundleCard(bundle: OfflineKitBundle, onToggleDownload: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.tripName, style = typography.cardTitle, color = colors.textPrimary)
                Text(bundle.destination, style = typography.caption, color = colors.textSecondary)
            }
            if (bundle.isDownloaded) {
                AccentTag(text = "Offline ready", icon = Icons.Filled.CheckCircle)
            } else {
                StatusChip(text = "Not downloaded")
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            MetaItem(icon = Icons.Outlined.Sd, text = formatSize(bundle.sizeMb))
            MetaItem(icon = Icons.Outlined.Layers, text = "${bundle.contents.size} items")
            MetaItem(
                icon = Icons.Outlined.Schedule,
                text = if (bundle.isDownloaded) "Synced ${bundle.lastSynced}" else "Not on device",
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("INCLUDES", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            bundle.contents.joinToString(" · "),
            style = typography.bodyMedium,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(14.dp))
        DownloadToggle(
            isDownloaded = bundle.isDownloaded,
            sizeMb = bundle.sizeMb,
            onClick = onToggleDownload,
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    val colors = JetTheme.colors
    Text(
        text = text,
        style = JetTheme.typography.label,
        color = colors.textSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    val colors = JetTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(text, style = JetTheme.typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun DownloadToggle(isDownloaded: Boolean, sizeMb: Double, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (isDownloaded) colors.success.copy(alpha = 0.15f) else colors.accent
    val foreground = if (isDownloaded) colors.success else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(background)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics {
                role = Role.Button
                stateDescription = if (isDownloaded) "Downloaded" else "Not downloaded"
            }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (isDownloaded) "Downloaded · tap to remove" else "Download · ${formatSize(sizeMb)}",
            style = JetTheme.typography.label,
            color = foreground,
        )
    }
}

@Composable
private fun NoResultsState(query: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text("No matches", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "No trips match \"$query\". Try another name or destination.",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text("No bundles yet", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Offline bundles appear here once you have trips to cache.",
                    style = JetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

private fun formatSize(mb: Double): String = if (mb >= 1024.0) {
    String.format(Locale.US, "%,.1f GB", mb / 1024.0)
} else {
    String.format(Locale.US, "%,.0f MB", mb)
}

@Preview(showBackground = true, name = "Offline Kit – populated")
@Composable
private fun OfflineKitContentPreview() {
    JetSetterTheme {
        OfflineKitContent(
            state = OfflinekitUiState(
                bundles = listOf(
                    OfflineKitBundle(
                        id = "atl-board",
                        tripName = "Atlanta Board Meeting",
                        destination = "Atlanta, GA",
                        sizeMb = 142.6,
                        lastSynced = "Today, 8:04 AM",
                        contents = listOf("Itinerary", "Boarding passes", "Offline map"),
                        isDownloaded = true,
                    ),
                    OfflineKitBundle(
                        id = "tyo-summit",
                        tripName = "Tokyo Product Summit",
                        destination = "Tokyo, JP",
                        sizeMb = 486.3,
                        lastSynced = "Yesterday, 6:12 PM",
                        contents = listOf("Itinerary", "Metro map", "Phrasebook"),
                        isDownloaded = false,
                    ),
                    OfflineKitBundle(
                        id = "lis-retreat",
                        tripName = "Lisbon Team Retreat",
                        destination = "Lisbon, PT",
                        sizeMb = 268.9,
                        lastSynced = "Jun 21, 9:30 AM",
                        contents = listOf("Itinerary", "Offline map", "Restaurant list"),
                        isDownloaded = false,
                    ),
                ),
            ),
            onToggleDownload = {},
            onDownloadAll = {},
            onRemoveAll = {},
            onQueryChange = {},
        )
    }
}
