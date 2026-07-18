package com.jetsetter.pro.core.di

import android.content.Context
import com.jetsetter.pro.core.auth.EncryptedSessionManager
import com.jetsetter.pro.core.secrets.Secrets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

/**
 * Provides the shared Supabase client — the same project the iOS app talks to, so both
 * platforms read and write one database (protected by Row Level Security, not the client).
 *
 * The URL and publishable ("anon") key come from BuildConfig via [Secrets] (set SUPABASE_URL
 * and SUPABASE_ANON_KEY in local.properties / the environment). When either is missing the
 * provider returns null and callers fall back to mock/local data — the same "not configured"
 * doctrine the rest of the app follows (see [Secrets.isConfigured]).
 *
 * Postgrest covers table reads/writes; Auth supplies the per-user session that RLS policies
 * key off — persisted at rest via [EncryptedSessionManager] (Keystore-encrypted, plan B5)
 * instead of the default plaintext store; Realtime delivers live cross-device updates (postgres
 * changes); Functions invokes edge functions (the `delete-account` deletion flow). Add Storage
 * the same way when a feature needs it.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient? {
        val url = Secrets.supabaseUrl
        val key = Secrets.supabaseAnonKey
        if (!Secrets.isConfigured(url) || !Secrets.isConfigured(key)) return null
        return createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Postgrest)
            install(Auth) {
                sessionManager = EncryptedSessionManager(context, url)
            }
            install(Realtime)
            install(Functions)
        }
    }
}
