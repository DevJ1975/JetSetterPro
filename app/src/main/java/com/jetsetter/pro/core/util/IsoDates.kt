package com.jetsetter.pro.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Tolerant ISO-8601 date decoding (spec §2.5) — the Android counterpart of the iOS decoder.
 *
 * Backend payloads and locally stored strings are inconsistent about precision and zone:
 * `2026-07-14T10:00:00Z`, `2026-07-14T10:00:00+02:00`, `2026-07-14T10:00:00`, `2026-07-14`
 * all appear in the wild. Each parser tries the strictest form first and degrades gracefully:
 * Instant → OffsetDateTime → LocalDateTime → LocalDate. Garbage returns `null` — never throws.
 *
 * Zone-less forms are interpreted as UTC so results are deterministic across devices.
 */
object IsoDates {

    /** Parses [raw] into an [Instant], or `null` when it isn't any recognizable ISO-8601 form. */
    fun parseDateTime(raw: String?): Instant? {
        val s = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        runCatching { return Instant.parse(s) }
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }
        runCatching { return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC) }
        return null
    }

    /** Parses [raw] into a [LocalDate] (UTC calendar day for timestamped forms), or `null`. */
    fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        runCatching { return Instant.parse(s).atZone(ZoneOffset.UTC).toLocalDate() }
        runCatching { return OffsetDateTime.parse(s).toLocalDate() }
        runCatching { return LocalDateTime.parse(s).toLocalDate() }
        runCatching { return LocalDate.parse(s) }
        return null
    }
}
