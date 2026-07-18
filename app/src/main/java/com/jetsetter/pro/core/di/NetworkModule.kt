package com.jetsetter.pro.core.di

import com.jetsetter.pro.BuildConfig
import com.jetsetter.pro.core.data.remote.FlightAwareApi
import com.jetsetter.pro.core.data.remote.IsoDateAdapters
import com.jetsetter.pro.core.data.remote.RetryInterceptor
import com.jetsetter.pro.core.data.remote.fx.FrankfurterApi
import com.jetsetter.pro.core.secrets.Secrets
import com.jetsetter.pro.core.weather.OpenMeteoApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * The §2.1 HTTP stack. One base OkHttp client carries the shared policy (30 s timeouts,
 * GET-only retry with backoff/jitter, logging); service clients derive from it via
 * `newBuilder()` so they share the connection pool and dispatcher. Wire naming is per-field
 * `@Json(name = "snake_case")` on DTOs — no global naming strategy — and dates are ISO-8601
 * via [IsoDateAdapters]. Supabase stays on its own Ktor stack (documented divergence).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(IsoDateAdapters())
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideLogging(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    // ── Base client: the single §2.1 policy ─────────────────────────────────
    @Provides
    @Singleton
    @Named("baseHttp")
    fun provideBaseHttp(logging: HttpLoggingInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor())
        .addInterceptor(logging)
        .build()

    // ── SSE client (Anthropic streaming) ────────────────────────────────────
    // Derived from baseHttp: quick connect, no read timeout so the SSE stream isn't cut
    // mid-response, a call timeout bounding the whole exchange, and the RetryInterceptor
    // stripped — a replayed streaming POST would double-bill and re-emit.
    @Provides
    @Singleton
    @Named("sseHttp")
    fun provideSseHttp(@Named("baseHttp") base: OkHttpClient): OkHttpClient = base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .apply { interceptors().removeAll { it is RetryInterceptor } }
        .build()

    // ── FlightAware AeroAPI ──────────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("flightaware")
    fun provideFlightAwareRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit {
        val client = base.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-apikey", Secrets.flightAware)
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://aeroapi.flightaware.com/aeroapi/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideFlightAwareApi(@Named("flightaware") retrofit: Retrofit): FlightAwareApi =
        retrofit.create(FlightAwareApi::class.java)

    // ── Open-Meteo (keyless weather + geocoding) ─────────────────────────────
    // No auth header, so the base client is used as-is (30 s timeouts + GET retry).
    // The API interface carries absolute URLs per method — geocoding and forecast live on
    // different hosts — so the baseUrl here is only a formal default.
    @Provides
    @Singleton
    @Named("openmeteo")
    fun provideOpenMeteoRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(base)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(@Named("openmeteo") retrofit: Retrofit): OpenMeteoApi =
        retrofit.create(OpenMeteoApi::class.java)

    // ── Frankfurter (keyless live FX, ECB provenance) ────────────────────────
    // No auth, so the base client is used as-is (30 s timeouts + GET retry).
    // Canonical host is api.frankfurter.dev/v1 (frankfurter.app is the legacy alias).
    @Provides
    @Singleton
    @Named("frankfurter")
    fun provideFrankfurterRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.dev/v1/")
            .client(base)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideFrankfurterApi(@Named("frankfurter") retrofit: Retrofit): FrankfurterApi =
        retrofit.create(FrankfurterApi::class.java)
}
