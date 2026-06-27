package com.jetsetter.pro.core.di

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Firebase singletons. FirebaseApp auto-initializes via the google-services
 * plugin (FirebaseInitProvider) before the Hilt graph is built, so no manual init is needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = Firebase.firestore

    /**
     * The IRIS assistant model. Uses Firebase AI Logic's Gemini Developer API backend
     * ([GenerativeBackend.googleAI]) — free tier, no Blaze plan required. Requires
     * "Firebase AI Logic" to be enabled for the project; until then calls fail and
     * [com.jetsetter.pro.core.data.repository.IrisRepository] falls back to demo replies.
     */
    @Provides
    @Singleton
    fun provideIrisModel(): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = IRIS_MODEL,
                systemInstruction = content { text(IRIS_SYSTEM_PROMPT) },
            )

    private const val IRIS_MODEL = "gemini-2.5-flash"

    private val IRIS_SYSTEM_PROMPT = """
        You are IRIS, JetSetter Pro's executive travel concierge. Be concise, proactive,
        and practical. Help with flights, itineraries, packing, expenses, and travel
        logistics. Prefer specific, actionable answers.
    """.trimIndent()
}
