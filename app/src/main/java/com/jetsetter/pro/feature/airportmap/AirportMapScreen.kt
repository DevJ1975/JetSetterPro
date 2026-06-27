package com.jetsetter.pro.feature.airportmap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
 * Stateful entry point: owns the [AirportmapViewModel], collects its [AirportmapUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [AirportMapContent].
 */
@Composable
fun AirportMapScreen(viewModel: AirportmapViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    AirportMapContent(
        state = state,
        onSelectOrigin = viewModel::selectOrigin,
        onUpdateQuery = viewModel::updateQuery,
        onSelectCategoryFilter = viewModel::selectCategoryFilter,
        onSelectSort = viewModel::selectSort,
        onToggleCategory = viewModel::toggleCategory,
        onExpandAll = viewModel::expandAll,
        onCollapseAll = viewModel::collapseAll,
        onClearFilters = viewModel::clearFilters,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun AirportMapContent(
    state: AirportmapUiState,
    onSelectOrigin: (Concourse) -> Unit,
    onUpdateQuery: (String) -> Unit,
    onSelectCategoryFilter: (AirportAmenityCategory?) -> Unit,
    onSelectSort: (AmenitySort) -> Unit,
    onToggleCategory: (AirportAmenityCategory) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography

    // One-time entrance: content eases in (fade + slight rise) on first composition.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "airportGuideEnter",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .graphicsLayer {
                alpha = enterProgress
                translationY = (1f - enterProgress) * 16.dp.toPx()
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
            .padding(top = spacing.large, bottom = spacing.xlarge),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text("Airport Guide", style = typography.displayTitle, color = colors.textPrimary)
        Text(
            "${state.airportCode} · ${state.airportName}",
            style = typography.caption,
            color = colors.textSecondary,
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier
                    .padding(top = spacing.large)
                    .align(Alignment.CenterHorizontally),
            )
        } else if (state.isEmpty) {
            EmptyCard()
        } else {
            OriginCard(origin = state.origin, onSelectOrigin = onSelectOrigin)
            SummaryCard(
                state = state,
                onExpandAll = onExpandAll,
                onCollapseAll = onCollapseAll,
                onClearFilters = onClearFilters,
            )
            SearchField(query = state.query, onUpdateQuery = onUpdateQuery)
            CategoryFilterRow(state = state, onSelect = onSelectCategoryFilter)
            SortRow(sort = state.sort, onSelectSort = onSelectSort)

            if (state.isFilteredEmpty) {
                FilteredEmptyCard(onClearFilters = onClearFilters)
            } else {
                state.sections.forEach { section ->
                    CategorySection(
                        section = section,
                        expanded = state.effectiveExpanded(section.category),
                        canToggle = state.canToggle(section.category),
                        walkOf = state::walkMinutes,
                        onToggle = { onToggleCategory(section.category) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginCard(origin: Concourse, onSelectOrigin: (Concourse) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Text("You're at", style = typography.label, color = colors.textSecondary)
            Text(origin.label, style = typography.label, color = colors.accent)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Concourse.values().forEach { concourse ->
                ConcourseChip(
                    code = concourse.code,
                    selected = concourse == origin,
                    onClick = { onSelectOrigin(concourse) },
                )
            }
        }
    }
}

@Composable
private fun ConcourseChip(code: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) Color.Transparent else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            style = typography.label.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else colors.textSecondary,
        )
    }
}

@Composable
private fun SummaryCard(
    state: AirportmapUiState,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (state.hasActiveFilter) "${state.visibleCount} of ${state.totalCount}" else "${state.totalCount}",
                    style = typography.metric,
                    color = colors.textPrimary,
                )
                Text(
                    if (state.hasActiveFilter) "amenities shown" else "amenities mapped",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            val nearestMinutes = state.nearestWalkMinutes
            if (nearestMinutes != null) {
                AccentTag(text = "Nearest $nearestMinutes min", icon = Icons.Filled.NearMe)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Walk times measured from ${state.origin.label}. " +
                "${state.withinShortWalk} within a ${AmenityWalkModel.SHORT_WALK_MINUTES}-min walk.",
            style = typography.caption,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(12.dp))
        if (state.hasActiveFilter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Filters active",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClearFilters()
                        }
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
                    Text("Clear filters", style = typography.label, color = colors.accent)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandAll()
                        }
                        .semantics(mergeDescendants = true) { role = Role.Button },
                ) {
                    AccentTag(text = "Expand all", icon = Icons.Filled.UnfoldMore)
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCollapseAll()
                        }
                        .semantics(mergeDescendants = true) { role = Role.Button },
                ) {
                    AccentTag(text = "Collapse all", icon = Icons.Filled.UnfoldLess)
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onUpdateQuery: (String) -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        PremiumTextField(
            value = query,
            onValueChange = onUpdateQuery,
            placeholder = "Search amenities, gates or concourses",
            modifier = Modifier.weight(1f),
        )
        if (query.isNotBlank()) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUpdateQuery("")
                    }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterRow(
    state: AirportmapUiState,
    onSelect: (AirportAmenityCategory?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryChip(
            label = "All",
            count = state.totalCount,
            color = JetTheme.colors.accent,
            selected = state.categoryFilter == null,
            onClick = { onSelect(null) },
        )
        AirportAmenityCategory.values().forEach { category ->
            CategoryChip(
                label = category.label,
                count = state.countFor(category),
                color = categoryColor(category),
                selected = state.categoryFilter == category,
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) color.copy(alpha = 0.55f) else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = typography.label, color = if (selected) color else colors.textPrimary)
        Text(count.toString(), style = typography.label, color = if (selected) color else colors.textSecondary)
    }
}

@Composable
private fun SortRow(sort: AmenitySort, onSelectSort: (AmenitySort) -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text("SORT", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        AmenitySort.values().forEach { option ->
            ChoiceChip(
                label = option.label,
                selected = option == sort,
                onClick = { onSelectSort(option) },
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    Text(
        text = label,
        style = typography.label,
        color = if (selected) Color.White else colors.textSecondary,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                width = 0.7.dp,
                color = if (selected) Color.Transparent else colors.separator,
                shape = CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun CategorySection(
    section: AirportGuideSection,
    expanded: Boolean,
    canToggle: Boolean,
    walkOf: (AirportGuideItem) -> Int,
    onToggle: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    val accent = categoryColor(section.category)
    // The closest amenity in this section, surfaced on the header so a collapsed section is useful.
    val nearestMinutes = section.items.minOfOrNull(walkOf)

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canToggle) {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggle()
                            }
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                stateDescription = if (expanded) "Expanded" else "Collapsed"
                            }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(section.category),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(section.category.label, style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${section.items.size} ${if (section.items.size == 1) "place" else "places"}" +
                        (nearestMinutes?.let { " · nearest $it min" } ?: ""),
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            if (canToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                section.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.separator),
                        )
                    }
                    AmenityRow(item = item, walkMinutes = walkOf(item))
                }
            }
        }
    }
}

