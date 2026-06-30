package com.jetsetter.pro.feature.visalookup

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of visa / entry requirements for popular destinations.
 *
 * Backed by the bundled [VisaEntryTable] — there is no network here. On iOS this same screen is
 * fed by a Sherpa/Timatic-style entry-requirements service; on Android we ship a curated, in-code
 * snapshot (for U.S. passport holders) so the Visa & Entry tab always has realistic data to
 * interact with. Rules are stored as numbers so the screen's figures and calculations stay
 * self-consistent — see [VisaEntryItem].
 */
@Singleton
class VisalookupRepository @Inject constructor() {

    /** Returns the curated destinations. Suspend to mirror the repo contract used elsewhere. */
    suspend fun getDestinations(): List<VisaEntryItem> = VisaEntryTable.destinations

    /**
     * Look up the entry requirement for a single destination — by item id, country name, or
     * free-text. Unknown destinations resolve to a conservative "visa required / check
     * requirements" default instead of being treated as visa-free. See [VisaEntryTable.requirementFor].
     */
    fun requirementFor(destination: String): VisaEntryItem =
        VisaEntryTable.requirementFor(destination)
}
