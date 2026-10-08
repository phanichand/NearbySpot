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
}
