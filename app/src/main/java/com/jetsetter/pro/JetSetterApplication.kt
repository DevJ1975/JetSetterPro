package com.jetsetter.pro

import android.app.Application
import com.jetsetter.pro.core.ai.NanoModelManager
import com.jetsetter.pro.core.di.ApplicationScope
import com.jetsetter.pro.core.kb.KbSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JetSetterApplication : Application() {

    @Inject lateinit var kbSeeder: KbSeeder
    @Inject lateinit var nanoModelManager: NanoModelManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Seed IRIS's on-device RAG knowledge base once per KB version. Fire-and-forget on the
        // application IO scope so startup never blocks; failures leave IRIS un-grounded, not broken.
        appScope.launch { kbSeeder.seedIfNeeded() }
        // Warm up the on-device Gemini Nano model if it's already present (never auto-downloads).
        appScope.launch { nanoModelManager.ensureReady() }
    }
}
