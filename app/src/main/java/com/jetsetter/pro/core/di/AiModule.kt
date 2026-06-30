package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.ai.GeminiNanoOnDeviceAi
import com.jetsetter.pro.core.ai.OnDeviceAi
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
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindOnDeviceAi(impl: GeminiNanoOnDeviceAi): OnDeviceAi
}
