package com.jetsetter.pro.core.backend

import com.jetsetter.pro.core.model.PackingItem

/**
 * Backend-neutral document types for the [CloudBackend] collections (spec §2.4). These are the
 * *wire-adjacent* shapes call sites hand the seam — deliberately plain (strings + primitives) so
 * `core/backend` depends on no feature module and any provider can serialize them. Mapping to the
 * app's richer domain/UI types happens at the call site.
 */

/**
 * The signed-in user as the backend sees it. [isAnonymous] is derived from the account having no
 * email attached — the anonymous-first flow's only distinguishing mark ([CloudBackend.ensureSignedIn]
 * creates email-less users; [CloudBackend.linkEmailToAnonymous] upgrades them).
 */
data class CloudSession(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
)

/**
 * One synced travel-behavior signal (spec §1.6). [kind] is the camelCase wire name
 * ([com.jetsetter.pro.core.intelligence.TravelSignal.Kind.wireName], e.g. `"seatChosen"`) and
 * [payloadJson] the signal's JSON encoding — the seam stays schema-agnostic so signal fields can
 * evolve without a backend change. [updatedAt] is ISO-8601.
 */
data class TravelSignalDoc(
    val id: String,
    val kind: String,
    val payloadJson: String,
    val updatedAt: String,
)

/**
 * One Travel Wallet pass, mirroring
 * [com.jetsetter.pro.feature.travelwallet.TravelWalletItem] field-for-field. [type] is the
 * `TravelWalletPassType` enum constant name (e.g. `"BOARDING_PASS"`) kept as a string so core
 * doesn't depend on the feature module; unknown values from a newer client map to a fallback at
 * the call site.
 */
data class TravelWalletItemDoc(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val keyDetailLabel: String,
    val keyDetailValue: String,
    val date: String,
    val isFavorite: Boolean = false,
)

/**
 * A trip's packing list as its own logical collection (spec §2.4 `packingLists/{tripId}`). The
 * document id IS the [tripId] — one list per trip. Physical storage is provider-specific (Supabase
 * keeps it embedded in the trip row's `packing_list` jsonb column, shared with iOS).
 */
data class PackingListDoc(
    val tripId: String,
    val items: List<PackingItem>,
)

/**
 * One detected flight disruption (spec §2.4). [status] carries the disruption state label
 * (e.g. `"DELAYED"`, `"CANCELLED"`); [detectedAt] is ISO-8601; [payloadJson] holds the full
 * provider payload for forward-compatible detail without schema churn.
 */
data class DisruptionEventDoc(
    val id: String,
    val flightNumber: String,
    val route: String,
    val status: String,
    val reason: String,
    val detectedAt: String,
    val payloadJson: String,
)
