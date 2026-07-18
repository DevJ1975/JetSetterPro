package com.jetsetter.pro

import com.jetsetter.pro.feature.rentalcar.RentalCarsCompany
import com.jetsetter.pro.feature.rentalcar.RentalcarDeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the pure URL assembly of the rental-partner hand-off links (plan B7): per-brand base
 * URL + minimal itinerary params, ISO date formatting, and the partner-id param appearing
 * only when a configured id is supplied. Pure JVM — Intent construction itself is a thin
 * `ACTION_VIEW` wrapper exercised on-device, not here.
 */
class RentalcarDeepLinksTest {

    private val pickup: LocalDate = LocalDate.of(2026, 7, 4)
    private val dropoff: LocalDate = LocalDate.of(2026, 7, 9)

    private fun url(company: RentalCarsCompany, partnerId: String? = null): String =
        RentalcarDeepLinks.bookingUrl(
            company = company,
            pickupLocation = "LAS",
            pickupDate = pickup,
            returnDate = dropoff,
            partnerId = partnerId,
        )

    // ── Per-brand assembly ───────────────────────────────────────────────────

    @Test
    fun enterprise_assemblesReservationUrl() {
        assertEquals(
            "https://www.enterprise.com/en/reserve.html" +
                "?location=LAS&pickupDate=2026-07-04&dropoffDate=2026-07-09",
            url(RentalCarsCompany.ENTERPRISE),
        )
    }

    @Test
    fun national_assemblesReservationUrl() {
        assertEquals(
            "https://www.nationalcar.com/en/reserve.html" +
                "?location=LAS&pickupDate=2026-07-04&dropoffDate=2026-07-09",
            url(RentalCarsCompany.NATIONAL),
        )
    }

    @Test
    fun hertz_assemblesReservationUrl() {
        assertEquals(
            "https://www.hertz.com/rentacar/reservation/" +
                "?pickupLocation=LAS&pickupDate=2026-07-04&returnDate=2026-07-09",
            url(RentalCarsCompany.HERTZ),
        )
    }

    @Test
    fun everyBrand_isHttpsWebUrl() {
        RentalCarsCompany.values().forEach { company ->
            assertTrue(url(company).startsWith("https://www."))
        }
    }

    // ── Partner-id gating ────────────────────────────────────────────────────

    @Test
    fun partnerId_appendedWhenSupplied() {
        assertTrue(url(RentalCarsCompany.ENTERPRISE, partnerId = "JSP123").endsWith("&cid=JSP123"))
        assertTrue(url(RentalCarsCompany.NATIONAL, partnerId = "JSP123").endsWith("&cid=JSP123"))
        assertTrue(url(RentalCarsCompany.HERTZ, partnerId = "1234567").endsWith("&cdp=1234567"))
    }

    @Test
    fun unconfiguredPartner_leavesNoPartnerParam() {
        RentalCarsCompany.values().forEach { company ->
            val plain = url(company, partnerId = null)
            assertFalse(plain.contains("cid="))
            assertFalse(plain.contains("cdp="))
        }
    }

    // ── Formatting / encoding ────────────────────────────────────────────────

    @Test
    fun dates_formatAsIsoCalendarDates_zeroPadded() {
        val url = RentalcarDeepLinks.bookingUrl(
            company = RentalCarsCompany.ENTERPRISE,
            pickupLocation = "LAS",
            pickupDate = LocalDate.of(2026, 1, 2),
            returnDate = LocalDate.of(2026, 11, 30),
        )

        assertTrue(url.contains("pickupDate=2026-01-02"))
        assertTrue(url.contains("dropoffDate=2026-11-30"))
    }

    @Test
    fun singleDayRental_sameDatesAllowed() {
        val url = RentalcarDeepLinks.bookingUrl(
            company = RentalCarsCompany.HERTZ,
            pickupLocation = "LAS",
            pickupDate = pickup,
            returnDate = pickup,
        )

        assertTrue(url.contains("pickupDate=2026-07-04&returnDate=2026-07-04"))
    }

    @Test
    fun pickupLocation_isUrlEncoded() {
        val url = RentalcarDeepLinks.bookingUrl(
            company = RentalCarsCompany.ENTERPRISE,
            pickupLocation = "Las Vegas Airport",
            pickupDate = pickup,
            returnDate = dropoff,
        )

        assertTrue(url.contains("location=Las+Vegas+Airport"))
        assertFalse(url.contains(" "))
    }
}
