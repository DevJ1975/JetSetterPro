package com.jetsetter.pro.core.ai

import java.util.UUID

/**
 * A data-changing IRIS action staged for user confirmation (spec §1.4, iOS `IRISPendingAction`).
 *
 * Staged tools never touch the repositories at dispatch time; they build a [commit] lambda that
 * performs the real write and park it here via [ActionRouter.stage]. The chat UI renders [summary]
 * on a confirmation card and either cancels ([ActionRouter.clear]) or runs the commit
 * ([ActionRouter.confirm]), whose returned string is appended to the transcript.
 */
data class IrisPendingAction(
    val id: UUID = UUID.randomUUID(),
    val kind: Kind,
    /** One-line human description of what will happen, e.g. `Log expense: USD 40.00 at Nobu`. */
    val summary: String,
    /** Performs the actual write(s) and returns the user-facing result line. */
    val commit: suspend () -> String,
) {
    /**
     * The staged-action kinds — mirrors the iOS enum verbatim, so the confirmation card can key
     * icons off it. [TRACK_FLIGHT] is present for cross-platform enum parity even though the
     * `trackFlight` tool executes immediately (navigation is not a data change).
     */
    enum class Kind {
        LOG_EXPENSE,
        CHECK_IN,
        ADD_TRIP,
        TRACK_FLIGHT,
        GENERATE_PACKING_LIST,
        SUBMIT_EXPENSES,
    }
}
