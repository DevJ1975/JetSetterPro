package com.jetsetter.pro.feature.identityvault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetColors
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme
import java.time.LocalDate

/**
 * Stateful entry point: owns the [IdentityvaultViewModel], collects its [IdentityvaultUiState]
 * lifecycle-aware, and forwards events. Holds no logic — see [IdentityVaultContent].
 */
@Composable
fun IdentityVaultScreen(viewModel: IdentityvaultViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    IdentityVaultContent(
        state = state,
        onUnlock = viewModel::unlock,
        onLock = viewModel::lock,
        onToggleReveal = viewModel::toggleReveal,
        onToggleRevealAll = viewModel::toggleRevealAll,
        onShowAddSheet = viewModel::showAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onAddItem = viewModel::addItem,
        onDeleteItem = viewModel::deleteItem,
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun IdentityVaultContent(
    state: IdentityvaultUiState,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onToggleReveal: (String) -> Unit,
    onToggleRevealAll: () -> Unit,
    onShowAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onAddItem: (IdentityVaultItemType, String, String, String, String, String) -> Unit,
    onDeleteItem: (String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                // One-time fade + slight rise as the vault content first appears.
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { entered = true }
                val entranceProgress by animateFloatAsState(
                    targetValue = if (entered) 1f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "vaultEntrance",
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .graphicsLayer {
                            alpha = entranceProgress
                            translationY = (1f - entranceProgress) * 24.dp.toPx()
                        },
                    contentPadding = PaddingValues(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = spacing.large,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    item {
                        Text(
                            "Identity Vault",
                            style = JetTheme.typography.displayTitle,
                            color = colors.textPrimary,
                        )
                    }

                    state.errorMessage?.let { message ->
                        item { ErrorCard(message) }
                    }

                    item {
                        LockBanner(
                            state = state,
                            onUnlock = onUnlock,
                            onLock = onLock,
                            onToggleRevealAll = onToggleRevealAll,
                        )
                    }

                    if (state.itemCount > 0) {
                        item { SummaryRow(state) }
                        item {
                            Text(
                                "SECURED CREDENTIALS",
                                style = JetTheme.typography.label,
                                color = colors.textSecondary,
                            )
                        }
                        items(state.entries, key = { it.item.id }) { entry ->
                            VaultItemCard(
                                entry = entry,
                                isUnlocked = state.isUnlocked,
                                isRevealed = entry.item.id in state.revealedIds,
                                onToggleReveal = { onToggleReveal(entry.item.id) },
                                onDelete = { onDeleteItem(entry.item.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else {
                        item { EmptyState(isUnlocked = state.isUnlocked) }
                    }

                    item {
                        Text(
                            "Stored encrypted on-device. Credentials stay masked until you " +
                                "authenticate, and the vault re-locks every time you leave.",
                            style = JetTheme.typography.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (state.isUnlocked) {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShowAddSheet()
                },
                containerColor = colors.accent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.medium),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add credential")
            }
        }
    }

    if (state.showAddSheet) {
        AddCredentialSheet(onDismiss = onDismissAddSheet, onConfirm = onAddItem)
    }
}

@Composable
private fun LockBanner(
    state: IdentityvaultUiState,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onToggleRevealAll: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val isUnlocked = state.isUnlocked
    val statusColor = if (isUnlocked) colors.success else colors.warning
    val haptics = LocalHapticFeedback.current

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isUnlocked) "Vault unlocked" else "Vault locked",
                    style = typography.cardTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = when {
                        state.isAuthenticating -> "Verifying Face ID…"
                        isUnlocked -> "Tap a card to reveal it."
                        state.itemCount == 0 -> "Authenticate to manage your vault."
                        else -> "${state.itemCount} secured items · authenticate to view."
                    },
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            AccentTag(
                text = if (isUnlocked) "SECURE" else "LOCKED",
                icon = if (isUnlocked) Icons.Filled.Verified else Icons.Filled.Lock,
            )
        }

        Spacer(Modifier.height(spacing.medium))

        if (isUnlocked) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                if (state.itemCount > 0) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleRevealAll()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceElevated,
                            contentColor = colors.textPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = if (state.allRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(spacing.small))
                        Text(if (state.allRevealed) "Hide all" else "Reveal all", style = typography.bodyMedium)
                    }
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLock()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.textPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.small))
                    Text("Lock vault", style = typography.bodyMedium)
                }
            }
        } else {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                },
                enabled = !state.isAuthenticating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) {
                if (state.isAuthenticating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(spacing.small))
                    Text("Authenticating…", style = typography.bodyMedium)
                } else {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.small))
                    Text("Unlock with Face ID", style = typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(state: IdentityvaultUiState) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = state.validCount.toString(),
            label = "Valid",
            color = colors.success,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = state.expiringCount.toString(),
            label = "Expiring",
            color = colors.warning,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = state.expiredCount.toString(),
            label = "Expired",
            color = colors.danger,
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: String, label: String, color: Color) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    JetCard(modifier = modifier.semantics(mergeDescendants = true) {}, contentPadding = 12.dp) {
        Text(value, style = typography.pageTitle, color = color)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun VaultItemCard(
    entry: IdentityVaultEntry,
    isUnlocked: Boolean,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val item = entry.item
    val showInClear = isUnlocked && isRevealed
    val haptics = LocalHapticFeedback.current

    // Whole card is a tap target for reveal/hide once unlocked (matches "Tap a card to reveal it").
    JetCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isUnlocked) {
                    Modifier
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleReveal()
                        }
                        .semantics {
                            role = Role.Button
                            stateDescription = if (isRevealed) "Revealed" else "Hidden"
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(item.type),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = typography.cardTitle, color = colors.textPrimary)
                Text(
                    "${item.holderName} · ${item.issuer}",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
            StatusChip(status = entry.status)
        }

        Spacer(Modifier.height(spacing.medium))

        Text(
            text = if (showInClear) item.value else item.maskedValue,
            style = typography.cardTitle.copy(fontFamily = FontFamily.Monospace),
            color = if (showInClear) colors.textPrimary else colors.textSecondary,
        )

        if (entry.passportCaution) {
            Spacer(Modifier.height(spacing.small))
            CautionNote("Under 6 months validity — renew before international travel.")
        }

        Spacer(Modifier.height(spacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                text = expiryText(entry),
                style = typography.caption,
                color = expiryColor(colors, entry.status),
                modifier = Modifier.weight(1f),
            )
            if (isUnlocked) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete ${item.label}",
                        tint = colors.danger,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            RevealToggle(
                isUnlocked = isUnlocked,
                isRevealed = isRevealed,
                onToggleReveal = onToggleReveal,
            )
        }
    }
}

