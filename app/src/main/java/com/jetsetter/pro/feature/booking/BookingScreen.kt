package com.jetsetter.pro.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.util.Locale

/**
 * Stateful entry point: owns the [BookingViewModel], collects its [BookingUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [BookingContent].
 */
@Composable
fun BookingScreen(viewModel: BookingViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    BookingContent(
        state = state,
        onSelectHotel = viewModel::selectHotel,
        onShowDetail = viewModel::showDetail,
        onDismissDetail = viewModel::dismissDetail,
        onToggleFavorite = viewModel::toggleFavorite,
        onSetSortMode = viewModel::setSortMode,
        onBudgetChange = viewModel::setBudget,
        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
        onClearFilters = viewModel::clearFilters,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingContent(
    state: BookingUiState,
    onSelectHotel: (HotelBookingItem) -> Unit,
    onShowDetail: (HotelBookingItem) -> Unit,
    onDismissDetail: () -> Unit,
    onToggleFavorite: (HotelBookingItem) -> Unit,
    onSetSortMode: (SortMode) -> Unit,
    onBudgetChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val nights = state.summary.nights
    val visible = state.visibleHotels

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // One-time entrance: fade + slight rise as the results first appear. Purely a
            // graphics-layer transform, so it never interferes with scrolling.
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            val enterAlpha by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(durationMillis = 250),
                label = "bookingEnter",
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = enterAlpha
                        translationY = (1f - enterAlpha) * 24f
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
                    Text("Hotel Booking", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
                }

                item { SearchSummaryCard(summary = state.summary, resultCount = state.hotels.size) }

                state.selectedHotel?.let { selected ->
                    item { SelectedStayCard(hotel = selected, nights = nights) }
                }

                item {
                    RefineCard(
                        state = state,
                        onSetSortMode = onSetSortMode,
                        onBudgetChange = onBudgetChange,
                        onToggleFavoritesOnly = onToggleFavoritesOnly,
                        onClearFilters = onClearFilters,
                    )
                }

                item {
                    Text(
                        text = "${visible.size} of ${state.hotels.size} stays · ${state.sortMode.label}",
                        style = JetTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }

                if (visible.isEmpty()) {
                    item {
                        EmptyResults(
                            hasActiveFilters = state.hasActiveFilters,
                            onClearFilters = onClearFilters,
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { hotel ->
                        HotelCard(
                            hotel = hotel,
                            nights = nights,
                            isSelected = hotel.id == state.selectedHotelId,
                            onOpenDetail = { onShowDetail(hotel) },
                            onSelect = { onSelectHotel(hotel) },
                            onToggleFavorite = { onToggleFavorite(hotel) },
                            // Animate add/remove/reorder as filters & sort change the list.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    state.detailHotel?.let { hotel ->
        HotelDetailSheet(
            hotel = hotel,
            nights = nights,
            isSelected = hotel.id == state.selectedHotelId,
            onDismiss = onDismissDetail,
            onToggleSelect = {
                onSelectHotel(hotel)
                onDismissDetail()
            },
            onToggleFavorite = { onToggleFavorite(hotel) },
        )
    }
}

@Composable
private fun SearchSummaryCard(summary: HotelSearchSummary, resultCount: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("DESTINATION", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(spacing.xsmall))
        Text(summary.destination, style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryChip(
                icon = { Icon(Icons.Filled.CalendarMonth, null, tint = colors.accent, modifier = Modifier.size(14.dp)) },
                label = "${summary.checkIn} – ${summary.checkOut}",
            )
            SummaryChip(
                icon = { Icon(Icons.Filled.Person, null, tint = colors.accent, modifier = Modifier.size(14.dp)) },
                label = "${summary.guests} guests",
            )
            SummaryChip(
                icon = null,
                label = "${summary.nights} nights",
            )
        }
    }
}

@Composable
private fun SummaryChip(icon: (@Composable () -> Unit)?, label: String) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.invoke()
        Text(label, style = JetTheme.typography.label, color = colors.textPrimary)
    }
}

/** Prominent banner for the currently selected stay, with its real total (incl. taxes). */
@Composable
private fun SelectedStayCard(hotel: HotelBookingItem, nights: Int) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val quote = hotel.quote(nights)
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Icon(Icons.Filled.CheckCircle, null, tint = colors.success, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SELECTED STAY", style = typography.caption, color = colors.success)
                Text(hotel.name, style = typography.cardTitle, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("$nights nights · taxes incl.", style = typography.caption, color = colors.textSecondary)
            Text(money(quote.total), style = typography.cardTitle, color = colors.accent)
        }
    }
}

/** Sort + budget + favorites controls grouped in one card. All inputs update state live. */
@Composable
private fun RefineCard(
    state: BookingUiState,
    onSetSortMode: (SortMode) -> Unit,
    onBudgetChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("REFINE", style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(spacing.small))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            SortMode.entries.forEach { mode ->
                ToggleChip(
                    label = mode.label,
                    selected = state.sortMode == mode,
                    onClick = { onSetSortMode(mode) },
                )
            }
        }

        Spacer(Modifier.height(spacing.medium))
        FieldLabel("Max nightly budget")
        Spacer(Modifier.height(spacing.xsmall))
        PremiumTextField(
            value = state.maxNightlyBudget,
            onValueChange = onBudgetChange,
            placeholder = "e.g. 300",
            modifier = Modifier.fillMaxWidth(),
        )
        state.budgetError?.let { error ->
            Spacer(Modifier.height(spacing.xsmall))
            Text(error, style = typography.caption, color = colors.danger)
        }

        Spacer(Modifier.height(spacing.medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleChip(
                label = "Favorites (${state.favoriteCount})",
                selected = state.showFavoritesOnly,
                icon = if (state.showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                onClick = onToggleFavoritesOnly,
                role = Role.Checkbox,
            )
            if (state.hasActiveFilters) {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearFilters()
                    },
                ) {
                    Text("Clear filters", style = typography.label, color = colors.accent)
                }
            }
        }
    }
}

/** Pill toggle used for both sort modes and the favorites filter. */
@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    role: Role = Role.RadioButton,
) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    val contentColor = if (selected) Color.White else colors.textSecondary
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                this.role = role
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(label, style = JetTheme.typography.label, color = contentColor)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun HotelCard(
    hotel: HotelBookingItem,
    nights: Int,
    isSelected: Boolean,
    onOpenDetail: () -> Unit,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val quote = hotel.quote(nights)
    val haptics = LocalHapticFeedback.current

    // Selection ring: a slightly larger accent border behind the card so it reads as "chosen".
    val outerShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .clip(outerShape)
                        .border(1.5.dp, colors.accent, outerShape)
                        .padding(2.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        JetCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDetail)
                .semantics { role = Role.Button },
        ) {
            PhotoPlaceholder(
                colorHex = hotel.photoColorHex,
                isFavorite = hotel.isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
            Spacer(Modifier.height(spacing.small))

            Text(hotel.name, style = typography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                StarRow(starRating = hotel.starRating)
                Text(
                    String.format(Locale.US, "%.1f", hotel.guestRating),
                    style = typography.label,
                    color = colors.success,
                )
                Text("Guest rating", style = typography.caption, color = colors.textSecondary)
            }

            Spacer(Modifier.height(spacing.xsmall))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.LocationOn, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(hotel.neighborhood, style = typography.caption, color = colors.textSecondary)
            }

            if (hotel.amenities.isNotEmpty() || hotel.freeCancellation) {
                Spacer(Modifier.height(spacing.small))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
                    if (hotel.freeCancellation) {
                        AccentTag(text = "Free cancellation", icon = Icons.Outlined.Verified)
                    }
                    hotel.amenities.take(2).forEach { amenity ->
                        AccentTag(text = amenity)
                    }
                }
            }

            Spacer(Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(hotel.pricePerNight), style = typography.cardTitle, color = colors.textPrimary)
                        Text(" / night", style = typography.caption, color = colors.textSecondary)
                    }
                    // Real total: nightly × nights, shown right on the card.
                    Text(
                        "$nights nights · ${money(quote.subtotal)}",
                        style = typography.caption,
                        color = colors.textSecondary,
                    )
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) colors.success else colors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Selected", style = typography.label)
                    } else {
                        Text("Select", style = typography.label)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HotelDetailSheet(
    hotel: HotelBookingItem,
    nights: Int,
    isSelected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quote = hotel.quote(nights)
    val haptics = LocalHapticFeedback.current

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            PhotoPlaceholder(
                colorHex = hotel.photoColorHex,
                isFavorite = hotel.isFavorite,
                onToggleFavorite = onToggleFavorite,
            )

            Text(hotel.name, style = typography.pageTitle, color = colors.textPrimary)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                StarRow(starRating = hotel.starRating)
                Text(
                    String.format(Locale.US, "%.1f", hotel.guestRating),
                    style = typography.label,
                    color = colors.success,
                )
                Text("Guest rating", style = typography.caption, color = colors.textSecondary)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.LocationOn, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(hotel.neighborhood, style = typography.caption, color = colors.textSecondary)
            }

            if (hotel.amenities.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xsmall))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    hotel.amenities.forEach { amenity -> AccentTag(text = amenity) }
                }
            }

            if (hotel.freeCancellation) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Verified, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text("Free cancellation up to 24h before check-in", style = typography.caption, color = colors.textSecondary)
                }
            }

            Spacer(Modifier.height(spacing.xsmall))
            HorizontalDivider(color = colors.separator)
            Spacer(Modifier.height(spacing.xsmall))

            Text("PRICE BREAKDOWN", style = typography.caption, color = colors.textSecondary)
            PriceRow("${money(quote.nightly)} × $nights nights", money(quote.subtotal))
            PriceRow("Taxes & fees (${(TAX_AND_FEE_RATE * 100).toInt()}%)", money(quote.taxesAndFees))
            HorizontalDivider(color = colors.separator)
            PriceRow("Total", money(quote.total), emphasize = true)

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) colors.success else colors.accent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Selected — tap to remove", style = typography.cardTitle)
                } else {
                    Text("Select this stay", style = typography.cardTitle)
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, emphasize: Boolean = false) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (emphasize) typography.cardTitle else typography.bodyMedium,
            color = if (emphasize) colors.textPrimary else colors.textSecondary,
        )
        Text(
            value,
            style = typography.cardTitle.copy(fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold),
            color = if (emphasize) colors.accent else colors.textPrimary,
        )
    }
}

