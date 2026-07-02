package com.jetsetter.pro

import com.jetsetter.pro.feature.checkin.PersistedCheckIn
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the persisted boarding-pass format: records written before the seat-map feature carry no
 * `seat` field and must keep deserializing (defaulting to blank, which means "keep the flight's
 * default seat" — see CheckinViewModel.applyRecord).
 */
class PersistedCheckInCompatTest {

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<PersistedCheckIn>>(
            Types.newParameterizedType(List::class.java, PersistedCheckIn::class.java),
        )

    @Test
    fun preSeatMapRecordsStillParse() {
        val legacyJson =
            """[{"flightId":"dl1423","zone":1,"position":7,"cabinLabel":"First · SkyPriority","checkedInAtMillis":123}]"""
        val records = adapter.fromJson(legacyJson)!!
        assertEquals(1, records.size)
        assertEquals("", records.first().seat)
        assertEquals("dl1423", records.first().flightId)
    }

    @Test
    fun seatRoundTrips() {
        val records = listOf(
            PersistedCheckIn(
                flightId = "dl1423",
                zone = 1,
                position = 7,
                cabinLabel = "First · SkyPriority",
                checkedInAtMillis = 123L,
                seat = "2C",
            ),
        )
        assertEquals(records, adapter.fromJson(adapter.toJson(records)))
    }
}
