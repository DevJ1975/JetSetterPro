package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.data.remote.vision.VisionApi
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
 * Google Vision wiring (plan B7). Own module file so `NetworkModule` stays small — the
 * Retrofit instance derives from the shared `baseHttp` client (30 s timeouts, logging;
 * `images:annotate` is a POST, so the GET-only retry policy never replays it).
 *
 * No auth interceptor: Vision authenticates via a `?key=` query parameter, which
 * `GoogleVisionService` supplies per call from `Secrets`.
 */
@Module
@InstallIn(SingletonComponent::class)
object VisionNetworkModule {

    @Provides
    @Singleton
    @Named("vision")
    fun provideVisionRetrofit(moshi: Moshi, @Named("baseHttp") base: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://vision.googleapis.com/")
            .client(base)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideVisionApi(@Named("vision") retrofit: Retrofit): VisionApi =
        retrofit.create(VisionApi::class.java)
}
