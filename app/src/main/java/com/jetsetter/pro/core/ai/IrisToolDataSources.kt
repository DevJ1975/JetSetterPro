package com.jetsetter.pro.core.ai

import javax.inject.Inject

/**
 * Narrow read/act seams the IRIS tools use to reach feature-module data (plan R5) — keeps the
 * dependency edge core→core: the interfaces live here, the implementations wrap the feature
 * repositories and are bound in `core/di/ToolDataModule`. Every method returns a formatted,
 * human-readable summary string, because a `tool_result` is prose the model narrates — not JSON.
 */

/** Visa rules + country essentials for `getVisaAndCountryEssentials` (visalookup + travelessentials). */
interface VisaEssentialsSource {
    /** Entry requirements, emergency numbers, tipping, plugs, water safety for [country]. */
    suspend fun essentials(country: String): String
}

/** Leave-by planning for `getDepartureRecommendation` (departureoptimizer). */
interface DepartureSource {
    /**
     * A leave-by recommendation for a flight out of [originIata] at [scheduledDepartureIso].
     * [lane] is an optional security-lane hint (e.g. "PreCheck"); [lat]/[lng] the traveler's
     * current position when known. All optional inputs may be null.
     */
    suspend fun recommendation(
        originIata: String,
        scheduledDepartureIso: String,
        lane: String?,
        lat: Double?,
        lng: Double?,
    ): String
}

/** Tracked-bag status for `flightActions(luggageStatus)` (luggagetracker). */
interface LuggageStatusSource {
    /** Every registered bag with status + last-seen scan, plus a claim estimate when in transit. */
    suspend fun luggageStatus(): String
}

/** Check-in state for `checkInForFlight` (checkin). */
interface CheckinStateSource {
    /** The next upcoming flight's ident (e.g. `DL1423`), or `null` when none is on file. */
    suspend fun nextFlightIdent(): String?

    /**
     * Marks [flightIdent] checked in (persisting the boarding assignment the Check-In screen
     * reads) and returns the outcome — success with boarding details, or why it couldn't happen
     * (unknown flight, window closed, already checked in).
     */
    suspend fun markCheckedIn(flightIdent: String): String
}

/** Aggregate of the four sources, so [IrisToolDispatcher] injects one collaborator, not four. */
class IrisToolDataSources @Inject constructor(
    val visaEssentials: VisaEssentialsSource,
    val departure: DepartureSource,
    val luggage: LuggageStatusSource,
    val checkin: CheckinStateSource,
)
