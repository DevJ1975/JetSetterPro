package com.jetsetter.pro.feature.translator

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, mock-first backing store for the Translator feature. No network: translations are
 * resolved against a small offline phrasebook, with a canned demo fallback so the screen always
 * responds. The tester-mutable "Recent" list is persisted via [ModuleStateStore] (Moshi JSON) so
 * it survives an app restart, mirroring the project's per-feature persistence idiom.
 */
@Singleton
class TranslatorRepository @Inject constructor(
    private val stateStore: ModuleStateStore,
) {

    private val recentsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<TranslatorRecentEntry>>(
            Types.newParameterizedType(List::class.java, TranslatorRecentEntry::class.java),
        )

    /** Languages offered in the picker. Mock build ships the EN→JA pair. */
    val languages: List<TranslatorLanguage> = listOf(
        TranslatorLanguage.English,
        TranslatorLanguage.Japanese,
    )

    private val phraseData: List<TranslatorPhrase> = listOf(
        TranslatorPhrase(
            id = "greet-hello",
            category = "Greetings",
            source = "Hello",
            target = "こんにちは",
            pronunciation = "Konnichiwa",
        ),
        TranslatorPhrase(
            id = "greet-thanks",
            category = "Greetings",
            source = "Thank you",
            target = "ありがとうございます",
            pronunciation = "Arigatō gozaimasu",
        ),
        TranslatorPhrase(
            id = "basics-excuse",
            category = "Basics",
            source = "Excuse me",
            target = "すみません",
            pronunciation = "Sumimasen",
        ),
        TranslatorPhrase(
            id = "basics-english",
            category = "Basics",
            source = "Do you speak English?",
            target = "英語を話せますか？",
            pronunciation = "Eigo o hanasemasu ka?",
        ),
        TranslatorPhrase(
            id = "directions-station",
            category = "Directions",
            source = "Where is the station?",
            target = "駅はどこですか？",
            pronunciation = "Eki wa doko desu ka?",
        ),
        TranslatorPhrase(
            id = "shopping-price",
            category = "Shopping",
            source = "How much is this?",
            target = "これはいくらですか？",
            pronunciation = "Kore wa ikura desu ka?",
        ),
        TranslatorPhrase(
            id = "dining-menu",
            category = "Dining",
            source = "May I see the menu?",
            target = "メニューをお願いします",
            pronunciation = "Menyū o onegaishimasu",
        ),
        TranslatorPhrase(
            id = "dining-check",
            category = "Dining",
            source = "Check, please",
            target = "お会計をお願いします",
            pronunciation = "O-kaikei o onegaishimasu",
        ),
        TranslatorPhrase(
            id = "help-assist",
            category = "Emergencies",
            source = "Can you help me?",
            target = "手伝ってもらえますか？",
            pronunciation = "Tetsudatte moraemasu ka?",
        ),
        TranslatorPhrase(
            id = "help-lost",
            category = "Emergencies",
            source = "I'm lost",
            target = "道に迷いました",
            pronunciation = "Michi ni mayoimashita",
        ),
    )

    /** Common travel phrases shown beneath the translation box. */
    suspend fun phrases(): List<TranslatorPhrase> = phraseData

    /** Loads the persisted recent translations, tolerating first-run / corrupt data. */
    suspend fun loadRecents(): List<TranslatorRecentEntry> =
        stateStore.read(RECENTS_KEY)
            ?.let { runCatching { recentsAdapter.fromJson(it) }.getOrNull() }
            ?: emptyList()

    /** Persists the recent translations as Moshi JSON so they survive an app restart. */
    suspend fun saveRecents(entries: List<TranslatorRecentEntry>) {
        stateStore.save(RECENTS_KEY, recentsAdapter.toJson(entries))
    }

    /**
     * Mock translation. Resolves an exact match from the offline phrasebook when possible,
     * otherwise returns a canned demo result. The short delay simulates async work so the
     * in-progress state is observable. [TranslatorResult.matched] reports which path was taken.
     */
    suspend fun translate(
        text: String,
        from: TranslatorLanguage,
        to: TranslatorLanguage,
    ): TranslatorResult {
        delay(450)
        // Reverse direction (e.g. JA→EN after Swap): match the phrase's translated side and
        // return its English source. Forward (EN→JA) keeps the original source→target behavior.
        val reverse = from.code != TranslatorLanguage.English.code
        val key = normalize(text)
        val match = phraseData.firstOrNull {
            val candidate = if (reverse) it.target else it.source
            normalize(candidate) == key
        }
        return if (match != null) {
            TranslatorResult(
                sourceText = text.trim(),
                translatedText = if (reverse) match.source else match.target,
                pronunciation = if (reverse) "" else match.pronunciation,
                note = "Matched a saved ${from.name} → ${to.name} travel phrase.",
                matched = true,
            )
        } else {
            TranslatorResult(
                sourceText = text.trim(),
                translatedText = if (reverse) "This is an offline demo translation" else "これはオフラインのデモ翻訳です",
                pronunciation = if (reverse) "" else "Kore wa ofurain no demo hon'yaku desu",
                note = "Offline demo result — connect a translation engine for live ${to.name}.",
                matched = false,
            )
        }
    }

    private fun normalize(text: String): String =
        text.trim().trimEnd('?', '.', '!', '？', '。', '！').lowercase()

    private companion object {
        const val RECENTS_KEY = "translator_recents"
    }
}
