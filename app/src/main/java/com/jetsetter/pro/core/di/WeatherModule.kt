package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.weather.OpenMeteoWeatherService
import com.jetsetter.pro.core.weather.WeatherService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the weather seam. [OpenMeteoWeatherService] is keyless — there is no `isConfigured`
 * gate and no mock fallback; the implementation is always live and returns null on failure.
 * The Retrofit plumbing it rides on lives in [NetworkModule] (derived from `baseHttp`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {

    @Binds
    @Singleton
    abstract fun bindWeatherService(impl: OpenMeteoWeatherService): WeatherService
}
