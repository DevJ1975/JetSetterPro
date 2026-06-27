package com.jetsetter.pro.feature.rentalcar

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first source of rental-car offers. Holds realistic in-memory sample data so the
 * Rental Cars tab is fully interactive without any network. A live integration (GDS /
 * aggregator) would replace [offers] but keep this same surface.
 *
 * Offers expose only a daily rate; trip totals are computed from the user's chosen length
 * via [RentalCarsOffer.totalFor], so the numbers can never contradict the inputs. Stable
 * ids let a persisted selection survive a restart.
 */
@Singleton
class RentalcarRepository @Inject constructor(
    private val store: ModuleStateStore,
) {

    private val offers: List<RentalCarsOffer> = listOf(
        RentalCarsOffer(
            id = "hertz-suv",
            company = RentalCarsCompany.HERTZ,
            carClass = "Full-size SUV",
            exampleModel = "Chevrolet Tahoe or similar",
            transmission = RentalCarsTransmission.AUTOMATIC,
            seats = 7,
            pricePerDay = 94.50,
        ),
        RentalCarsOffer(
            id = "enterprise-sedan",
            company = RentalCarsCompany.ENTERPRISE,
            carClass = "Midsize Sedan",
            exampleModel = "Toyota Camry or similar",
            transmission = RentalCarsTransmission.AUTOMATIC,
            seats = 5,
            pricePerDay = 52.75,
        ),
        RentalCarsOffer(
            id = "national-luxury",
            company = RentalCarsCompany.NATIONAL,
            carClass = "Luxury",
            exampleModel = "BMW 5 Series or similar",
            transmission = RentalCarsTransmission.AUTOMATIC,
            seats = 5,
            pricePerDay = 138.00,
        ),
        RentalCarsOffer(
            id = "enterprise-economy",
            company = RentalCarsCompany.ENTERPRISE,
            carClass = "Economy",
            exampleModel = "Kia Rio or similar",
            transmission = RentalCarsTransmission.MANUAL,
            seats = 5,
            pricePerDay = 38.20,
            unlimitedMileage = false,
        ),
        RentalCarsOffer(
            id = "hertz-electric",
            company = RentalCarsCompany.HERTZ,
            carClass = "Electric",
            exampleModel = "Tesla Model 3 or similar",
            transmission = RentalCarsTransmission.AUTOMATIC,
            seats = 5,
            pricePerDay = 81.00,
        ),
    )

    /** Smallest / largest rental lengths the day editor accepts. */
    val minRentalDays: Int = 1
    val maxRentalDays: Int = 60

    /**
     * Emits the available rental-car offers after a short simulated search so the loading
     * state is real rather than instant. Mock-first: no network is touched.
     */
    fun observeOffers(): Flow<List<RentalCarsOffer>> = flow {
        delay(450)
        emit(offers)
    }

    // --- Persistence (Moshi, mirroring the Converters idiom) -------------------------------

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val prefsAdapter = moshi.adapter(RentalcarPersistedState::class.java)

    /** Restores tester-entered state; falls back to defaults on first run or bad JSON. */
    suspend fun loadPrefs(): RentalcarPersistedState =
        store.read(PREFS_KEY)
            ?.let { runCatching { prefsAdapter.fromJson(it) }.getOrNull() }
            ?: RentalcarPersistedState()

    /** Persists tester-entered state so it survives an app restart. */
    suspend fun savePrefs(prefs: RentalcarPersistedState) {
        store.save(PREFS_KEY, prefsAdapter.toJson(prefs))
    }

    private companion object {
        const val PREFS_KEY = "rentalcar_prefs"
    }
}
