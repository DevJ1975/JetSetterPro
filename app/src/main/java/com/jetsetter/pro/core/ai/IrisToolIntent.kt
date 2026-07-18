package com.jetsetter.pro.core.ai

/**
 * R1 tool-intent heuristic. The on-device tier (Gemini Nano via the ML Kit Prompt API) has no tool
 * calling, so when the user's turn plausibly needs one of IRIS's tools — and Claude is configured —
 * that turn is routed to Claude even though Nano is available. Lightweight keyword matching, pure,
 * and deliberately recall-biased: a false positive only costs a cloud round-trip; a false negative
 * leaves Nano answering conversationally without acting.
 *
 * (Divergence from iOS, where FoundationModels supports on-device tools, is recorded in
 * docs/IOS_PARITY_NOTES.md.)
 */
object IrisToolIntent {

    // One family per tool cluster: expense logging; trips/flights/tracking/booking; packing;
    // weather; visa; remember-preference; departure timing; luggage; loved-ones SMS; check-in.
    private val TOOL_KEYWORDS = Regex(
        "\\b(?:" +
            "expenses?|log(?:ged|ging)?|spen[dt]|spending|receipts?|" +
            "trips?|flights?|track(?:ed|ing)?|book(?:ed|ing)?|" +
            "pack(?:ed|ing)?|" +
            "weather|" +
            "visas?|" +
            "remember|" +
            "departures?|depart(?:ing)?|leav(?:e|ing)|" +
            "luggage|bags?|baggage|" +
            "sms|notify|text(?:ed|ing)?" +
            ")\\b" +
            "|\\bcheck[\\s-]?in\\b",
        RegexOption.IGNORE_CASE,
    )

    /** True when [text] plausibly requires a tool (expense, trip, weather, check-in, … verbs). */
    fun likelyNeedsTools(text: String): Boolean =
        text.isNotBlank() && TOOL_KEYWORDS.containsMatchIn(text)
}

/** Which tier serves a turn — the result of [IrisRouting.decide]. */
enum class IrisRoute { ON_DEVICE, CLAUDE, DEMO }

/**
 * R1 tier-routing decision, extracted pure so the order is pinned by unit tests:
 * on-device first whenever available (regardless of the Anthropic key) — except that a
 * tool-intent turn diverts to Claude when configured — else Claude when configured, else demo.
 */
object IrisRouting {
    fun decide(onDeviceAvailable: Boolean, claudeConfigured: Boolean, lastUserText: String): IrisRoute =
        when {
            onDeviceAvailable && !(claudeConfigured && IrisToolIntent.likelyNeedsTools(lastUserText)) ->
                IrisRoute.ON_DEVICE
            claudeConfigured -> IrisRoute.CLAUDE
            else -> IrisRoute.DEMO
        }
}
