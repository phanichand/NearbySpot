package com.nearbyspot.domain.usecase

import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionPlannerTest {

    /** A straight west-east line of points about 1.1 km apart, driven at 20 m/s. */
    private fun route(durationSeconds: Int): RouteInfo {
        val distanceMeters = durationSeconds * SPEED
        val points = (0..(distanceMeters / 1_113).toInt()).map { LatLng(0.0, it * 0.01) }
        return RouteInfo(
            encodedPolyline = "",
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            decodedPoints = points,
        )
    }

    private fun emptySection(index: Int, start: Int, end: Int, status: SectionStatus) = RouteSection(
        index = index,
        startRouteIndex = start,
        endRouteIndex = end,
        aheadSeconds = index * 3_600,
        status = status,
        picks = emptyList(),
    )

    @Test
    fun `a 3 hour route yields a 45 minute first section then hourly sections`() {
        val route = route(10_800)

        val sections = SectionPlanner.plan(route, currentRouteIndex = 0, speedMetersPerSecond = SPEED.toDouble())

        assertEquals(listOf(0, 2_700, 6_300, 9_900), sections.map { it.aheadSeconds })
        assertEquals(listOf(0, 1, 2, 3), sections.map { it.index })
        assertTrue(sections.all { it.status == SectionStatus.NOT_LOADED && it.picks.isEmpty() })
        assertTrue(sections.zipWithNext().all { (a, b) -> a.startRouteIndex < b.startRouteIndex })
        assertEquals(route.decodedPoints.lastIndex, sections.last().endRouteIndex)
    }

    @Test
    fun `a route with 30 minutes left yields exactly one section`() {
        val sections = SectionPlanner.plan(route(1_800), 0, SPEED.toDouble())

        assertEquals(1, sections.size)
        assertEquals(0, sections.single().aheadSeconds)
    }

    @Test
    fun `current position at the last point yields one section without crashing`() {
        val route = route(3_600)

        val sections = SectionPlanner.plan(route, route.decodedPoints.lastIndex, SPEED.toDouble())

        assertEquals(1, sections.size)
        assertEquals(route.decodedPoints.lastIndex, sections.single().endRouteIndex)
    }

    @Test
    fun `null or non positive speed falls back to the route average`() {
        val fromNull = SectionPlanner.plan(route(7_200), 0, null)
        val fromZero = SectionPlanner.plan(route(7_200), 0, 0.0)

        assertEquals(listOf(0, 2_700, 6_300), fromNull.map { it.aheadSeconds })
        assertEquals(fromNull, fromZero)
    }

    @Test
    fun `the horizon limits how many sections are planned`() {
        val sections = SectionPlanner.plan(route(39_600), 0, SPEED.toDouble())

        assertEquals(6, sections.size)
        assertEquals(17_100, sections.last().aheadSeconds)
    }

    @Test
    fun `a larger horizon plans sections further ahead`() {
        val sections = SectionPlanner.plan(
            route(39_600),
            0,
            SPEED.toDouble(),
            horizonSeconds = 39_600,
        )

        assertTrue(sections.size > 6)
    }

    @Test
    fun `an empty or single point route yields no sections`() {
        val empty = RouteInfo("", 0, 0, emptyList())

        assertTrue(SectionPlanner.plan(empty, 0, SPEED.toDouble()).isEmpty())
    }

    @Test
    fun `mergeEmpty folds an empty later section into its previous neighbour`() {
        val sections = listOf(
            emptySection(0, 0, 40, SectionStatus.LOADED),
            emptySection(1, 40, 90, SectionStatus.LOADED),
            emptySection(2, 90, 140, SectionStatus.NO_QUALIFYING_PLACE),
            emptySection(3, 140, 190, SectionStatus.LOADED),
        )

        val merged = SectionPlanner.mergeEmpty(sections)

        assertEquals(listOf(0, 1, 3), merged.map { it.index })
        assertEquals(140, merged[1].endRouteIndex)
    }

    @Test
    fun `mergeEmpty keeps an empty first section so it can explain itself`() {
        val sections = listOf(
            emptySection(0, 0, 40, SectionStatus.NO_QUALIFYING_PLACE),
            emptySection(1, 40, 90, SectionStatus.LOADED),
        )

        assertEquals(sections, SectionPlanner.mergeEmpty(sections))
    }

    private companion object {
        const val SPEED = 20
    }
}
