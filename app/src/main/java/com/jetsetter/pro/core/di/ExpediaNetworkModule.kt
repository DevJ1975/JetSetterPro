package com.jetsetter.pro.core.di

import com.jetsetter.pro.BuildConfig
import com.jetsetter.pro.core.data.remote.expedia.ExpediaApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

/**
 * Expedia Rapid plumbing (plan B7) — a service-specific module so `NetworkModule` stays small.
 *
 * The baseUrl is the *data* host: Rapid's synthetic test environment in debug builds, production
 * otherwise (the iOS `Endpoints.swift` contract; see docs/API_REFERENCE.md). The token endpoint
 * always runs on the production host via an absolute URL on the method. The shared
 * `@Named("baseHttp")` client is used as-is (30 s timeouts + GET-only retry) — auth headers are
 * per-call parameters because the Bearer token comes from a suspending fetch, and the token POST
 * is never retried by policy.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExpediaNetworkModule {

    @Provides
    @Singleton
    @Named("expedia")
    fun provideExpediaRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(
                if (BuildConfig.DEBUG) "https://test.api.expediagroup.com/"
                else "https://api.expediagroup.com/",
            )
            .client(base)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideExpediaApi(@Named("expedia") retrofit: Retrofit): ExpediaApi =
        retrofit.create(ExpediaApi::class.java)
}
