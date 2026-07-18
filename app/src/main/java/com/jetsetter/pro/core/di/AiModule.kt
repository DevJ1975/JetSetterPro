package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.ai.ExpenseCategorizer
import com.jetsetter.pro.core.ai.GeminiNanoOnDeviceAi
import com.jetsetter.pro.core.ai.NanoExpenseCategorizer
import com.jetsetter.pro.core.ai.OnDeviceAi
import com.jetsetter.pro.core.ai.TravelPersonaProvider
import com.jetsetter.pro.core.ai.TravelProfileSummaryProvider
import com.jetsetter.pro.core.intelligence.StoreTravelPersonaProvider
import com.jetsetter.pro.core.intelligence.StoreTravelProfileSummaryProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the IRIS AI seams.
 *
 * [OnDeviceAi] resolves to [GeminiNanoOnDeviceAi] (ML Kit GenAI Prompt API / Gemini Nano). It self-
 * gates: on devices without AICore, or before the model is downloaded, `isAvailable()` returns false
 * and IRIS routing falls through to the Anthropic Claude tier and then the demo fallback — so the
 * change is safe on every device. [com.jetsetter.pro.core.ai.UnavailableOnDeviceAi] remains as the
 * documented inert fallback for builds that want the tier hard-off.
 *
 * [ExpenseCategorizer] resolves to [NanoExpenseCategorizer] (Gemini Nano, same self-gating):
 * whenever the on-device model isn't AVAILABLE it returns `null` and expense entry stays fully
 * manual. [com.jetsetter.pro.core.ai.NoopExpenseCategorizer] remains as the documented inert
 * fallback for builds that want categorization hard-off.
 *
 * [TravelPersonaProvider] / [TravelProfileSummaryProvider] resolve to the real
 * `TravelProfileStore`-backed implementations (plan A4): the system prompt and the
 * `getLearnedTravelProfile` tool serve the learned profile + generated persona, both of which
 * degrade to `""` while learning is disabled or nothing has been learned.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindOnDeviceAi(impl: GeminiNanoOnDeviceAi): OnDeviceAi

    @Binds
    @Singleton
    abstract fun bindTravelPersonaProvider(impl: StoreTravelPersonaProvider): TravelPersonaProvider

    @Binds
    @Singleton
    abstract fun bindTravelProfileSummaryProvider(
        impl: StoreTravelProfileSummaryProvider,
    ): TravelProfileSummaryProvider

    @Binds
    @Singleton
    abstract fun bindExpenseCategorizer(impl: NanoExpenseCategorizer): ExpenseCategorizer
}
