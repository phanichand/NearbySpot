package com.nearbyspot.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSearchDiagnosticsTest {

    @Test
    fun `finish emits one structured aggregate summary`() = runTest {
        var now = 0L
        val summaries = mutableListOf<String>()
        val diagnostics = RouteSearchDiagnostics(
            clockNanos = { now },
            emit = summaries::add,
        )

        diagnostics.measure(RouteSearchDiagnostics.Stage.SEARCH_DISCOVERY) {
            now += 12_000_000L
        }
        diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_ATTEMPTED, 3)
        diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_SUCCEEDED, 2)
        diagnostics.increment(RouteSearchDiagnostics.Counter.CANDIDATES_DISCOVERED, 17)
        now += 8_000_000L

        diagnostics.finish(RouteSearchDiagnostics.Outcome.SUCCESS)
        diagnostics.finish(RouteSearchDiagnostics.Outcome.FAILURE)

        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertTrue(summary.startsWith("route_search_summary status=success total_ms=20"))
        assertTrue(summary.contains("search_discovery:12"))
        assertTrue(summary.contains("waypoints_attempted:3"))
        assertTrue(summary.contains("waypoints_succeeded:2"))
        assertTrue(summary.contains("candidates_discovered:17"))
    }

    @Test
    fun `summary contains only fixed diagnostic fields`() {
        var now = 0L
        val summaries = mutableListOf<String>()
        val diagnostics = RouteSearchDiagnostics(
            clockNanos = { now },
            emit = summaries::add,
        )
        diagnostics.increment(RouteSearchDiagnostics.Counter.FINAL_RESULTS, 4)
        now = 5_000_000L

        diagnostics.finish(RouteSearchDiagnostics.Outcome.CANCELLATION)

        val summary = summaries.single()
        assertEquals(
            "route_search_summary status=cancellation total_ms=5 " +
                "stages_ms={waypoint_planning:0,search_discovery:0,filtering:0," +
                "travel_matrices:0} " +
                "counts={waypoints_attempted:0,waypoints_succeeded:0," +
                "candidates_discovered:0,candidates_deduped:0,candidates_filtered:0," +
                "candidates_budgeted:0,reach_verified:0,detour_verified:0,final_results:4," +
                "nearby_repository_calls:0,travel_time_repository_calls:0," +
                "matrix_elements_requested:0,partial_failures:0}",
            summary,
        )
        assertFalse(summary.contains("exception"))
        assertFalse(summary.contains("latitude"))
        assertFalse(summary.contains("longitude"))
    }
}
