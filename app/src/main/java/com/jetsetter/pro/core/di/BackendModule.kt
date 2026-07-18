package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.backend.CloudBackend
import com.jetsetter.pro.core.backend.SupabaseBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the app's cloud seam ([CloudBackend]) to its only live implementation,
 * [SupabaseBackend]. This is the dual-seam decision's switch point: a Firebase (or other)
 * backend would be introduced by swapping this one binding — no call site changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackendModule {

    @Binds
    @Singleton
    abstract fun bindCloudBackend(impl: SupabaseBackend): CloudBackend
}
