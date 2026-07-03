package com.jetsetter.pro

import com.jetsetter.pro.feature.departureoptimizer.ROUTE_WAYPOINTS
import com.jetsetter.pro.feature.departureoptimizer.routePointAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM unit tests over the in-app route interpolation — runs via `testDebugUnitTest`. */
class RouteMapTest {

    @Test
    fun endpointsMatchTheSeededRoute() {
        assertEquals(ROUTE_WAYPOINTS.first(), routePointAt(0f))
        assertEquals(ROUTE_WAYPOINTS.last(), routePointAt(1f))
    }

    @Test
    fun fractionIsClamped() {
        assertEquals(ROUTE_WAYPOINTS.first(), routePointAt(-0.5f))
        assertEquals(ROUTE_WAYPOINTS.last(), routePointAt(1.5f))
    }

    @Test
    fun midpointsLieWithinTheRouteBounds() {
        val minLat = ROUTE_WAYPOINTS.minOf { it.first }
        val maxLat = ROUTE_WAYPOINTS.maxOf { it.first }
        val minLng = ROUTE_WAYPOINTS.minOf { it.second }
        val maxLng = ROUTE_WAYPOINTS.maxOf { it.second }
        for (i in 1..9) {
            val (lat, lng) = routePointAt(i / 10f)
            assertTrue("lat $lat outside route bounds", lat in minLat..maxLat)
            assertTrue("lng $lng outside route bounds", lng in minLng..maxLng)
        }
    }

    @Test
    fun progressMovesMonotonicallySouthTowardTheAirport() {
        // The seeded drive heads consistently south (latitude strictly falls); longitude bends
        // slightly west on the final approach into the terminal, so only latitude is monotonic.
        var previous = routePointAt(0f)
        for (i in 1..10) {
            val current = routePointAt(i / 10f)
            assertTrue("latitude should not increase along the route", current.first <= previous.first + 1e-9)
            previous = current
        }
    }
}
