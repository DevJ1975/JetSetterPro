package com.jetsetter.pro.feature.booking

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the Hotel Booking screen with realistic in-memory sample data plus lightweight,
 * tester-mutable preferences (favorites / selection / sort) persisted via [ModuleStateStore].
 *
 * Mock-first: there is no network, OTA integration, or remote persistence here — a real build
 * would swap [searchHotels] for a hotel search API (e.g. Booking.com / Expedia). The shape (a
 * summary plus a list of results) is kept stable so the live source can drop in later.
 */
@Singleton
class BookingRepository @Inject constructor(
    private val stateStore: ModuleStateStore,
) {
    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(BookingPreferences::class.java)

    /** The active search context shown in the header. `nights` drives every total on screen. */
    fun searchSummary(): HotelSearchSummary = HotelSearchSummary(
        destination = "Lisbon, Portugal",
        checkIn = "Jul 14",
        checkOut = "Jul 17",
        nights = 3,
        guests = 2,
    )

    /** Returns the mock search results. `suspend` so the live API call drops in unchanged. */
    suspend fun searchHotels(): List<HotelBookingItem> = hotels

    /** Loads persisted booking preferences, falling back to defaults on first run / bad data. */
    suspend fun loadPreferences(): BookingPreferences =
        stateStore.read(PREFS_KEY)
            ?.let { runCatching { prefsAdapter.fromJson(it) }.getOrNull() }
            ?: BookingPreferences()

    /** Persists booking preferences as Moshi JSON so they survive an app restart. */
    suspend fun savePreferences(prefs: BookingPreferences) {
        stateStore.save(PREFS_KEY, prefsAdapter.toJson(prefs))
    }

    // Stable ids (not random UUIDs) so persisted favorites/selection survive a restart.
    private val hotels: List<HotelBookingItem> = listOf(
        HotelBookingItem(
            id = "tivoli-avenida",
            name = "Tivoli Avenida Liberdade",
            starRating = 5,
            guestRating = 9.2,
            neighborhood = "Avenida da Liberdade",
            pricePerNight = 412.0,
            photoColorHex = 0xFF4C6FBF,
            amenities = listOf("Pool", "Spa", "Wifi", "Bar"),
            freeCancellation = true,
        ),
        HotelBookingItem(
            id = "memmo-alfama",
            name = "Memmo Alfama",
            starRating = 4,
            guestRating = 9.0,
            neighborhood = "Alfama",
            pricePerNight = 268.0,
            photoColorHex = 0xFFB5793A,
            amenities = listOf("Rooftop", "Breakfast", "Wifi"),
            freeCancellation = true,
        ),
        HotelBookingItem(
            id = "lumiares",
            name = "The Lumiares Hotel & Spa",
            starRating = 5,
            guestRating = 9.4,
            neighborhood = "Bairro Alto",
            pricePerNight = 355.0,
            photoColorHex = 0xFF3F8F73,
            amenities = listOf("Spa", "Bar", "Wifi"),
        ),
        HotelBookingItem(
            id = "santa-justa",
            name = "Hotel Santa Justa",
            starRating = 3,
            guestRating = 8.4,
            neighborhood = "Baixa",
            pricePerNight = 159.0,
            photoColorHex = 0xFF8E6FB0,
            amenities = listOf("Wifi", "Gym"),
            freeCancellation = true,
        ),
        HotelBookingItem(
            id = "marriott",
            name = "Lisbon Marriott Hotel",
            starRating = 4,
            guestRating = 8.7,
            neighborhood = "Sete Rios",
            pricePerNight = 224.0,
            photoColorHex = 0xFFC25C6A,
            amenities = listOf("Pool", "Parking", "Gym"),
        ),
    )

    private companion object {
        const val PREFS_KEY = "booking_prefs"
    }
}
