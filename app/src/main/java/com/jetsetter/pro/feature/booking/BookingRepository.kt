package com.jetsetter.pro.feature.booking

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.remote.expedia.ExpediaService
import com.jetsetter.pro.core.data.remote.getOrNull
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the Hotel Booking screen. Live-when-configured (plan B7): [searchHotels] shops the demo
 * stay window through Expedia Rapid when the OAuth2 pair is present and maps priced properties
 * onto [HotelBookingItem]; unconfigured keys, a fetch failure, or an unpriceable payload all fall
 * back to the realistic in-memory sample data below — the screen never surfaces an error state
 * it can't act on. Lightweight, tester-mutable preferences (favorites / selection / sort)
 * persist via [ModuleStateStore] either way.
 */
@Singleton
class BookingRepository @Inject constructor(
    private val stateStore: ModuleStateStore,
    private val expedia: ExpediaService,
) {
    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(BookingPreferences::class.java)

    // The demo stay window — one source of truth for the header display AND the live query
    // dates, so the numbers on screen always describe what was actually shopped.
    private val checkInDate: LocalDate = LocalDate.of(Year.now().value, 7, 14)
    private val checkOutDate: LocalDate = checkInDate.plusDays(NIGHTS.toLong())

    /** The active search context shown in the header. `nights` drives every total on screen. */
    fun searchSummary(): HotelSearchSummary = HotelSearchSummary(
        destination = "Lisbon, Portugal",
        checkIn = checkInDate.format(HEADER_DATE),
        checkOut = checkOutDate.format(HEADER_DATE),
        nights = NIGHTS,
        guests = GUESTS,
    )

    /**
     * Live-first search (plan B7): when Expedia Rapid is configured, shop the demo stay window
     * for [LIVE_PROPERTY_IDS] and map every priced property onto the booking model; anything
     * short of at least one mappable result serves the mock list instead.
     */
    suspend fun searchHotels(): List<HotelBookingItem> {
        if (expedia.isConfigured) {
            val live = expedia.availability(
                checkin = checkInDate.toString(),
                checkout = checkOutDate.toString(),
                propertyIds = LIVE_PROPERTY_IDS,
                occupancy = GUESTS.toString(),
                currency = LIVE_CURRENCY,
            ).getOrNull()
                ?.mapNotNull { it.toHotelBookingItem(nights = NIGHTS, expectedCurrency = LIVE_CURRENCY) }
                ?.takeIf { it.isNotEmpty() }
            if (live != null) return live
        }
        return hotels
    }

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