@Composable
private fun AmenityRow(item: AirportGuideItem, walkMinutes: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(categoryColor(item.category)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = typography.bodyMedium, color = colors.textPrimary)
            Text(
                "${item.concourse.label} · ${item.locationDetail}",
                style = typography.caption,
                color = colors.textSecondary,
            )
            if (item.note.isNotBlank()) {
                Text(item.note, style = typography.caption, color = colors.textSecondary)
            }
        }
        WalkBadge(minutes = walkMinutes)
    }
}

/** Walk-time pill, tinted by proximity (green / amber / red) — like [AccentTag] but status-aware. */
@Composable
private fun WalkBadge(minutes: Int) {
    val typography = JetTheme.typography
    val color = walkColor(proximityFor(minutes))
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text("$minutes min", style = typography.label, color = color)
    }
}

@Composable
private fun FilteredEmptyCard(onClearFilters: () -> Unit) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyStateIcon(icon = Icons.Filled.SearchOff)
            Spacer(Modifier.height(spacing.small))
            Text("No matching amenities", style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Nothing matches your search or filter.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.medium))
            Text(
                "Clear filters",
                style = typography.label,
                color = colors.accent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearFilters()
                    }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val typography = JetTheme.typography
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyStateIcon(icon = Icons.Filled.Map)
            Spacer(Modifier.height(spacing.small))
            Text("Guide unavailable", style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "No amenities are mapped for this airport yet.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shared muted icon disc for the screen's empty states. */
@Composable
private fun EmptyStateIcon(icon: ImageVector) {
    val colors = JetTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.textSecondary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun categoryColor(category: AirportAmenityCategory): Color {
    val colors = JetTheme.colors
    return when (category) {
        AirportAmenityCategory.LOUNGES -> colors.accent
        AirportAmenityCategory.DINING -> colors.warning
        AirportAmenityCategory.RESTROOMS -> colors.blue
        AirportAmenityCategory.SHOPS -> colors.success
        AirportAmenityCategory.GATES -> colors.primary
    }
}

private fun categoryIcon(category: AirportAmenityCategory): ImageVector = when (category) {
    AirportAmenityCategory.LOUNGES -> Icons.Filled.Weekend
    AirportAmenityCategory.DINING -> Icons.Filled.Restaurant
    AirportAmenityCategory.RESTROOMS -> Icons.Filled.Wc
    AirportAmenityCategory.SHOPS -> Icons.Filled.ShoppingBag
    AirportAmenityCategory.GATES -> Icons.Filled.FlightTakeoff
}

@Composable
private fun walkColor(proximity: WalkProximity): Color {
    val colors = JetTheme.colors
    return when (proximity) {
        WalkProximity.CLOSE -> colors.success
        WalkProximity.MODERATE -> colors.warning
        WalkProximity.FAR -> colors.danger
    }
}

@Preview(showBackground = true, name = "Airport Guide")
@Composable
private fun AirportMapContentPreview() {
    JetSetterTheme {
        AirportMapContent(
            state = AirportmapUiState(
                airportName = "Hartsfield-Jackson Atlanta International",
                airportCode = "ATL",
                origin = Concourse.T,
                expandedCategories = setOf(AirportAmenityCategory.LOUNGES),
                allItems = listOf(
                    AirportGuideItem("l1", "Delta Sky Club", AirportAmenityCategory.LOUNGES, Concourse.A, "Above Gate A17", 4, "Open 4:30 AM - 8:00 PM"),
                    AirportGuideItem("l3", "Amex Centurion Lounge", AirportAmenityCategory.LOUNGES, Concourse.C, "Near Gate C10", 3, "Amex Platinum access"),
                    AirportGuideItem("d1", "Starbucks", AirportAmenityCategory.DINING, Concourse.T, "Near Gate T7", 2, "Coffee, grab & go"),
                    AirportGuideItem("r1", "Restroom", AirportAmenityCategory.RESTROOMS, Concourse.T, "Near security", 1, "Wheelchair accessible"),
                    AirportGuideItem("g1", "Gates T1 - T13", AirportAmenityCategory.GATES, Concourse.T, "Domestic Terminal", 1, "No Plane Train needed"),
                ),
            ),
            onSelectOrigin = {},
            onUpdateQuery = {},
            onSelectCategoryFilter = {},
            onSelectSort = {},
            onToggleCategory = {},
            onExpandAll = {},
            onCollapseAll = {},
            onClearFilters = {},
        )
    }
}
