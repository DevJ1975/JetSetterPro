package com.jetsetter.pro.feature.localexperience

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, mock-first source of curated local activities. No network — the sample data is
 * realistic enough for a beta tester to browse, filter and sort.
 *
 * The one piece of genuinely mutable state — the tester's saved/bookmarked ids — is persisted
 * through [ModuleStateStore] as a Moshi-encoded JSON array so it survives app restarts, while the
 * shared Room DB stays free of throwaway mock entities.
 */
@Singleton
class LocalexperienceRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    suspend fun loadExperiences(): List<LocalExperiencesItem> = sampleExperiences

    /** Emits the persisted set of saved experience ids, re-emitting whenever it changes. */
    fun observeSavedIds(): Flow<Set<String>> =
        store.observe(SAVED_KEY).map { json ->
            json?.let { savedAdapter.fromJson(it) }?.toSet() ?: emptySet()
        }

    /** Persists the full saved-id set (the screen toggles one id at a time, then hands the set in). */
    suspend fun setSavedIds(ids: Set<String>) {
        store.save(SAVED_KEY, savedAdapter.toJson(ids.sorted()))
    }

    private companion object {
        const val SAVED_KEY = "local_experience_saved_ids"

        private val savedAdapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    }

    private val sampleExperiences: List<LocalExperiencesItem> = listOf(
        LocalExperiencesItem(
            id = "exp-1",
            title = "Sunrise Rooftop Coffee Tasting",
            category = LocalExperiencesCategory.FOOD_AND_DRINK,
            neighborhood = "Old Fourth Ward",
            durationLabel = "1.5 hrs",
            price = 38.0,
            rating = 4.9,
            reviewCount = 214,
            colorHex = 0xFFB5651D,
        ),
        LocalExperiencesItem(
            id = "exp-2",
            title = "Coastal Cliffside Hike & Picnic",
            category = LocalExperiencesCategory.OUTDOOR,
            neighborhood = "Marin Headlands",
            durationLabel = "4 hrs",
            price = 65.0,
            rating = 4.8,
            reviewCount = 318,
            colorHex = 0xFF2E8B57,
        ),
        LocalExperiencesItem(
            id = "exp-3",
            title = "After-Hours Museum & Art Walk",
            category = LocalExperiencesCategory.CULTURE,
            neighborhood = "Downtown Arts District",
            durationLabel = "2 hrs",
            price = 52.0,
            rating = 4.7,
            reviewCount = 142,
            colorHex = 0xFF6A5ACD,
        ),
        LocalExperiencesItem(
            id = "exp-4",
            title = "Speakeasy Cocktail Crawl",
            category = LocalExperiencesCategory.NIGHTLIFE,
            neighborhood = "West End",
            durationLabel = "3 hrs",
            price = 89.0,
            rating = 4.6,
            reviewCount = 276,
            colorHex = 0xFFC2185B,
        ),
        LocalExperiencesItem(
            id = "exp-5",
            title = "Hot Spring & Forest Spa Retreat",
            category = LocalExperiencesCategory.WELLNESS,
            neighborhood = "Cedar Valley",
            durationLabel = "Half day",
            price = 120.0,
            rating = 4.9,
            reviewCount = 188,
            colorHex = 0xFF00897B,
        ),
        LocalExperiencesItem(
            id = "exp-6",
            title = "Old Town Vespa Photo Tour",
            category = LocalExperiencesCategory.TOURS,
            neighborhood = "Historic Center",
            durationLabel = "2.5 hrs",
            price = 74.0,
            rating = 4.8,
            reviewCount = 401,
            colorHex = 0xFF3F6FB5,
        ),
    )
}
