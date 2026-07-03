package com.jetsetter.pro

import com.jetsetter.pro.core.ai.IrisPersona
import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerEstimate
import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerRisk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM unit tests over the IRIS demo persona — runs via `testDebugUnitTest`. */
class IrisPersonaTest {

    private val rolled = DepartureOptimizerEstimate(
        driveMinutes = 48,
        tsaWaitMinutes = 40,
        trafficRisk = DepartureOptimizerRisk.HIGH,
        weatherLabel = "Thunderstorms nearby",
        weatherTempF = 66,
        weatherRisk = DepartureOptimizerRisk.HIGH,
    )

    @Test
    fun leaveReplyReadsTheLiveEstimate() {
        val reply = IrisPersona.demoResponse("When should I leave?", rolled)
        assertTrue(reply, reply.contains("48-minute drive"))
        assertTrue(reply, reply.contains("high traffic"))
        assertTrue(reply, reply.contains("TSA is running 40 minutes"))
        assertTrue(reply, reply.contains("Thunderstorms nearby · 66°F"))
        // 7:00 AM departure − (48 + 15 + 40 + 30) = 4:47 AM leave-by.
        assertTrue(reply, reply.contains("4:47 AM"))
    }

    @Test
    fun weatherReplyReadsTheLiveEstimate() {
        val reply = IrisPersona.demoResponse("How's the weather?", rolled)
        assertTrue(reply, reply.contains("Thunderstorms nearby · 66°F"))
    }

    @Test
    fun withoutAnEstimateTheReplyStatesTheSeededDefaults() {
        val reply = IrisPersona.demoResponse("any traffic?")
        assertTrue(reply, reply.contains("5:19 AM"))
        assertTrue(reply, reply.contains("34-minute drive"))
        assertTrue(reply, reply.contains("Clear skies · 74°F"))
    }

    @Test
    fun repliesNeverLeakSetupHints() {
        listOf("", "hello", "any delays?", "help me pack", "expenses", "seat", "when should I leave", "weather")
            .forEach { prompt ->
                val reply = IrisPersona.demoResponse(prompt, rolled)
                assertFalse("reply for \"$prompt\" leaks setup copy: $reply", reply.contains("API key", ignoreCase = true))
            }
    }
}
