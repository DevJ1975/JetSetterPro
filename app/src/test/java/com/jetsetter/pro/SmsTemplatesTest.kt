package com.jetsetter.pro

import com.jetsetter.pro.core.util.SmsTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Loved Ones SMS bodies (spec §3.3) verbatim — the emoji, em dash, and punctuation are
 * part of the cross-platform contract with iOS. Pure-JVM (no Android deps).
 */
class SmsTemplatesTest {

    @Test
    fun takeoff_substitutesFlightIdent_verbatim() {
        assertEquals(
            "✈️ Wheels up on DL1423 — I'll text you when I land.",
            SmsTemplates.takeoff("DL1423"),
        )
    }

    @Test
    fun landing_substitutesCity_verbatim() {
        assertEquals(
            "🛬 Just landed safely in Tokyo. Talk soon!",
            SmsTemplates.landing("Tokyo"),
        )
    }

    @Test
    fun takeoff_startsWithAirplaneEmojiIncludingVariationSelector() {
        // ✈ (U+2708) must carry the emoji-presentation selector U+FE0F, matching iOS exactly.
        val body = SmsTemplates.takeoff("AA88")
        assertEquals(0x2708, body[0].code)
        assertEquals(0xFE0F, body[1].code)
        assertEquals("✈️ Wheels up on AA88 — I'll text you when I land.", body)
    }

    @Test
    fun otherIdentsAndCities_flowThroughUnchanged() {
        assertEquals(
            "🛬 Just landed safely in São Paulo. Talk soon!",
            SmsTemplates.landing("São Paulo"),
        )
    }
}
