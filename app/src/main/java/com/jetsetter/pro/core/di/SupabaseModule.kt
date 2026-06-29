package com.jetsetter.pro.core.di

import com.jetsetter.pro.core.secrets.Secrets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

/**
 * Provides the shared Supabase client — the same project the iOS app talks to, so both
 * platforms read and write one database (protected by Row Level Security, not the client).
 *
 * The URL and publishable ("anon") key come from BuildConfig via [Secrets] (set SUPABASE_URL
 * and SUPABASE_ANON_KEY in local.properties / the environment). When either is missing the
 * provider returns null and callers fall back to mock/local data — the same "not configured"
 * doctrine the rest of the app follows (see [Secrets.isConfigured] and FirebaseModule).
 *
 * Postgrest covers table reads/writes; Auth supplies the per-user session that RLS policies
 * key off. Add Realtime/Storage the same way when a feature needs them.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient? {
        val url = Secrets.supabaseUrl
        val key = Secrets.supabaseAnonKey
        if (!Secrets.isConfigured(url) || !Secrets.isConfigured(key)) return null
        return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Postgrest)
            install(Auth)
        }
    }
}
