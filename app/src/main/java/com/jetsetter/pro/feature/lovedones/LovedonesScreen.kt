package com.jetsetter.pro.feature.lovedones

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jetsetter.pro.core.data.lovedones.LovedOne
import com.jetsetter.pro.core.util.SmsComposer
import com.jetsetter.pro.core.util.SmsTemplates
import com.jetsetter.pro.ui.components.AccentTag
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.components.PremiumTextField
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Stateful entry point: owns the [LovedonesViewModel], collects its [LovedonesUiState]
 * lifecycle-aware, and forwards events. The test-SMS action needs a Context, so the launch
 * lambda is built here — the content below stays a pure, previewable function.
 */
@Composable
fun LovedonesScreen(viewModel: LovedonesViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LovedonesContent(
        state = state,
        onShowAddSheet = viewModel::showAddSheet,
        onShowEditSheet = viewModel::showEditSheet,
        onDismissSheet = viewModel::dismissSheet,
        onSaveContact = viewModel::saveContact,
        onDeleteContact = viewModel::deleteContact,
        onSetNotifyOnTakeoff = viewModel::setNotifyOnTakeoff,
        onSetNotifyOnLanding = viewModel::setNotifyOnLanding,
        onSendTestSms = { contact ->
            // Opens the native composer prefilled with the takeoff template — nothing is sent
            // until the user taps send in their own messaging app (spec §3.3).
            SmsComposer.composeSms(
                context = context,
                phoneNumbers = listOf(contact.phoneNumber),
                body = SmsTemplates.takeoff("DL123"),
            )
        },
    )
}

/**
 * Stateless content: a pure function of [state] + event lambdas, so it's trivially previewable
 * and testable.
 */
@Composable
private fun LovedonesContent(
    state: LovedonesUiState,
    onShowAddSheet: () -> Unit,
    onShowEditSheet: (LovedOne) -> Unit,
    onDismissSheet: () -> Unit,
    onSaveContact: (String, String, Boolean, Boolean) -> Unit,
    onDeleteContact: (String) -> Unit,
    onSetNotifyOnTakeoff: (String, Boolean) -> Unit,
    onSetNotifyOnLanding: (String, Boolean) -> Unit,
    onSendTestSms: (LovedOne) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    // One-time entrance: fade + gentle slide-in on first composition. Purely visual; runs once
    // and leaves scrolling untouched (a graphics layer over the list, not a wrapping container).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "contentAlpha",
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (appeared) 0f else 24f,
        animationSpec = tween(durationMillis = 250),
        label = "contentOffsetY",
    )

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffsetY
                    },
                contentPadding = PaddingValues(
                    start = spacing.medium,
                    end = spacing.medium,
                    top = spacing.large,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                item { Header(contactCount = state.contacts.size) }
                if (state.isEmpty) {
                    item { EmptyContactsCard() }
                } else {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            onEdit = { onShowEditSheet(contact) },
                            onDelete = { onDeleteContact(contact.id) },
                            onSetNotifyOnTakeoff = { onSetNotifyOnTakeoff(contact.id, it) },
                            onSetNotifyOnLanding = { onSetNotifyOnLanding(contact.id, it) },
                            onSendTestSms = { onSendTestSms(contact) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onShowAddSheet()
            },
            containerColor = colors.accent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.medium),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add loved one")
        }
    }

    if (state.showEditSheet) {
        EditContactSheet(
            editing = state.editing,
            isSaving = state.isSaving,
            onDismiss = onDismissSheet,
            onConfirm = onSaveContact,
        )
    }
}

@Composable
private fun Header(contactCount: Int) {
    val colors = JetTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(JetTheme.spacing.xsmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Loved Ones", style = JetTheme.typography.displayTitle, color = colors.textPrimary)
            AccentTag(
                text = if (contactCount == 1) "1 contact" else "$contactCount contacts",
                icon = Icons.Filled.FavoriteBorder,
            )
        }
        Text(
            "People to text when your flight takes off or lands. Messages open in your SMS app — nothing is ever sent silently.",
            style = JetTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ContactCard(
    contact: LovedOne,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetNotifyOnTakeoff: (Boolean) -> Unit,
    onSetNotifyOnLanding: (Boolean) -> Unit,
    onSendTestSms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val haptics = LocalHapticFeedback.current

    JetCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        style = typography.cardTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(contact.phoneNumber, style = typography.caption, color = colors.textSecondary)
                }
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onEdit()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit ${contact.name}",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${contact.name}",
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(spacing.small))
        NotifyToggleRow(
            title = "Text on takeoff",
            checked = contact.notifyOnTakeoff,
            onCheckedChange = onSetNotifyOnTakeoff,
        )
        NotifyToggleRow(
            title = "Text on landing",
            checked = contact.notifyOnLanding,
            onCheckedChange = onSetNotifyOnLanding,
        )

        Spacer(Modifier.height(spacing.xsmall))
        TestSmsRow(onClick = onSendTestSms)
    }
}

