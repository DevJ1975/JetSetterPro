package com.jetsetter.pro.feature.more

import com.jetsetter.pro.core.backend.CloudSession

/**
 * The three email/password flows the Account section's dialog can run. [title] heads the dialog,
 * [confirmLabel] the submit button, [description] the one-line explainer under the title.
 */
enum class AccountAuthMode(val title: String, val confirmLabel: String, val description: String) {
    SIGN_IN(
        title = "Sign in",
        confirmLabel = "Sign in",
        description = "Sign in to an existing account to pick up your synced trips and expenses.",
    ),
    CREATE(
        title = "Create account",
        confirmLabel = "Create",
        description = "Create an email account so your data syncs across devices.",
    ),
    LINK(
        title = "Link email",
        confirmLabel = "Link",
        description = "Add an email to your guest session — your synced data stays attached.",
    ),
}

/**
 * Account slice of [MoreUiState]: the live cloud session, whether the backend is configured at
 * all, an in-flight flag, and a one-shot notice line (auth/deletion outcome) the user dismisses.
 */
data class AccountUiState(
    val isConfigured: Boolean = false,
    val session: CloudSession? = null,
    val busy: Boolean = false,
    val notice: String? = null,
)

/**
 * Pure presentation/validation rules for the Account section — extracted from the ViewModel so
 * they are unit-testable without Android (see AccountLogicTest).
 */
object AccountLogic {

    const val DELETE_SUCCESS_MESSAGE =
        "Account deleted. Your cloud data and this device's synced copies have been removed."

    /**
     * Shown when [com.jetsetter.pro.core.backend.CloudBackend.deleteAccount] fails — which today
     * includes the `delete-account` edge function simply not being deployed yet. The message must
     * make clear that NOTHING was deleted (the client stops before any local wipe).
     */
    const val DELETE_FAILURE_MESSAGE =
        "Couldn't reach the account-deletion service — nothing was deleted. Please try again later."

    /** One-line session summary for the Account card. */
    fun statusLine(isConfigured: Boolean, session: CloudSession?): String = when {
        !isConfigured -> "Cloud sync isn't configured in this build — data stays on this device."
        session == null -> "Not signed in — data stays on this device until you sign in."
        session.isAnonymous -> "Guest session — synced anonymously. Link an email to keep your account."
        else -> "Signed in as ${session.email}."
    }

    /**
     * Client-side credential check before any network call. Returns a user-facing error line, or
     * null when the credentials are submittable. Password floor matches Supabase's default
     * minimum (6 characters).
     */
    fun validateCredentials(email: String, password: String): String? {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        val validEmail = at > 0 &&                       // something before the @
            trimmed.indexOf('@', at + 1) == -1 &&        // exactly one @
            trimmed.substring(at + 1).let { domain ->    // domain with a dot, not at the edges
                domain.contains('.') && !domain.startsWith(".") && !domain.endsWith(".") &&
                    domain.length >= 3
            } &&
            !trimmed.any { it.isWhitespace() }
        return when {
            !validEmail -> "Enter a valid email address."
            password.length < 6 -> "Password must be at least 6 characters."
            else -> null
        }
    }

    /** Notice line after a successful auth call, per mode. */
    fun authSuccessMessage(mode: AccountAuthMode): String = when (mode) {
        AccountAuthMode.SIGN_IN -> "Signed in."
        AccountAuthMode.CREATE ->
            "Account created. If email confirmation is enabled, check your inbox to finish."
        AccountAuthMode.LINK ->
            "Email linked — your synced data stays attached. Confirm via email if prompted."
    }

    /** Notice line after a failed auth call: the mode's verb + the backend's reason when usable. */
    fun authFailureMessage(mode: AccountAuthMode, error: Throwable?): String {
        val verb = when (mode) {
            AccountAuthMode.SIGN_IN -> "sign in"
            AccountAuthMode.CREATE -> "create the account"
            AccountAuthMode.LINK -> "link the email"
        }
        val reason = error?.message?.takeIf { it.isNotBlank() } ?: "check your connection and try again"
        return "Couldn't $verb: $reason"
    }

    /** Notice line for the account-deletion outcome. */
    fun deleteResultMessage(result: Result<Unit>): String =
        if (result.isSuccess) DELETE_SUCCESS_MESSAGE else DELETE_FAILURE_MESSAGE
}
