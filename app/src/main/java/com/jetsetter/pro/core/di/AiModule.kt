package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.ai.OnDeviceAi
import com.jetsetter.pro.core.ai.UnavailableOnDeviceAi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the IRIS AI seams to their default implementations.
 *
 * [OnDeviceAi] resolves to [UnavailableOnDeviceAi] (a no-op that reports the on-device tier as
 * unavailable), so IRIS routing falls through to the Anthropic Claude tier and then the demo
 * fallback. Swap this binding for a real ML Kit GenAI / AICore (Gemini Nano) implementation to
 * light up the on-device tier without touching the repository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindOnDeviceAi(impl: UnavailableOnDeviceAi): OnDeviceAi
}