@Composable
private fun PhotoPlaceholder(colorHex: Long, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    val base = Color(colorHex)
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(base, base.copy(alpha = 0.6f)))),
    ) {
        Icon(
            Icons.Filled.Apartment,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center).size(40.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .minimumInteractiveComponentSize()
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFavorite()
                }
                .semantics(mergeDescendants = true) { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from saved" else "Save hotel",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StarRow(starRating: Int) {
    val colors = JetTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$starRating-star hotel"
        },
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < starRating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (index < starRating) colors.accent else colors.separator,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun EmptyResults(hasActiveFilters: Boolean, onClearFilters: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Icon(Icons.Filled.SearchOff, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            Text(
                if (hasActiveFilters) "No stays match your filters" else "No stays available",
                style = typography.cardTitle,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(spacing.xsmall))
        Text(
            if (hasActiveFilters) {
                "Try raising your nightly budget or turning off the favorites filter."
            } else {
                "Pull to refresh once results are back online."
            },
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (hasActiveFilters) {
            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClearFilters()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
            ) {
                Text("Clear filters", style = typography.label)
            }
        }
    }
}

private fun money(amount: Double): String = "$" + String.format(Locale.US, "%,.2f", amount)

@Preview(showBackground = true, name = "Hotel Booking – results")
@Composable
private fun BookingContentPreview() {
    JetSetterTheme {
        BookingContent(
            state = BookingUiState(
                summary = HotelSearchSummary(
                    destination = "Lisbon, Portugal",
                    checkIn = "Jul 14",
                    checkOut = "Jul 17",
                    nights = 3,
                    guests = 2,
                ),
                sortMode = SortMode.PRICE_LOW,
                selectedHotelId = "memmo-alfama",
                hotels = listOf(
                    HotelBookingItem(
                        id = "tivoli-avenida",
                        name = "Tivoli Avenida Liberdade",
                        starRating = 5,
                        guestRating = 9.2,
                        neighborhood = "Avenida da Liberdade",
                        pricePerNight = 412.0,
                        photoColorHex = 0xFF4C6FBF,
                        amenities = listOf("Pool", "Spa"),
                        freeCancellation = true,
                        isFavorite = true,
                    ),
                    HotelBookingItem(
                        id = "memmo-alfama",
                        name = "Memmo Alfama",
                        starRating = 4,
                        guestRating = 9.0,
                        neighborhood = "Alfama",
                        pricePerNight = 268.0,
                        photoColorHex = 0xFFB5793A,
                        amenities = listOf("Rooftop", "Breakfast"),
                    ),
                ),
            ),
            onSelectHotel = {},
            onShowDetail = {},
            onDismissDetail = {},
            onToggleFavorite = {},
            onSetSortMode = {},
            onBudgetChange = {},
            onToggleFavoritesOnly = {},
            onClearFilters = {},
        )
    }
}

@Preview(showBackground = true, name = "Hotel Booking – empty (filtered)")
@Composable
private fun BookingEmptyPreview() {
    JetSetterTheme {
        BookingContent(
            state = BookingUiState(
                summary = HotelSearchSummary(
                    destination = "Lisbon, Portugal",
                    checkIn = "Jul 14",
                    checkOut = "Jul 17",
                    nights = 3,
                    guests = 2,
                ),
                maxNightlyBudget = "100",
                hotels = listOf(
                    HotelBookingItem(
                        id = "santa-justa",
                        name = "Hotel Santa Justa",
                        starRating = 3,
                        guestRating = 8.4,
                        neighborhood = "Baixa",
                        pricePerNight = 159.0,
                        photoColorHex = 0xFF8E6FB0,
                    ),
                ),
            ),
            onSelectHotel = {},
            onShowDetail = {},
            onDismissDetail = {},
            onToggleFavorite = {},
            onSetSortMode = {},
            onBudgetChange = {},
            onToggleFavoritesOnly = {},
            onClearFilters = {},
        )
    }
}
