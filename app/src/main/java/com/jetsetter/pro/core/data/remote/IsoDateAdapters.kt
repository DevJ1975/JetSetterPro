package com.jetsetter.pro.core.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Moshi adapters for the §2.1 wire-date convention: ISO-8601 strings ↔ `java.time` types.
 *
 * [Instant] accepts both UTC instants (`2026-07-17T12:30:00Z`) and offset date-times
 * (`2026-07-17T12:30:00+02:00`, normalized to UTC); [LocalDate] is strict `yyyy-MM-dd`.
 * Unparsable values throw [JsonDataException] so callers surface them as
 * [ApiError.DecodingFailed] via [toApiError]. Registered in `NetworkModule.provideMoshi`.
 */
class IsoDateAdapters {

    @FromJson
    fun instantFromJson(value: String): Instant = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(value).toInstant()
        } catch (e: DateTimeParseException) {
            throw JsonDataException("Not an ISO-8601 instant: '$value'", e)
        }
    }

    @ToJson
    fun instantToJson(value: Instant): String = value.toString()

    @FromJson
    fun localDateFromJson(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw JsonDataException("Not an ISO-8601 date (yyyy-MM-dd): '$value'", e)
    }

    @ToJson
    fun localDateToJson(value: LocalDate): String = value.toString()
}
