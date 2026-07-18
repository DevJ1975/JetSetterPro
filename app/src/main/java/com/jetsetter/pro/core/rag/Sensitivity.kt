package com.jetsetter.pro.core.rag

/**
 * Privacy classification of a knowledge chunk. This is the spine of IRIS's RAG privacy model:
 *
 *  - [PUBLIC]   — general, non-personal travel knowledge (visa rules, baggage limits, etiquette …).
 *                 Shippable in the app and regenerable.
 *  - [PERSONAL] — anything derived from this user (their trips, expenses, learned preferences).
 *
 * Contract (parity spec): BOTH sensitivities may ground BOTH IRIS tiers — on-device Gemini Nano
 * and Anthropic Claude are each sanctioned AI processors for personalization (see
 * [ContextAssembler]). The invariant that remains: PERSONAL data is never sent to third-party
 * DATA APIs (FlightAware, Open-Meteo, FX, SITA, …) — those requests carry only IATA codes,
 * coordinates, currency codes, and flight idents.
 *
 * Persisted to Room as the [wire] string.
 */
enum class Sensitivity(val wire: String) {
    PUBLIC("PUBLIC"),
    PERSONAL("PERSONAL");

    companion object {
        fun fromWire(value: String): Sensitivity =
            entries.firstOrNull { it.wire == value } ?: PERSONAL // fail closed: unknown ⇒ treat as personal
    }
}

/** Which IRIS tier is being served — decides the sensitivity allow-set in [ContextAssembler]. */
enum class AiTier { ON_DEVICE, CLOUD }
