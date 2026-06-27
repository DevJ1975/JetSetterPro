package com.jetsetter.pro.feature.localexperience

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tour
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
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
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stateful entry point: owns the [LocalexperienceViewModel], collects its
 * [LocalexperienceUiState] lifecycle-aware, and forwards events. Holds no logic —
 * see [LocalExperienceContent].
 */
@Composable
fun LocalExperienceScreen(viewModel: LocalexperienceViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LocalExperienceContent(
        state = state,
        onSelectCategory = viewModel::selectCategory,
        onSelectSort = viewModel::setSort,
        onToggleSavedOnly = viewModel::toggleSavedOnly,
        onToggleSaved = viewModel::toggleSaved,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun LocalExperienceContent(
    state: LocalexperienceUiState,
    onSelectCategory: (LocalExperiencesCategory?) -> Unit,
    onSelectSort: (LocalExperienceSort) -> Unit,
    onToggleSavedOnly: () -> Unit,
    onToggleSaved: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing

    // One-time entrance: fade + slight upward slide on first composition (~250ms). Purely visual,
    // applied via graphicsLayer so it never interferes with the list scroll below.
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
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // Compute the rendered list once, then derive every aggregate from it so the summary
            // strip and the rows can never disagree.
            val visible = state.visibleExperiences

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = entranceAlpha
                        translationY = entranceOffset
                    },
                contentPadding = PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = spacing.large,
                    bottom = spacing.xlarge,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item {
                    Text("Local Experiences", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
                }
                item {
                    Text(
                        "Hand-picked activities near your next stop.",
                        style = JetTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }

                item {
                    CategoryFilterRow(
                        selected = state.selectedCategory,
                        categoryCounts = state.categoryCounts,
                        totalCount = state.experiences.size,
                        savedCount = state.savedCount,
                        showSavedOnly = state.showSavedOnly,
                        onSelectCategory = onSelectCategory,
                        onToggleSavedOnly = onToggleSavedOnly,
                    )
                }

                item {
                    SortRow(selected = state.sort, onSelectSort = onSelectSort)
                }

                if (visible.isEmpty()) {
                    item { EmptyState(showSavedOnly = state.showSavedOnly) }
                } else {
                    item { SummaryStrip(items = visible) }
                    items(visible, key = { it.id }) { experience ->
                        ExperienceCard(
                            item = experience,
                            isSaved = state.isSaved(experience.id),
                            onToggleSaved = { onToggleSaved(experience.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: LocalExperiencesCategory?,
    categoryCounts: Map<LocalExperiencesCategory, Int>,
    totalCount: Int,
    savedCount: Int,
    showSavedOnly: Boolean,
    onSelectCategory: (LocalExperiencesCategory?) -> Unit,
    onToggleSavedOnly: () -> Unit,
) {
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        FilterPill(
            label = "Saved",
            isSelected = showSavedOnly,
            onClick = onToggleSavedOnly,
            leadingIcon = if (showSavedOnly) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            count = savedCount,
        )
        FilterPill(
            label = "All",
            isSelected = selected == null && !showSavedOnly,
            onClick = { onSelectCategory(null) },
            count = totalCount,
        )
        LocalExperiencesCategory.entries.forEach { category ->
            FilterPill(
                label = category.label,
                isSelected = selected == category,
                onClick = { onSelectCategory(category) },
                leadingIcon = category.icon,
                count = categoryCounts[category] ?: 0,
            )
        }
    }
}

@Composable
private fun SortRow(
    selected: LocalExperienceSort,
    onSelectSort: (LocalExperienceSort) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text("SORT", style = JetTheme.typography.caption, color = colors.textSecondary)
        LocalExperienceSort.entries.forEach { sort ->
            FilterPill(
                label = sort.label,
                isSelected = selected == sort,
                onClick = { onSelectSort(sort) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    count: Int? = null,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val background = if (isSelected) colors.accent else colors.surface
    val contentColor = if (isSelected) Color.White else colors.textSecondary
    val borderColor = if (isSelected) colors.accent else colors.separator
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
                role = Role.RadioButton
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
        }
        Text(label, style = JetTheme.typography.label, color = contentColor)
        if (count != null) {
            Text(
                count.toString(),
                style = JetTheme.typography.label,
                color = if (isSelected) Color.White.copy(alpha = 0.75f) else colors.textSecondary.copy(alpha = 0.6f),
            )
        }
    }
}

/** Compact, real aggregate of the *visible* list — count, average rating and starting price. */
@Composable
private fun SummaryStrip(items: List<LocalExperiencesItem>) {
    val averageRating = items.map { it.rating }.average()
    val fromPrice = items.minOf { it.price }

    JetCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryStat(
                value = items.size.toString(),
                label = if (items.size == 1) "experience" else "experiences",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            SummaryStat(
                value = formatRating(averageRating),
                label = "avg rating",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            SummaryStat(
                value = money(fromPrice),
                label = "from",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(Locale.US), style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(JetTheme.colors.separator),
    )
}

@Composable
private fun ExperienceCard(
    item: LocalExperiencesItem,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = modifier.fillMaxWidth()) {
        // Colored "photo" placeholder with the category icon, a floating tag, and a save toggle.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(item.colorHex)),
        ) {
            Icon(
                imageVector = item.category.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(44.dp).align(Alignment.Center),
            )
            AccentTag(
                text = item.category.label,
                icon = item.category.icon,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
            SaveButton(
                isSaved = isSaved,
                onClick = onToggleSaved,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.title,
                style = typography.cardTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RatingBadge(rating = item.rating)
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
            Text(item.neighborhood, style = typography.caption, color = colors.textSecondary)
            Text("·", style = typography.caption, color = colors.textSecondary)
            Text(reviews(item.reviewCount), style = typography.caption, color = colors.textSecondary)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(item.durationLabel, style = typography.bodyMedium, color = colors.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("FROM", style = typography.caption, color = colors.textSecondary)
                Text(money(item.price), style = typography.cardTitle, color = colors.textPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveButton(isSaved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (isSaved) "Remove from saved" else "Save experience",
            tint = if (isSaved) colors.accent else Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RatingBadge(rating: Double) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.warning.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(13.dp),
        )
        Text(
            formatRating(rating),
            style = JetTheme.typography.label,
            color = colors.warning,
        )
    }
}

@Composable
private fun EmptyState(showSavedOnly: Boolean) {
    val colors = JetTheme.colors
    val title = if (showSavedOnly) "No saved experiences yet" else "Nothing in this category"
    val body = if (showSavedOnly) {
        "Tap the bookmark on any experience to save it for later."
    } else {
        "Try a different filter to see more local experiences."
    }
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (showSavedOnly) Icons.Outlined.BookmarkBorder else Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Text(title, style = JetTheme.typography.cardTitle, color = colors.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(body, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

private val LocalExperiencesCategory.icon: ImageVector
    get() = when (this) {
        LocalExperiencesCategory.FOOD_AND_DRINK -> Icons.Filled.Restaurant
        LocalExperiencesCategory.OUTDOOR -> Icons.Filled.Terrain
        LocalExperiencesCategory.CULTURE -> Icons.Filled.AccountBalance
        LocalExperiencesCategory.NIGHTLIFE -> Icons.Filled.Nightlife
        LocalExperiencesCategory.WELLNESS -> Icons.Filled.Spa
        LocalExperiencesCategory.TOURS -> Icons.Filled.Tour
    }

/** "$38" for whole dollars, "$38.50" otherwise — no misleading ".00" on round prices. */
private fun money(amount: Double): String {
    val rounded = amount.roundToInt()
    return if (amount == rounded.toDouble()) {
        "$" + String.format(Locale.US, "%,d", rounded)
    } else {
        "$" + String.format(Locale.US, "%,.2f", amount)
    }
}

/** One decimal place, always — "4.9", "5.0". */
private fun formatRating(value: Double): String = String.format(Locale.US, "%.1f", value)

/** Grouped review count, e.g. "1,204 reviews"; singular handled. */
private fun reviews(count: Int): String {
    val formatted = String.format(Locale.US, "%,d", count)
    return if (count == 1) "$formatted review" else "$formatted reviews"
}

@Preview(showBackground = true, name = "Local Experiences – populated")
@Composable
private fun LocalExperienceContentPreview() {
    JetSetterTheme {
        LocalExperienceContent(
            state = LocalexperienceUiState(
                experiences = listOf(
                    LocalExperiencesItem(
                        id = "p1",
                        title = "Sunrise Rooftop Coffee Tasting",
                        category = LocalExperiencesCategory.FOOD_AND_DRINK,
                        neighborhood = "Old Fourth Ward",
                        durationLabel = "1.5 hrs",
                        price = 38.0,
                        rating = 4.9,
                        reviewCount = 214,
                        colorHex = 0xFFB5651D,
                    ),
                    LocalExperiencesItem(
                        id = "p2",
                        title = "Coastal Cliffside Hike & Picnic",
                        category = LocalExperiencesCategory.OUTDOOR,
                        neighborhood = "Marin Headlands",
                        durationLabel = "4 hrs",
                        price = 65.0,
                        rating = 4.8,
                        reviewCount = 318,
                        colorHex = 0xFF2E8B57,
                    ),
                ),
                savedIds = setOf("p1"),
            ),
            onSelectCategory = {},
            onSelectSort = {},
            onToggleSavedOnly = {},
            onToggleSaved = {},
        )
    }
}

@Preview(showBackground = true, name = "Local Experiences – saved empty")
@Composable
private fun LocalExperienceEmptyPreview() {
    JetSetterTheme {
        LocalExperienceContent(
            state = LocalexperienceUiState(
                experiences = listOf(
                    LocalExperiencesItem(
                        id = "p1",
                        title = "Sunrise Rooftop Coffee Tasting",
                        category = LocalExperiencesCategory.FOOD_AND_DRINK,
                        neighborhood = "Old Fourth Ward",
                        durationLabel = "1.5 hrs",
                        price = 38.0,
                        rating = 4.9,
                        reviewCount = 214,
                        colorHex = 0xFFB5651D,
                    ),
                ),
                showSavedOnly = true,
            ),
            onSelectCategory = {},
            onSelectSort = {},
            onToggleSavedOnly = {},
            onToggleSaved = {},
        )
    }
}
