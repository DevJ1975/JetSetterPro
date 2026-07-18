package com.jetsetter.pro.core.ai

import com.jetsetter.pro.core.intelligence.IrisMemory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the generated 2–3 sentence traveler persona (spec §1.6) for the system prompt.
 * The default binding ([EmptyTravelPersonaProvider], see `core/di/AiModule`) returns `""` until
 * the learning switchover (A4) rebinds the real `TravelPersonaGenerator`-backed implementation.
 */
interface TravelPersonaProvider {
    /** The cached traveler persona, or `""` when none has been generated. */
    suspend fun personaForPrompt(): String
}

/** Default [TravelPersonaProvider]: no persona until the learning stage rebinds a real one. */
@Singleton
class EmptyTravelPersonaProvider @Inject constructor() : TravelPersonaProvider {
    override suspend fun personaForPrompt(): String = ""
}

/**
 * Supplies the learned travel-profile summary (spec §1.6, `TravelProfileData.summaryForPrompt()`)
 * for the system prompt. The default binding ([EmptyTravelProfileSummaryProvider], see
 * `core/di/AiModule`) returns `""` until the learning switchover (A4) rebinds the real
 * `TravelProfileStore`-backed implementation — consistent with the spec contract that an empty
 * profile contributes nothing to the prompt.
 */
interface TravelProfileSummaryProvider {
    /** The learned-profile summary, or `""` when nothing has been learned (or learning is off). */
    suspend fun summaryForPrompt(): String
}

/** Default [TravelProfileSummaryProvider]: empty until the learning stage rebinds a real one. */
@Singleton
class EmptyTravelProfileSummaryProvider @Inject constructor() : TravelProfileSummaryProvider {
    override suspend fun summaryForPrompt(): String = ""
}

/**
 * Assembles the full IRIS system prompt served to BOTH tiers (on-device Gemini Nano and Anthropic
 * Claude): the static [IrisPersona.BASE_PROMPT] plus the spec §1.2 session-start appends, in iOS
 * order — stored preferences summary, traveler persona, learned travel profile summary, live
 * context snapshot. Empty sections are omitted entirely.
 *
 * The result is cached on a hash of the four dynamic inputs (= the iOS "session recreation"
 * semantics: the prompt is rebuilt only when its inputs change), and the dynamic sections are
 * trimmed to [MAX_DYNAMIC_CHARS] (~4k est. tokens) by dropping live-context itinerary bullets
 * first, then profile ranking bullets. The pure assembly lives in [assemble] so tests can pin the
 * contract without DI.
 */
@Singleton
class IrisSystemPromptBuilder @Inject constructor(
    private val irisMemory: IrisMemory,
    private val liveContextBuilder: LiveContextBuilder,
    private val personaProvider: TravelPersonaProvider,
    private val profileProvider: TravelProfileSummaryProvider,
) {
    private val cache = IrisPromptCache()

    /** The full system prompt for this session; each source degrades to `""` on failure. */
    suspend fun build(): String {
        val prefs = runCatching { irisMemory.summaryForPrompt() }.getOrDefault("")
        val persona = runCatching { personaProvider.personaForPrompt() }.getOrDefault("")
        val profile = runCatching { profileProvider.summaryForPrompt() }.getOrDefault("")
        val liveContext = runCatching { liveContextBuilder.build() }.getOrDefault("")
        return cache.promptFor(IrisPersona.BASE_PROMPT, prefs, persona, profile, liveContext)
    }

    companion object {
        /** Budget for the dynamic sections (~4k est. tokens at ~4 chars/token). */
        internal const val MAX_DYNAMIC_CHARS = 16_000

        internal const val PREFS_LABEL = "STORED PREFERENCES:"
        internal const val PERSONA_LABEL = "TRAVELER PERSONA:"

        /**
         * Pure prompt assembly: [base] + the non-empty dynamic sections in iOS order, separated by
         * blank lines. Preferences and persona get a "LABEL:" header (per the FORMAT rule); the
         * profile summary and live context arrive self-labeled. When the dynamic block exceeds
         * [maxDynamicChars], `- ` bullet lines are dropped from the live context first (the
         * itinerary items), then from the profile summary (the ranking lines), until it fits or no
         * bullets remain.
         */
        internal fun assemble(
            base: String,
            prefs: String,
            persona: String,
            profile: String,
            liveContext: String,
            maxDynamicChars: Int = MAX_DYNAMIC_CHARS,
        ): String {
            val p = prefs.trim()
            val per = persona.trim()
            var prof = profile.trim()
            var live = liveContext.trim()

            var dynamic = renderDynamic(p, per, prof, live)
            while (dynamic.length > maxDynamicChars) {
                val liveTrimmed = dropLastBullet(live)
                if (liveTrimmed != null) {
                    live = liveTrimmed
                } else {
                    prof = dropLastBullet(prof) ?: break // nothing left to trim
                }
                dynamic = renderDynamic(p, per, prof, live)
            }
            return base + dynamic
        }

        /** The dynamic block, starting with a blank-line separator; `""` when every section is empty. */
        private fun renderDynamic(prefs: String, persona: String, profile: String, live: String): String {
            val sections = buildList {
                if (prefs.isNotBlank()) add("$PREFS_LABEL\n$prefs")
                if (persona.isNotBlank()) add("$PERSONA_LABEL\n$persona")
                if (profile.isNotBlank()) add(profile)
                if (live.isNotBlank()) add(live)
            }
            return if (sections.isEmpty()) "" else "\n\n" + sections.joinToString("\n\n")
        }

        /** [block] without its last `- ` bullet line, or null when it has none. */
        private fun dropLastBullet(block: String): String? {
            val lines = block.lines()
            val index = lines.indexOfLast { it.trimStart().startsWith("- ") }
            if (index < 0) return null
            return lines.filterIndexed { i, _ -> i != index }.joinToString("\n")
        }
    }
}

/**
 * Holds the last (inputs-hash, prompt) pair so [IrisSystemPromptBuilder.build] re-assembles only
 * when one of the four dynamic strings actually changed. Pure and DI-free so tests can drive it.
 */
internal class IrisPromptCache {
    private var inputsHash: Int = 0
    private var prompt: String? = null

    /** How many times a prompt was (re)assembled — a cache-behavior probe for tests. */
    internal var rebuilds: Int = 0
        private set

    @Synchronized
    internal fun promptFor(
        base: String,
        prefs: String,
        persona: String,
        profile: String,
        liveContext: String,
        maxDynamicChars: Int = IrisSystemPromptBuilder.MAX_DYNAMIC_CHARS,
    ): String {
        val hash = listOf(prefs, persona, profile, liveContext).hashCode()
        prompt?.let { cached -> if (hash == inputsHash) return cached }
        rebuilds++
        return IrisSystemPromptBuilder.assemble(base, prefs, persona, profile, liveContext, maxDynamicChars)
            .also {
                inputsHash = hash
                prompt = it
            }
    }
}
