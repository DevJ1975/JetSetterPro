package com.jetsetter.pro

import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerEstimate
import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerRisk
import com.jetsetter.pro.feature.departureoptimizer.DepartureOptimizerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JVM unit tests over the Departure Optimizer's derived math — runs via `testDebugUnitTest`. */
class DepartureOptimizerEstimateTest {

    @Test
    fun leaveByIsDepartureMinusAllFactors() {
        val est = DepartureOptimizerEstimate(
            departureMinuteOfDay = 7 * 60,
            driveMinutes = 34,
            parkingBufferMinutes = 15,
            tsaWaitMinutes = 22,
            gateBufferMinutes = 30,
        )
        assertEquals(101, est.totalLeadMinutes)
        assertEquals(7 * 60 - 101, est.leaveByMinuteOfDay) // 5:19 AM
    }

    @Test
    fun statusBucketsFollowSlack() {
        fun withSlack(slack: Int): DepartureOptimizerEstimate {
            val base = DepartureOptimizerEstimate()
            return base.copy(nowMinuteOfDay = base.leaveByMinuteOfDay - slack)
        }
        assertEquals(DepartureOptimizerStatus.ON_TRACK, withSlack(20).status)
        assertEquals(DepartureOptimizerStatus.TIGHT, withSlack(19).status)
        assertEquals(DepartureOptimizerStatus.TIGHT, withSlack(5).status)
        assertEquals(DepartureOptimizerStatus.LEAVE_NOW, withSlack(4).status)
        assertEquals(DepartureOptimizerStatus.LEAVE_NOW, withSlack(-10).status)
    }

    @Test
    fun weatherSummaryCombinesLabelAndTemperature() {
        val est = DepartureOptimizerEstimate(
            weatherLabel = "Gusty winds",
            weatherTempF = 81,
            weatherRisk = DepartureOptimizerRisk.MODERATE,
        )
        assertEquals("Gusty winds · 81°F", est.weatherSummary)
    }
}
