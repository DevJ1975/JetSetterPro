package com.jetsetter.pro.core.intelligence

import com.jetsetter.pro.core.ai.TravelPersonaProvider
import com.jetsetter.pro.core.ai.TravelProfileSummaryProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real [TravelPersonaProvider] (plan A4): serves [TravelProfileStore.persona] — the cached,
 * consent-gated traveler persona — into the IRIS system prompt. Replaces the `Empty*` default
 * binding in `core/di/AiModule`; still `""` whenever learning is off or nothing is learned.
 */
@Singleton
class StoreTravelPersonaProvider @Inject constructor(
    private val store: TravelProfileStore,
) : TravelPersonaProvider {
    override suspend fun personaForPrompt(): String = store.persona()
}

/**
 * The real [TravelProfileSummaryProvider] (plan A4): serves the learned profile's
 * [TravelProfileData.summaryForPrompt] from [TravelProfileStore] — `""` while learning is
 * disabled or the profile is empty (the store returns the empty profile in both cases).
 * Also backs the `getLearnedTravelProfile` IRIS tool.
 */
@Singleton
class StoreTravelProfileSummaryProvider @Inject constructor(
    private val store: TravelProfileStore,
) : TravelProfileSummaryProvider {
    override suspend fun summaryForPrompt(): String = store.profile().summaryForPrompt()
}