/** A labelled on/off notification row with a Material switch (the app's settings idiom). */
@Composable
private fun NotifyToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JetTheme.spacing.small),
    ) {
        Text(
            title,
            style = JetTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
        )
    }
}

/** Opens the native SMS composer with the takeoff template so the user can preview the flow. */
@Composable
private fun TestSmsRow(onClick: () -> Unit) {
    val colors = JetTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Sms,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Text("Send a test text", style = JetTheme.typography.label, color = colors.accent)
    }
}

@Composable
private fun EmptyContactsCard() {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    JetCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(spacing.small))
            Text(
                "No loved ones yet",
                style = typography.cardTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xsmall))
            Text(
                "Tap + to add someone. IRIS can prefill a text to them the moment you take off or land.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Add/edit modal bottom sheet. Inputs are local, transient draft state (kept across config
 * changes via [rememberSaveable], re-seeded when the edited contact changes); the parent only
 * tracks whether the sheet is shown and which contact it edits. On confirm the raw strings are
 * handed up to the ViewModel, which trims and persists them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditContactSheet(
    editing: LovedOne?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Boolean) -> Unit,
) {
    val colors = JetTheme.colors
    val typography = JetTheme.typography
    val spacing = JetTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var phone by rememberSaveable(editing?.id) { mutableStateOf(editing?.phoneNumber.orEmpty()) }
    var notifyOnTakeoff by rememberSaveable(editing?.id) { mutableStateOf(editing?.notifyOnTakeoff ?: true) }
    var notifyOnLanding by rememberSaveable(editing?.id) { mutableStateOf(editing?.notifyOnLanding ?: true) }

    val canSave = name.isNotBlank() && phone.isNotBlank() && !isSaving

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
            Text(
                if (editing == null) "Add loved one" else "Edit loved one",
                style = typography.pageTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(spacing.xsmall))

            FieldLabel("Name")
            PremiumTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Mom",
                modifier = Modifier.fillMaxWidth(),
            )
            if (name.isBlank()) RequiredHint()

            FieldLabel("Phone number")
            PremiumTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = "+1 702 555 0134",
                modifier = Modifier.fillMaxWidth(),
            )
            if (phone.isBlank()) RequiredHint()

            Spacer(Modifier.height(spacing.xsmall))
            NotifyToggleRow(
                title = "Text on takeoff",
                checked = notifyOnTakeoff,
                onCheckedChange = { notifyOnTakeoff = it },
            )
            NotifyToggleRow(
                title = "Text on landing",
                checked = notifyOnLanding,
                onCheckedChange = { notifyOnLanding = it },
            )

            Spacer(Modifier.height(spacing.small))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(name, phone, notifyOnTakeoff, notifyOnLanding)
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isSaving -> "Saving…"
                        editing == null -> "Add contact"
                        else -> "Save changes"
                    },
                    style = typography.cardTitle,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = JetTheme.typography.label, color = JetTheme.colors.textSecondary)
}

@Composable
private fun RequiredHint() {
    Text("Required", style = JetTheme.typography.caption, color = JetTheme.colors.danger)
}

@Preview(showBackground = true, name = "Loved Ones – populated")
@Composable
private fun LovedonesContentPreview() {
    JetSetterTheme {
        LovedonesContent(
            state = LovedonesUiState(
                contacts = listOf(
                    LovedOne(id = "l1", name = "Mom", phoneNumber = "+1 702 555 0134"),
                    LovedOne(
                        id = "l2",
                        name = "Alex",
                        phoneNumber = "+1 404 555 0188",
                        notifyOnLanding = false,
                    ),
                ),
            ),
            onShowAddSheet = {},
            onShowEditSheet = {},
            onDismissSheet = {},
            onSaveContact = { _, _, _, _ -> },
            onDeleteContact = {},
            onSetNotifyOnTakeoff = { _, _ -> },
            onSetNotifyOnLanding = { _, _ -> },
            onSendTestSms = {},
        )
    }
}

@Preview(showBackground = true, name = "Loved Ones – empty")
@Composable
private fun LovedonesContentEmptyPreview() {
    JetSetterTheme {
        LovedonesContent(
            state = LovedonesUiState(),
            onShowAddSheet = {},
            onShowEditSheet = {},
            onDismissSheet = {},
            onSaveContact = { _, _, _, _ -> },
            onDeleteContact = {},
            onSetNotifyOnTakeoff = { _, _ -> },
            onSetNotifyOnLanding = { _, _ -> },
            onSendTestSms = {},
        )
    }
}
