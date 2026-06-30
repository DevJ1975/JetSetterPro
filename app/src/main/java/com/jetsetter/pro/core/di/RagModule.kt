package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.rag.MediaPipeTextEmbedder
import com.jetsetter.pro.core.rag.OnDeviceEmbedder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device RAG seams. [OnDeviceEmbedder] resolves to [MediaPipeTextEmbedder]; the
 * retrieval/KB singletons (KbRepository, RetrievalService, ContextAssembler) are constructor-injected
 * and need no explicit provider.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RagModule {

    @Binds
    @Singleton
    abstract fun bindOnDeviceEmbedder(impl: MediaPipeTextEmbedder): OnDeviceEmbedder
}
