package com.nearbyspot.presentation.route

import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePresentationLogicTest {

    @Test
    fun `default route settings have no active filter badge`() {
        assertEquals(0, activeRouteFilterCount(RouteFilterSettings()))
    }

    @Test
    fun `active filter badge counts each changed dimension`() {
        assertEquals(
            4,
            activeRouteFilterCount(
                RouteFilterSettings(
                    maxDetourMinutes = 30,
                    transportMode = TransportMode.WALK,
                    maxPriceLevel = 2,
                    openNowOnly = true,
                ),
            ),
        )
    }

    @Test
    fun `route stop fraction is clamped and handles an empty route`() {
        assertEquals(0f, routeStopFraction(5, 0))
        assertEquals(0f, routeStopFraction(-2, 10))
        assertEquals(0.5f, routeStopFraction(5, 10))
        assertEquals(1f, routeStopFraction(20, 10))
    }

    @Test
    fun `route stop zones separate early middle and destination areas`() {
        assertEquals(RouteStopZone.EARLY, routeStopZone(0.33f))
        assertEquals(RouteStopZone.MIDDLE, routeStopZone(0.34f))
        assertEquals(RouteStopZone.MIDDLE, routeStopZone(0.74f))
        assertEquals(RouteStopZone.DESTINATION, routeStopZone(0.75f))
    }
}
