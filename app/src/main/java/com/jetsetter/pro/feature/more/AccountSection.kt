package com.jetsetter.pro.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jetsetter.pro.core.backend.CloudSession
import com.jetsetter.pro.ui.components.JetCard
import com.jetsetter.pro.ui.theme.JetSetterTheme
import com.jetsetter.pro.ui.theme.JetTheme

/**
 * Account section card (plan B5b): shows the live cloud-session state and offers the matching
 * actions — sign in / create account when signed out, link email / sign out for a guest
 * (anonymous) session, sign out for an email account, and the destructive delete-account flow.
 * All state flows in via [AccountUiState]; every mutation goes back through the callbacks
 * (ultimately [com.jetsetter.pro.core.backend.CloudBackend]). Dialog visibility is local UI
 * state — nothing here talks to the backend directly.
 */
@Composable
internal fun AccountCard(
    state: AccountUiState,
    onSubmitAuth: (AccountAuthMode, String, String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    var authMode by remember { mutableStateOf<AccountAuthMode?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val session = state.session

    JetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Icon(
                if (state.isConfigured) Icons.Filled.AccountCircle else Icons.Filled.CloudOff,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Cloud account", style = JetTheme.typography.cardTitle, color = colors.textPrimary)
                Text(
                    AccountLogic.statusLine(state.isConfigured, session),
                    style = JetTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            }
        }

        state.notice?.let { notice ->
            Spacer(Modifier.height(spacing.xsmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    notice,
                    style = JetTheme.typography.caption,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissNotice) {
                    Text("Dismiss", style = JetTheme.typography.caption, color = colors.accent)
                }
            }
        }

        // When the backend keys are absent every action would no-op or throw — status line only.
        if (state.isConfigured) {
            Spacer(Modifier.height(spacing.xsmall))
            when {
                session == null -> {
                    AccountActionRow("Sign in", enabled = !state.busy) { authMode = AccountAuthMode.SIGN_IN }
                    AccountRowDivider()
                    AccountActionRow("Create account", enabled = !state.busy) { authMode = AccountAuthMode.CREATE }
                }
                session.isAnonymous -> {
                    AccountActionRow("Link email", enabled = !state.busy) { authMode = AccountAuthMode.LINK }
                    AccountRowDivider()
                    AccountActionRow("Sign out", enabled = !state.busy, onClick = onSignOut)
                }
                else -> {
                    AccountActionRow("Sign out", enabled = !state.busy, onClick = onSignOut)
                }
            }
            if (session != null) {
                AccountRowDivider()
                AccountActionRow(
                    label = "Delete account…",
                    enabled = !state.busy,
                    labelColor = colors.danger,
                    showChevron = false,
                ) { confirmDelete = true }
            }
        }
    }

    authMode?.let { mode ->
        AccountAuthDialog(
            mode = mode,
            onDismiss = { authMode = null },
            onSubmit = { email, password ->
                authMode = null
                onSubmitAuth(mode, email, password)
            },
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteAccount()
            },
        )
    }
}

/** One tappable action line inside the card (visual sibling of the Features rows). */
@Composable
private fun AccountActionRow(
    label: String,
    enabled: Boolean,
    labelColor: Color = JetTheme.colors.textPrimary,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = JetTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled) { onClick() }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = JetTheme.typography.bodyMedium,
            color = if (enabled) labelColor else colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
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
private fun AccountRowDivider() {
    val colors = JetTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(colors.separator),
    )
}

/**
 * Email + password dialog shared by the three auth flows. Validation is client-side and pure
 * ([AccountLogic.validateCredentials]); the dialog only submits credentials that pass, so the
 * ViewModel never sees a locally-invalid pair. Backend failures surface later via the card's
 * notice line (the dialog is already dismissed — deliberate, matching the one-shot notice UX).
 */
@Composable
private fun AccountAuthDialog(
    mode: AccountAuthMode,
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
) {
    val colors = JetTheme.colors
    val spacing = JetTheme.spacing
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text(mode.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(mode.description, style = JetTheme.typography.caption, color = colors.textSecondary)
                CredentialField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    placeholder = "Email",
                    keyboardType = KeyboardType.Email,
                )
                CredentialField(
                    value = password,
                    onValueChange = { password = it; validationError = null },
                    placeholder = "Password",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                validationError?.let {
                    Text(it, style = JetTheme.typography.caption, color = colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = AccountLogic.validateCredentials(email, password)
                    if (error != null) validationError = error else onSubmit(email, password)
                },
            ) {
                Text(mode.confirmLabel, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
        },
    )
}

/** Destructive confirmation for account deletion — spells out exactly what is (not) removed. */
@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = JetTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = { Text("Delete account?") },
        text = {
            Text(
                "This permanently deletes your account and every synced trip, expense, and " +
                    "setting — in the cloud and on this device. It can't be undone. If the " +
                    "deletion service can't be reached, nothing is deleted.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete everything", color = colors.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) }
        },
    )
}

/**
 * Local variant of [com.jetsetter.pro.ui.components.PremiumTextField] with keyboard-type and
 * password-masking support (the shared component is deliberately plain-text only).
 */
@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = JetTheme.colors
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = JetTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (colors.isDark) Color(0xFF141726) else Color(0xFFF4F5FB))
            .border(0.5.dp, colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.06f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = JetTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            inner()
        },
    )
}

@Preview(showBackground = true, name = "Account – guest session")
@Composable
private fun AccountCardAnonymousPreview() {
    JetSetterTheme {
        AccountCard(
            state = AccountUiState(
                isConfigured = true,
                session = CloudSession(uid = "abc", email = null, isAnonymous = true),
            ),
            onSubmitAuth = { _, _, _ -> },
            onSignOut = {},
            onDeleteAccount = {},
            onDismissNotice = {},
        )
    }
}

@Preview(showBackground = true, name = "Account – signed in with email")
@Composable
private fun AccountCardEmailPreview() {
    JetSetterTheme {
        AccountCard(
            state = AccountUiState(
                isConfigured = true,
                session = CloudSession(uid = "abc", email = "jamil@example.com", isAnonymous = false),
                notice = AccountLogic.DELETE_FAILURE_MESSAGE,
            ),
            onSubmitAuth = { _, _, _ -> },
            onSignOut = {},
            onDeleteAccount = {},
            onDismissNotice = {},
        )
    }
}
