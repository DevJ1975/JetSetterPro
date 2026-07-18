package com.jetsetter.pro

import com.jetsetter.pro.core.backend.CloudSession
import com.jetsetter.pro.feature.more.AccountAuthMode
import com.jetsetter.pro.feature.more.AccountLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pure Account-section rules (plan B5b): the session status line, client-side
 * credential validation, and the auth/deletion notice messages — including the contract that a
 * failed deletion clearly says NOTHING was deleted (the edge function may simply not be
 * deployed yet).
 */
class AccountLogicTest {

    private val anonymous = CloudSession(uid = "u1", email = null, isAnonymous = true)
    private val email = CloudSession(uid = "u1", email = "jamil@example.com", isAnonymous = false)

    // ── statusLine ───────────────────────────────────────────────────────────

    @Test
    fun statusLine_unconfiguredWinsOverAnySession() {
        val line = AccountLogic.statusLine(isConfigured = false, session = email)
        assertTrue(line.contains("isn't configured"))
    }

    @Test
    fun statusLine_signedOut() {
        assertTrue(AccountLogic.statusLine(true, null).startsWith("Not signed in"))
    }

    @Test
    fun statusLine_anonymousMentionsGuestAndLinking() {
        val line = AccountLogic.statusLine(true, anonymous)
        assertTrue(line.contains("Guest session"))
        assertTrue(line.contains("Link an email"))
    }

    @Test
    fun statusLine_emailSessionShowsTheAddress() {
        assertEquals("Signed in as jamil@example.com.", AccountLogic.statusLine(true, email))
    }

    // ── validateCredentials ──────────────────────────────────────────────────

    @Test
    fun validate_acceptsReasonableCredentials() {
        assertNull(AccountLogic.validateCredentials("jamil@example.com", "hunter22"))
        assertNull(AccountLogic.validateCredentials("  padded@example.com  ", "123456"))   // trimmed
    }

    @Test
    fun validate_rejectsBadEmails() {
        val cases = listOf(
            "",                     // blank
            "no-at-sign.com",       // missing @
            "@example.com",         // nothing before @
            "two@@example.com",     // double @
            "user@nodot",           // domain without a dot
            "user@.com",            // dot at domain start
            "user@example.",        // dot at domain end
            "spaced user@example.com", // interior whitespace
        )
        for (candidate in cases) {
            assertEquals(
                "expected rejection for '$candidate'",
                "Enter a valid email address.",
                AccountLogic.validateCredentials(candidate, "hunter22"),
            )
        }
    }

    @Test
    fun validate_rejectsShortPasswords_supabaseMinimumIsSix() {
        assertEquals(
            "Password must be at least 6 characters.",
            AccountLogic.validateCredentials("jamil@example.com", "12345"),
        )
        assertNull(AccountLogic.validateCredentials("jamil@example.com", "123456"))
    }

    @Test
    fun validate_emailErrorTakesPrecedenceOverPasswordError() {
        assertEquals("Enter a valid email address.", AccountLogic.validateCredentials("nope", ""))
    }

    // ── auth notice messages ─────────────────────────────────────────────────

    @Test
    fun authSuccess_hasAModeSpecificMessage() {
        assertEquals("Signed in.", AccountLogic.authSuccessMessage(AccountAuthMode.SIGN_IN))
        assertTrue(AccountLogic.authSuccessMessage(AccountAuthMode.CREATE).contains("Account created"))
        assertTrue(AccountLogic.authSuccessMessage(AccountAuthMode.LINK).contains("stays attached"))
    }

    @Test
    fun authFailure_usesTheBackendReasonWhenPresent() {
        val message = AccountLogic.authFailureMessage(
            AccountAuthMode.SIGN_IN,
            IllegalStateException("Invalid login credentials"),
        )
        assertEquals("Couldn't sign in: Invalid login credentials", message)
    }

    @Test
    fun authFailure_fallsBackWhenTheReasonIsBlankOrMissing() {
        for (error in listOf(null, IllegalStateException(""), IllegalStateException(" "))) {
            val message = AccountLogic.authFailureMessage(AccountAuthMode.LINK, error)
            assertEquals("Couldn't link the email: check your connection and try again", message)
        }
    }

    // ── deletion notice messages ─────────────────────────────────────────────

    @Test
    fun deleteResult_success() {
        assertEquals(
            AccountLogic.DELETE_SUCCESS_MESSAGE,
            AccountLogic.deleteResultMessage(Result.success(Unit)),
        )
    }

    @Test
    fun deleteResult_failureSaysServiceUnreachableAndNothingDeleted() {
        // The not-deployed-yet edge function is the expected live failure: the message must make
        // the "couldn't reach the service" + "nothing was deleted" contract explicit.
        val message = AccountLogic.deleteResultMessage(
            Result.failure(IllegalStateException("404 from functions endpoint")),
        )
        assertEquals(AccountLogic.DELETE_FAILURE_MESSAGE, message)
        assertTrue(message.contains("Couldn't reach the account-deletion service"))
        assertTrue(message.contains("nothing was deleted"))
        assertNotNull(message)
    }
}
