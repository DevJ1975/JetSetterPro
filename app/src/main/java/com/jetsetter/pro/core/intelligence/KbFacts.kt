package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.rag.RetrievalService
import com.jetsetter.pro.core.rag.Sensitivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolved, non-personal KB snippets for the next trip's destination, assembled by the Home
 * suggestion inputs and folded into the pure [IrisSuggestionEngine]'s visa/weather bodies (plan
 * R10b) so the engine does no I/O itself. All fields are PUBLIC knowledge
 * (visa/entry/packing/loyalty), so a KbFacts may also inform cloud-tier answers.
 */
data class KbFacts(
    val destination: String? = null,
    val visaNote: String? = null,
    val packingNote: String? = null,
    val loyaltyTip: String? = null,
) {
    fun isEmpty(): Boolean = visaNote == null && packingNote == null && loyaltyTip == null
}

/**
 * Resolves [KbFacts] from the on-device KB via [RetrievalService]. On devices without the on-device
 * embedder this returns empty facts (retrieval yields nothing) — proactive nudges then degrade to
 * the data-only set, never erroring.
 */
@Singleton
class KbFactsResolver @Inject constructor(
    private val retrieval: RetrievalService,
) {
    suspend fun resolve(destination: String?): KbFacts {
        if (destination.isNullOrBlank()) return KbFacts()
        val pub = setOf(Sensitivity.PUBLIC)
        suspend fun top(query: String): String? =
            retrieval.retrieve(query, k = 1, allowed = pub).firstOrNull()?.text

        return KbFacts(
            destination = destination,
            visaNote = top("visa and entry requirements for $destination"),
            packingNote = top("what to pack for $destination weather and season"),
            loyaltyTip = top("airline loyalty elite status tips"),
        )
    }
}
