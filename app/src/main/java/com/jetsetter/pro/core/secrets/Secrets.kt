package com.jetsetter.pro.core.secrets

import com.jetsetter.pro.BuildConfig

/**
 * Centralized access to API keys — the Android counterpart of the iOS `AppSecrets`.
 *
 * Keys are injected at build time from `local.properties` (or the environment) into
 * `BuildConfig` (see app/build.gradle.kts). A key is considered *not configured* if it
 * is blank or still a placeholder (`YOUR_…`, `REPLACE_ME`). Callers should fall back to
 * mock data when [isConfigured] is false — exactly as the iOS app does.
 */
object Secrets {
    val flightAware: String get() = BuildConfig.API_FLIGHTAWARE
    val anthropic: String get() = BuildConfig.API_ANTHROPIC
    val googleVision: String get() = BuildConfig.API_GOOGLE_VISION
    val uberServerToken: String get() = BuildConfig.API_UBER_SERVER_TOKEN
    val sitaWorldTracer: String get() = BuildConfig.API_SITA_WORLDTRACER
    val mapsApiKey: String get() = BuildConfig.MAPS_API_KEY

    /** Expedia Rapid — OAuth2 client-credentials pair. */
    val expediaClientId: String get() = BuildConfig.API_EXPEDIA_CLIENT_ID
    val expediaClientSecret: String get() = BuildConfig.API_EXPEDIA_CLIENT_SECRET

    /** Lyft — OAuth2 client-credentials pair. */
    val lyftClientId: String get() = BuildConfig.API_LYFT_CLIENT_ID
    val lyftClientSecret: String get() = BuildConfig.API_LYFT_CLIENT_SECRET

    /** Car-rental partners (deep links + placeholder search). */
    val enterprise: String get() = BuildConfig.API_ENTERPRISE
    val hertz: String get() = BuildConfig.API_HERTZ
    val national: String get() = BuildConfig.API_NATIONAL

    /** Expense-report integrations (submitExpenses providers). */
    val expensifyPartnerKey: String get() = BuildConfig.API_EXPENSIFY_PARTNER_KEY
    val rampClientId: String get() = BuildConfig.API_RAMP_CLIENT_ID
    val rampClientSecret: String get() = BuildConfig.API_RAMP_CLIENT_SECRET
    val brexClientId: String get() = BuildConfig.API_BREX_CLIENT_ID
    val divvyClientId: String get() = BuildConfig.API_DIVVY_CLIENT_ID

    /** Supabase — the shared backend the iOS app also uses. */
    val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    /** True when [value] is a real, usable key (non-blank and not a placeholder). */
    fun isConfigured(value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() &&
            !v.startsWith("YOUR_", ignoreCase = true) &&
            !v.equals("REPLACE_ME", ignoreCase = true)
    }
}