@Composable
private fun StatusChip(status: IdentityVaultStatus?) {
    val colors = JetTheme.colors
    val color = statusColor(colors, status)
    val icon = when (status) {
        IdentityVaultStatus.VALID -> Icons.Filled.Verified
        IdentityVaultStatus.EXPIRING_SOON -> Icons.Filled.Schedule
        IdentityVaultStatus.EXPIRED -> Icons.Filled.Error
        null -> Icons.Filled.Schedule
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(status?.label ?: "No expiry", style = JetTheme.typography.label, color = color)
    }
}

@Composable
private fun CautionNote(text: String) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(14.dp),
        )
        Text(text, style = JetTheme.typography.caption, color = colors.warning)
    }
}

@Composable
private fun RevealToggle(
    isUnlocked: Boolean,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val tint = if (isUnlocked) colors.accent else colors.textSecondary
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (isUnlocked) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleReveal()
                        }
                        .semantics {
                            role = Role.Button
                            stateDescription = if (isRevealed) "Revealed" else "Hidden"
                        }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = when {
                !isUnlocked -> Icons.Filled.Lock
                isRevealed -> Icons.Filled.VisibilityOff
                else -> Icons.Filled.Visibility
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = when {
                !isUnlocked -> "Locked"
                isRevealed -> "Hide"
                else -> "Reveal"
            },
            style = typography.label,
            color = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddCredentialSheet(
    onDismiss: () -> Unit,
    onConfirm: (IdentityVaultItemType, String, String, String, String, String) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var typeName by rememberSaveable { mutableStateOf(IdentityVaultItemType.PASSPORT.name) }
    val selectedType = IdentityVaultItemType.valueOf(typeName)
    var label by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var holder by rememberSaveable { mutableStateOf("") }
    var issuer by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }

    val expiryValid = runCatching { LocalDate.parse(expiry.trim()) }.isSuccess
    val expiryError = expiry.isNotBlank() && !expiryValid
    val canSave = label.isNotBlank() && value.isNotBlank() && expiryValid

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
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text("New credential", style = typography.pageTitle, color = colors.textPrimary)
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Type")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                IdentityVaultItemType.entries.forEach { type ->
                    TypeChip(
                        type = type,
                        selected = type == selectedType,
                        onClick = { typeName = type.name },
                    )
                }
            }

            FieldLabel("Label")
            PremiumTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = selectedType.label,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Number")
            PremiumTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = "Document or member number",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Holder")
                    PremiumTextField(
                        value = holder,
                        onValueChange = { holder = it },
                        placeholder = "Jamil Jones",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("Issuer")
                    PremiumTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        placeholder = "United States",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            FieldLabel("Expiry date")
            PremiumTextField(
                value = expiry,
                onValueChange = { expiry = it },
                placeholder = "2031-04-18",
                modifier = Modifier.fillMaxWidth(),
            )
            if (expiryError) {
                Text(
                    "Use the format YYYY-MM-DD.",
                    style = typography.caption,
                    color = colors.danger,
                )
            }

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(selectedType, label, value, holder, issuer, expiry.trim())
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.small))
                Text("Add to vault", style = typography.cardTitle)
            }
        }
    }
}

