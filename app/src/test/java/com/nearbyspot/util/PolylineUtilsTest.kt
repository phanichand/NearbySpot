package com.nearbyspot.util

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineUtilsTest {

    @Test
    fun `long route is capped at eight anchors while preserving route spread`() {
        val route = equatorialRoute(pointCount = 51)

        val anchors = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = 0,
            intervalMeters = 1_000.0,
            destinationExclusionMeters = 0.0,
        )

        assertEquals(8, anchors.size)
        assertTrue(anchors.zipWithNext().all { (first, second) ->
            first.routeIndex < second.routeIndex
        })
        assertTrue(anchors.first().routeIndex <= 1)
        assertTrue(anchors.last().routeIndex >= route.lastIndex - 2)
    }

    @Test
    fun `short route receives a stable midpoint anchor`() {
        val route = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.01),
        )

        val anchors = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = 0,
            intervalMeters = 2_000.0,
            destinationExclusionMeters = 0.0,
        )

        assertEquals(1, anchors.size)
        assertEquals(0, anchors.single().routeIndex)
        assertEquals(0.005, anchors.single().point.longitude, 0.000_001)
    }

    @Test
    fun `anchor indices refer to their segments in the original route`() {
        val route = equatorialRoute(pointCount = 8)

        val anchors = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = 0,
            intervalMeters = 1_500.0,
            destinationExclusionMeters = 0.0,
        )

        assertTrue(anchors.isNotEmpty())
        anchors.forEach { anchor ->
            val segmentStart = route[anchor.routeIndex].longitude
            val segmentEnd = route[anchor.routeIndex + 1].longitude
            assertTrue(anchor.point.longitude >= segmentStart)
            assertTrue(anchor.point.longitude <= segmentEnd)
        }
    }

    @Test
    fun `advancing current index only removes anchors from the stable full route plan`() {
        val route = equatorialRoute(pointCount = 31)
        val initial = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = 0,
            intervalMeters = 1_000.0,
            destinationExclusionMeters = 0.0,
        )
        val advancedIndex = initial[2].routeIndex + 1

        val advanced = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = advancedIndex,
            intervalMeters = 1_000.0,
            destinationExclusionMeters = 0.0,
        )

        assertEquals(
            initial.filter { it.routeIndex >= advancedIndex },
            advanced,
        )
    }

    @Test
    fun `destination exclusion is applied before returning anchors`() {
        val route = equatorialRoute(pointCount = 16)
        val exclusionMeters = 3_500.0

        val anchors = PolylineUtils.planStableRouteAnchors(
            points = route,
            currentRouteIndex = 0,
            intervalMeters = 1_000.0,
            destinationExclusionMeters = exclusionMeters,
        )

        assertTrue(anchors.isNotEmpty())
        assertTrue(anchors.all { anchor ->
            PolylineUtils.haversineDistance(anchor.point, route.last()) > exclusionMeters
        })
    }

    @Test
    fun `degenerate routes and completed route return no anchors`() {
        val point = LatLng(12.0, 77.0)

        assertTrue(plan(emptyList()).isEmpty())
        assertTrue(plan(listOf(point)).isEmpty())
        assertTrue(plan(listOf(point, point, point)).isEmpty())
        assertTrue(
            PolylineUtils.planStableRouteAnchors(
                points = listOf(point, LatLng(12.01, 77.01)),
                currentRouteIndex = 1,
                intervalMeters = 1_000.0,
                destinationExclusionMeters = 0.0,
            ).isEmpty(),
        )
    }

    private fun plan(points: List<LatLng>) = PolylineUtils.planStableRouteAnchors(
        points = points,
        currentRouteIndex = 0,
        intervalMeters = 1_000.0,
        destinationExclusionMeters = 0.0,
    )

    private fun equatorialRoute(pointCount: Int): List<LatLng> =
        List(pointCount) { index -> LatLng(0.0, index * 0.01) }
}
