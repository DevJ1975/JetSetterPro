package com.jetsetter.pro.core.ai

/**
 * The twelve app surfaces IRIS's `openScreen` tool can navigate to (spec §1.3).
 *
 * [wireName] is the camelCase value in the tool schema — the cross-platform contract with iOS,
 * never rename. [route] is the Android navigation route the screen actually lives at (tab routes
 * from `ui/navigation/Destinations.kt`, feature routes from `ui/navigation/FeatureCatalog.kt`).
 * Per plan R10(a), [PACKING_LIST] routes to the Itinerary tab — the app has no dedicated
 * trip-detail/packing route; packing lists are edited there.
 */
enum class IrisScreen(val wireName: String, val route: String, val label: String) {
    HOME("home", "home", "Home"),
    ITINERARY("itinerary", "itinerary", "Itinerary"),
    IRIS("iris", "iris", "IRIS"),
    EXPENSES("expenses", "expenses", "Expenses"),
    MORE("more", "more", "More"),
    CHECK_IN("checkIn", "checkin", "Check-In"),
    DISRUPTION("disruption", "disruption", "Disruption Monitor"),
    FLIGHT_TRACKER("flightTracker", "flighttracker", "Flight Tracker"),
    DOCUMENT_VAULT("documentVault", "documentvault", "Document Vault"),
    PACKING_LIST("packingList", "itinerary", "Packing List"),
    GROUND_TRANSPORT("groundTransport", "groundtransport", "Ground Transport"),
    CURRENCY("currency", "currencytracker", "Currency"),
    ;

    companion object {
        /** Resolves a tool-supplied wire name (case-insensitive); `null` when unknown. */
        fun fromWire(raw: String?): IrisScreen? =
            entries.firstOrNull { it.wireName.equals(raw?.trim(), ignoreCase = true) }

        /** The schema `enum` values, in declaration order. */
        val wireNames: List<String> get() = entries.map { it.wireName }
    }
}
