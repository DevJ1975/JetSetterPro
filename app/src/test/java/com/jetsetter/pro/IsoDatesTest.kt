package com.jetsetter.pro

import com.jetsetter.pro.core.util.IsoDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pins the tolerant ISO-8601 decoding contract in [IsoDates] (spec §2.5): every common wire form
 * parses, zone-less forms resolve as UTC, and garbage returns null instead of throwing.
 */
class IsoDatesTest {

    // ---- parseDateTime -------------------------------------------------------------------

    @Test
    fun parseDateTime_instantForm() {
        assertEquals(
            Instant.parse("2026-07-14T10:00:00Z"),
            IsoDates.parseDateTime("2026-07-14T10:00:00Z"),
        )
    }

    @Test
    fun parseDateTime_offsetForm_normalizesToInstant() {
        // 10:00 at +02:00 is 08:00 UTC.
        assertEquals(
            Instant.parse("2026-07-14T08:00:00Z"),
            IsoDates.parseDateTime("2026-07-14T10:00:00+02:00"),
        )
    }

    @Test
    fun parseDateTime_localDateTimeForm_assumesUtc() {
        assertEquals(
            Instant.parse("2026-07-14T10:00:00Z"),
            IsoDates.parseDateTime("2026-07-14T10:00:00"),
        )
    }

    @Test
    fun parseDateTime_dateOnlyForm_isStartOfDayUtc() {
        assertEquals(
            Instant.parse("2026-07-14T00:00:00Z"),
            IsoDates.parseDateTime("2026-07-14"),
        )
    }

    @Test
    fun parseDateTime_garbageIsNull() {
        assertNull(IsoDates.parseDateTime("not-a-date"))
        assertNull(IsoDates.parseDateTime("2026-99-99"))
        assertNull(IsoDates.parseDateTime("07/14/2026"))
        assertNull(IsoDates.parseDateTime(""))
        assertNull(IsoDates.parseDateTime("   "))
        assertNull(IsoDates.parseDateTime(null))
    }

    @Test
    fun parseDateTime_trimsWhitespace() {
        assertEquals(
            Instant.parse("2026-07-14T10:00:00Z"),
            IsoDates.parseDateTime("  2026-07-14T10:00:00Z  "),
        )
    }

    // ---- parseDate -----------------------------------------------------------------------

    @Test
    fun parseDate_dateOnlyForm() {
        assertEquals(LocalDate.parse("2026-07-14"), IsoDates.parseDate("2026-07-14"))
    }

    @Test
    fun parseDate_timestampFormsCollapseToUtcCalendarDay() {
        assertEquals(LocalDate.parse("2026-07-14"), IsoDates.parseDate("2026-07-14T23:59:59Z"))
        assertEquals(LocalDate.parse("2026-07-14"), IsoDates.parseDate("2026-07-14T10:00:00+02:00"))
        assertEquals(LocalDate.parse("2026-07-14"), IsoDates.parseDate("2026-07-14T10:00:00"))
    }

    @Test
    fun parseDate_garbageIsNull() {
        assertNull(IsoDates.parseDate("garbage"))
        assertNull(IsoDates.parseDate("2026-13-01"))
        assertNull(IsoDates.parseDate(""))
        assertNull(IsoDates.parseDate(null))
    }
}
