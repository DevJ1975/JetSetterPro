package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerApi
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
 * SITA WorldTracer wiring (plan B7). Own module file so `NetworkModule` stays small — the
 * client derives from the shared `baseHttp` (30 s timeouts, GET retry, logging) via
 * `newBuilder()`, adding only the `x-partner-key` auth header (§2.3), mirroring the
 * FlightAware pattern.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorldTracerNetworkModule {

    @Provides
    @Singleton
    @Named("worldtracer")
    fun provideWorldTracerRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit {
        val client = base.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-partner-key", Secrets.sitaWorldTracer)
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.sita.aero/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideWorldTracerApi(@Named("worldtracer") retrofit: Retrofit): WorldTracerApi =
        retrofit.create(WorldTracerApi::class.java)
}