@Composable
private fun TypeChip(type: IdentityVaultItemType, selected: Boolean, onClick: () -> Unit) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val contentColor = if (selected) Color.White else colors.textSecondary
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .border(
                0.5.dp,
                if (selected) Color.Transparent else colors.accent.copy(alpha = 0.20f),
                CircleShape,
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(iconFor(type), contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        Text(type.label, style = typography.label, color = contentColor)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun EmptyState(isUnlocked: Boolean) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Filled.Badge else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("No credentials yet", style = typography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isUnlocked) {
                        "Tap + to add your first secured credential."
                    } else {
                        "Unlock with Face ID to add and manage credentials."
                    },
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val colors = JetTheme.colors
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Text("Couldn't load the vault", style = JetTheme.typography.cardTitle, color = colors.danger)
        Spacer(Modifier.height(4.dp))
        Text(message, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

private fun iconFor(type: IdentityVaultItemType): ImageVector = when (type) {
    IdentityVaultItemType.PASSPORT -> Icons.Filled.Public
    IdentityVaultItemType.KNOWN_TRAVELER -> Icons.Filled.ConfirmationNumber
    IdentityVaultItemType.GLOBAL_ENTRY -> Icons.Filled.Flight
    IdentityVaultItemType.DRIVERS_LICENSE -> Icons.Filled.Badge
}

private fun statusColor(colors: JetColors, status: IdentityVaultStatus?): Color = when (status) {
    IdentityVaultStatus.VALID -> colors.success
    IdentityVaultStatus.EXPIRING_SOON -> colors.warning
    IdentityVaultStatus.EXPIRED -> colors.danger
    null -> colors.textSecondary
}

private fun expiryColor(colors: JetColors, status: IdentityVaultStatus?): Color = when (status) {
    IdentityVaultStatus.EXPIRING_SOON -> colors.warning
    IdentityVaultStatus.EXPIRED -> colors.danger
    else -> colors.textSecondary
}

/** Human-readable expiry line derived from the real day count, e.g. "Expires in 58 days · 2026-08-23". */
private fun expiryText(entry: IdentityVaultEntry): String {
    val date = entry.item.expiryDate ?: return "No expiry date on file"
    val days = entry.daysUntilExpiry ?: return "Expires $date"
    return when {
        days < 0 -> "Expired ${-days} days ago · $date"
        days == 0L -> "Expires today · $date"
        days == 1L -> "Expires tomorrow · $date"
        else -> "Expires in $days days · $date"
    }
}

@Preview(showBackground = true, name = "Identity Vault – unlocked")
@Composable
private fun IdentityVaultContentPreview() {
    JetSetterTheme {
        val passport = IdentityVaultItem(
            id = "passport",
            type = IdentityVaultItemType.PASSPORT,
            label = "Passport Number",
            value = "X1234567",
            holderName = "Jamil Jones",
            issuer = "United States",
            expiry = "2026-11-20",
        )
        val ktn = IdentityVaultItem(
            id = "ktn",
            type = IdentityVaultItemType.KNOWN_TRAVELER,
            label = "Known Traveler Number",
            value = "98765432",
            holderName = "Jamil Jones",
            issuer = "TSA PreCheck",
            expiry = "2029-09-30",
        )
        val license = IdentityVaultItem(
            id = "dl",
            type = IdentityVaultItemType.DRIVERS_LICENSE,
            label = "Driver's License",
            value = "D0451 6678 0921",
            holderName = "Jamil Jones",
            issuer = "Nevada DMV",
            expiry = "2026-06-05",
        )
        IdentityVaultContent(
            state = IdentityvaultUiState(
                entries = listOf(
                    IdentityVaultEntry(passport, IdentityVaultStatus.VALID, 148, passportCaution = true),
                    IdentityVaultEntry(ktn, IdentityVaultStatus.VALID, 1192, passportCaution = false),
                    IdentityVaultEntry(license, IdentityVaultStatus.EXPIRED, -21, passportCaution = false),
                ),
                isUnlocked = true,
                revealedIds = setOf("passport"),
            ),
            onUnlock = {},
            onLock = {},
            onToggleReveal = {},
            onToggleRevealAll = {},
            onShowAddSheet = {},
            onDismissAddSheet = {},
            onAddItem = { _, _, _, _, _, _ -> },
            onDeleteItem = {},
        )
    }
}
