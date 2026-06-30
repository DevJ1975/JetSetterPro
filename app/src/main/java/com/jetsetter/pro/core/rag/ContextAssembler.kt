package com.jetsetter.pro.core.rag

import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.TravelProfile
import com.jetsetter.pro.core.intelligence.UserMemory
import com.jetsetter.pro.core.rag.RetrievalService.RetrievedChunk
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the grounding context IRIS prepends to its system prompt for a turn — and enforces the
 * privacy gate that is the heart of the design.
 *
 * The sensitivity allow-set is keyed off the [AiTier], structurally:
 *  - [AiTier.ON_DEVICE] → `{PUBLIC, PERSONAL}` plus a private "Traveler context" block (learned
 *    profile + next trip). All of this stays on the handset (Gemini Nano).
 *  - [AiTier.CLOUD] → `{PUBLIC}` only. The cloud path never retrieves PERSONAL chunks and never
 *    touches [TravelProfile], so nothing personal can reach Claude. A `require` backs this up.
 */
@Singleton
class ContextAssembler @Inject constructor(
    private val retrieval: RetrievalService,
    private val tripRepository: TripRepository,
    private val travelProfile: TravelProfile,
    private val userMemory: UserMemory,
) {
    data class GroundingContext(val systemBlock: String, val sources: List<RetrievedChunk>) {
        fun isEmpty(): Boolean = systemBlock.isBlank()
    }

    suspend fun assemble(query: String, tier: AiTier): GroundingContext {
        val allowed = when (tier) {
            AiTier.ON_DEVICE -> setOf(Sensitivity.PUBLIC, Sensitivity.PERSONAL)
            AiTier.CLOUD -> setOf(Sensitivity.PUBLIC)
        }
        val sources = retrieval.retrieve(query, allowed = allowed)

        // PRIVACY (defense in depth): personal context can never be assembled for the cloud tier.
        require(tier == AiTier.ON_DEVICE || sources.none { it.sensitivity == Sensitivity.PERSONAL }) {
            "Personal context must never be assembled for the cloud tier."
        }

        val sb = StringBuilder()
        if (sources.isNotEmpty()) {
            sb.append("\n\n## Knowledge\n")
            sb.append("Use these facts when relevant. If they don't cover the question, answer from general knowledge and say so.\n")
            sources.forEach { sb.append("- ").append(it.text.trim()).append('\n') }
        }
        if (tier == AiTier.ON_DEVICE) {
            travelerContext()?.let { sb.append("\n\n## Traveler context (on-device, private)\n").append(it) }
        }
        return GroundingContext(sb.toString(), sources)
    }

    /** A concise, structured summary of who this traveler is — on-device tier only. */
    private suspend fun travelerContext(): String? {
        val profile = runCatching { travelProfile.current() }.getOrNull() ?: return null
        val trips = runCatching { tripRepository.observeTrips().first() }.getOrElse { emptyList() }

        val lines = buildList {
            if (profile.tripCount > 0) add("- Trips logged: ${profile.tripCount}")
            profile.topDestination?.takeIf { it.isNotBlank() }?.let { add("- Most-visited destination: $it") }
            profile.homeAirport.takeIf { it.isNotBlank() }?.let { add("- Home airport: $it") }
            if (profile.totalSpend > 0) add("- Total tracked spend: $${"%.2f".format(profile.totalSpend)}")

            val today = LocalDate.now().toString()
            trips.filter { it.startDate >= today }
                .sortedBy { it.startDate }
                .take(2)
                .forEach { add("- Upcoming: ${it.name} to ${it.destination} (${it.startDate}→${it.endDate})") }

            // Learned preferences (PERSONAL — on-device only).
            runCatching { userMemory.topPreferences().toContext().lines }.getOrDefault(emptyList())
                .forEach { add(it) }
        }
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }
}
