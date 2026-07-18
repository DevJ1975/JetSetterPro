package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.data.remote.lyft.LyftApi
import com.jetsetter.pro.core.data.remote.lyft.LyftAuthApi
import com.jetsetter.pro.core.data.remote.uber.UberApi
import com.jetsetter.pro.core.secrets.Secrets
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
 * Retrofit wiring for the ride-estimate services (plan B7: Uber + Lyft), kept out of
 * [NetworkModule] so the base §2.1 stack stays small. Both clients derive from the shared
 * `@Named("baseHttp")` OkHttp client (30 s timeouts, GET-only retry, logging) so they share
 * the connection pool and dispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object RideNetworkModule {

    // ── Uber Riders API ─────────────────────────────────────────────────────
    // Server-token auth (`Authorization: Token …`) attached per request; the token is read
    // from Secrets inside the interceptor so an unconfigured build simply sends a header
    // Uber rejects with 401 → typed ApiError (callers never get here unconfigured anyway —
    // they gate on UberService.isConfigured).
    @Provides
    @Singleton
    @Named("uber")
    fun provideUberRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit {
        val client = base.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Token ${Secrets.uberServerToken}")
                    .addHeader("Accept-Language", "en_US")
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.uber.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideUberApi(@Named("uber") retrofit: Retrofit): UberApi =
        retrofit.create(UberApi::class.java)

    // ── Lyft Public API ─────────────────────────────────────────────────────
    // No auth interceptor: the OAuth2 bearer token is a suspend fetch (LyftTokenProvider),
    // so LyftService passes `Authorization` as a per-call header parameter. Both the token
    // endpoint and /v1/cost live on the same host and share one Retrofit.
    @Provides
    @Singleton
    @Named("lyft")
    fun provideLyftRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.lyft.com/")
            .client(base)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideLyftApi(@Named("lyft") retrofit: Retrofit): LyftApi =
        retrofit.create(LyftApi::class.java)

    @Provides
    @Singleton
    fun provideLyftAuthApi(@Named("lyft") retrofit: Retrofit): LyftAuthApi =
        retrofit.create(LyftAuthApi::class.java)
}
