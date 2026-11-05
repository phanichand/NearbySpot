package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.util.PolylineUtils

/** Splits the drive ahead into a "next stop" section followed by roughly hourly sections. */
object SectionPlanner {
    const val HORIZON_SECONDS = 18_000
    const val SECTION_SECONDS = 3_600
    private const val NEXT_STOP_SECONDS = SectionRanker.MAX_REACH_MINUTES * 60
    private const val FALLBACK_SPEED_METERS_PER_SECOND = 15.0

    /**
     * Plans sections from [currentRouteIndex] to the end of the route or [horizonSeconds] ahead,
     * whichever comes first. A null or non-positive speed falls back to the route's own average.
     */
    fun plan(
        routeInfo: RouteInfo,
        currentRouteIndex: Int,
        speedMetersPerSecond: Double?,
        horizonSeconds: Int = HORIZON_SECONDS,
    ): List<RouteSection> {
        val points = routeInfo.decodedPoints
        if (points.size < 2) return emptyList()

        val current = currentRouteIndex.coerceIn(0, points.lastIndex)
        val cumulativeMeters = DoubleArray(points.size - current)
        for (offset in 1 until cumulativeMeters.size) {
            cumulativeMeters[offset] = cumulativeMeters[offset - 1] +
                PolylineUtils.haversineDistance(points[current + offset - 1], points[current + offset])
        }
        val speed = speedMetersPerSecond?.takeIf { it > 0.0 } ?: routeAverageSpeed(routeInfo)
        val remainingSeconds = (cumulativeMeters.last() / speed).toInt()
        val limitSeconds = minOf(remainingSeconds, horizonSeconds)

        fun routeIndexAt(seconds: Int): Int {
            if (seconds >= remainingSeconds) return points.lastIndex
            val offset = cumulativeMeters.indexOfFirst { it >= seconds * speed }
            return if (offset < 0) points.lastIndex else current + offset
        }

        val starts = listOf(0) + generateSequence(NEXT_STOP_SECONDS) { it + SECTION_SECONDS }
            .takeWhile { it < limitSeconds }
            .toList()
        return starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: limitSeconds
            RouteSection(
                index = index,
                startRouteIndex = routeIndexAt(start),
                endRouteIndex = routeIndexAt(end),
                aheadSeconds = start,
                status = SectionStatus.NOT_LOADED,
                picks = emptyList(),
            )
        }
    }

    /**
     * Folds every empty section after the first into the section before it, so the traveller never
     * sees an empty row mid-route. Section indexes are kept, so loads keyed by index still match.
     * The first section is kept because it explains why nothing is nearby.
     */
    fun mergeEmpty(sections: List<RouteSection>): List<RouteSection> {
        val merged = mutableListOf<RouteSection>()
        for (section in sections) {
            if (section.status == SectionStatus.NO_QUALIFYING_PLACE && merged.isNotEmpty()) {
                merged[merged.lastIndex] = merged.last().copy(endRouteIndex = section.endRouteIndex)
            } else {
                merged += section
            }
        }
        return merged
    }

    private fun routeAverageSpeed(routeInfo: RouteInfo): Double =
        if (routeInfo.durationSeconds > 0 && routeInfo.distanceMeters > 0) {
            routeInfo.distanceMeters.toDouble() / routeInfo.durationSeconds
        } else {
            FALLBACK_SPEED_METERS_PER_SECOND
        }
}
