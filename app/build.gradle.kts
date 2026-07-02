import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── Secrets ──────────────────────────────────────────────────────────────────
// API keys are read from local.properties (git-ignored) or the environment and
// surfaced to code via BuildConfig. This mirrors the iOS Secrets.xcconfig flow.
// A missing key resolves to "" — services treat an empty key as "not configured"
// and fall back to mock data (see core/secrets/Secrets.kt).
val secretProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = secretProps.getProperty(key) ?: System.getenv(key) ?: ""

val secretKeys = listOf(
    "API_FLIGHTAWARE", "API_ANTHROPIC", "API_GOOGLE_VISION",
    "API_EXPEDIA_CLIENT_ID", "API_EXPEDIA_CLIENT_SECRET",
    "API_AMADEUS_CLIENT_ID", "API_AMADEUS_CLIENT_SECRET", "API_DUFFEL",
    "API_SITA_WORLDTRACER", "API_UBER_SERVER_TOKEN",
    "API_LYFT_CLIENT_ID", "API_LYFT_CLIENT_SECRET",
    "API_ENTERPRISE", "API_HERTZ", "API_NATIONAL",
    "API_EXPENSIFY_PARTNER_KEY", "API_RAMP_CLIENT_ID", "API_RAMP_CLIENT_SECRET",
    "API_BREX_CLIENT_ID", "API_DIVVY_CLIENT_ID",
    "MAPS_API_KEY",
    // Supabase — shared backend with the iOS app. URL + publishable ("anon") key.
    "SUPABASE_URL", "SUPABASE_ANON_KEY",
)

// ── Release signing ──────────────────────────────────────────────────────────
// Reads keystore.properties (git-ignored). Absent on machines without the keystore, in which
// case the release build falls back to debug signing so it still assembles.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jetsetter.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trainovate.jetsetterpro"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        secretKeys.forEach { key ->
            buildConfigField("String", key, "\"${secret(key)}\"")
        }
        // Google Maps SDK reads its key from a manifest meta-data placeholder.
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            // Sign with the real release keystore when configured. If it's absent we do NOT
            // silently fall back to the debug key — that produces a "release" build signed with a
            // throwaway, world-known key (anyone can re-sign/impersonate it, and it can't be
            // updated over a Play release). Local/CI machines without the keystore can opt in to
            // debug signing with -PallowDebugSigning; otherwise the release variant is left
            // UNSIGNED so it can never masquerade as a distributable build.
            val releaseSigning = signingConfigs.findByName("release")
            when {
                releaseSigning != null -> signingConfig = releaseSigning
                project.hasProperty("allowDebugSigning") -> {
                    signingConfig = signingConfigs.getByName("debug")
                    project.logger.warn(
                        "WARNING: release variant signed with the DEBUG key (-PallowDebugSigning). Do NOT distribute this build.",
                    )
                }
                else -> project.logger.warn(
                    "WARNING: no release keystore (keystore.properties missing); the release variant will be UNSIGNED. " +
                        "Provide the keystore, or pass -PallowDebugSigning for a local debug-signed build.",
                )
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Local persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Supabase — shared backend with the iOS app (Postgrest + Auth + Realtime). The BoM aligns
    // the module versions; ktor-client-okhttp is the HTTP engine supabase-kt runs on. This is now
    // the cross-device data + auth layer (trips sync, anonymous sign-in); Firestore was retired.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    // Async, images, background work
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)   // Task<T>.await() for Firebase
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Maps — Flight Tracker overlay. Renders a real Google Map when MAPS_API_KEY is set;
    // falls back to a Compose-drawn map otherwise.
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
