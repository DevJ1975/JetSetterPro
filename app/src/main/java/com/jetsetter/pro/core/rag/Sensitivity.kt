package com.jetsetter.pro.core.rag

/**
 * Privacy classification of a knowledge chunk. This is the spine of IRIS's RAG privacy model:
 *
 *  - [PUBLIC]   — general, non-personal travel knowledge (visa rules, baggage limits, etiquette …).
 *                 Shippable in the app, regenerable, and safe to send to any tier including Claude.
 *  - [PERSONAL] — anything derived from this user (their trips, expenses, learned preferences).
 *                 On-device tiers only — must never enter a Claude/cloud request.
 *
 * Persisted to Room as the [wire] string. The tier→sensitivity gate is enforced in
 * [ContextAssembler].
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
