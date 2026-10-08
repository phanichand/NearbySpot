package com.nearbyspot.util

import com.google.android.gms.maps.model.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PolylineUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_WAYPOINTS = 10

    data class RouteAnchor(
        val point: LatLng,
        val routeIndex: Int,
    )

    /**
     * Plans search anchors against the complete route so an anchor's location and cache key do not
     * change as [currentRouteIndex] advances. The full-route plan is destination-filtered and capped
     * before past anchors are removed; resampling or recapping the remaining suffix would shift the
     * surviving anchors between refreshes.
     *
     * [RouteAnchor.routeIndex] is the global index of the segment containing the sampled point. An
     * anchor remains ahead while the user is at that segment's start and is removed after the route
     * index advances beyond it.
     */
    fun planStableRouteAnchors(
        points: List<LatLng>,
        currentRouteIndex: Int,
        intervalMeters: Double,
        destinationExclusionMeters: Double,
        maxAnchors: Int = 8,
    ): List<RouteAnchor> {
        require(intervalMeters > 0.0 && intervalMeters.isFinite()) {
            "intervalMeters must be finite and greater than zero"
        }
        require(destinationExclusionMeters >= 0.0 && destinationExclusionMeters.isFinite()) {
            "destinationExclusionMeters must be finite and non-negative"
        }
        require(maxAnchors > 0) { "maxAnchors must be greater than zero" }

        if (points.size < 2 || currentRouteIndex >= points.lastIndex) return emptyList()

        val destination = points.last()
        val stableFullRoutePlan = sampleFullRouteAnchors(points, intervalMeters)
            .filter { anchor ->
                haversineDistance(anchor.point, destination) > destinationExclusionMeters
            }
            .evenlyCapped(maxAnchors)

        val firstAheadIndex = currentRouteIndex.coerceAtLeast(0)
        return stableFullRoutePlan.filter { it.routeIndex >= firstAheadIndex }
    }

    /**
     * Walks the polyline, emitting a sample point every [intervalMeters].
     * Always includes the midpoint and caps at [MAX_WAYPOINTS].
     */
    fun sampleWaypoints(
        points: List<LatLng>,
        intervalMeters: Double = 2000.0,
    ): List<LatLng> {
        if (points.size < 2) return points.toList()

        val totalDistance = totalDistance(points)
        if (totalDistance <= 0) return listOf(points.first())

        val effectiveInterval = if (totalDistance / intervalMeters > MAX_WAYPOINTS) {
            totalDistance / MAX_WAYPOINTS
        } else {
            intervalMeters
        }

        val samples = mutableListOf<LatLng>()
        val midDistance = totalDistance / 2.0
        var midAdded = false

        var accumulated = 0.0
        var nextSampleAt = effectiveInterval

        for (i in 0 until points.size - 1) {
            val segDist = haversineDistance(points[i], points[i + 1])
            val segStart = accumulated
            accumulated += segDist

            while (nextSampleAt <= accumulated && samples.size < MAX_WAYPOINTS) {
                val fraction = (nextSampleAt - segStart) / segDist
                samples.add(interpolate(points[i], points[i + 1], fraction))
                nextSampleAt += effectiveInterval
            }

            if (!midAdded && accumulated >= midDistance) {
                val fraction = (midDistance - segStart) / segDist
                val midPoint = interpolate(points[i], points[i + 1], fraction.coerceIn(0.0, 1.0))
                if (samples.none { haversineDistance(it, midPoint) < effectiveInterval * 0.3 }) {
                    samples.add(midPoint)
                }
                midAdded = true
            }
        }

        return samples.take(MAX_WAYPOINTS)
    }

    /**
     * Finds the polyline index closest to the user's current GPS position.
     * Used to determine how far along the route the user has traveled.
     */
    fun findPositionOnRoute(points: List<LatLng>, userLatLng: LatLng): Int {
        return findNearestPointIndex(points, userLatLng)
    }

    /**
     * Returns the portion of the polyline ahead of [fromIndex] (inclusive).
     */
    fun getAheadPoints(points: List<LatLng>, fromIndex: Int): List<LatLng> {
        if (fromIndex >= points.size) return emptyList()
        return points.subList(fromIndex.coerceAtLeast(0), points.size)
    }

    /**
     * Finds the index of the nearest point in the polyline to [target].
     */
    fun findNearestPointIndex(points: List<LatLng>, target: LatLng): Int {
        var minDist = Double.MAX_VALUE
        var minIndex = 0
        for (i in points.indices) {
            val d = haversineDistance(points[i], target)
            if (d < minDist) {
                minDist = d
                minIndex = i
            }
        }
        return minIndex
    }

    fun haversineDistance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private fun totalDistance(points: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            sum += haversineDistance(points[i], points[i + 1])
        }
        return sum
    }

    private fun sampleFullRouteAnchors(
        points: List<LatLng>,
        intervalMeters: Double,
    ): List<RouteAnchor> {
        val routeDistance = totalDistance(points)
        if (routeDistance <= 0.0 || !routeDistance.isFinite()) return emptyList()

        val targetDistances = if (routeDistance <= intervalMeters) {
            listOf(routeDistance / 2.0)
        } else {
            buildList {
                var targetDistance = intervalMeters
                while (targetDistance < routeDistance) {
                    add(targetDistance)
                    targetDistance += intervalMeters
                }
            }
        }

        val anchors = ArrayList<RouteAnchor>(targetDistances.size)
        var segmentStartDistance = 0.0
        var targetIndex = 0

        for (routeIndex in 0 until points.lastIndex) {
            if (targetIndex >= targetDistances.size) break

            val segmentDistance = haversineDistance(points[routeIndex], points[routeIndex + 1])
            val segmentEndDistance = segmentStartDistance + segmentDistance

            if (segmentDistance > 0.0) {
                while (
                    targetIndex < targetDistances.size &&
                    targetDistances[targetIndex] <= segmentEndDistance
                ) {
                    val fraction = (
                        (targetDistances[targetIndex] - segmentStartDistance) / segmentDistance
                    ).coerceIn(0.0, 1.0)
                    anchors += RouteAnchor(
                        point = interpolate(points[routeIndex], points[routeIndex + 1], fraction),
                        routeIndex = routeIndex,
                    )
                    targetIndex++
                }
            }

            segmentStartDistance = segmentEndDistance
        }

        return anchors
    }

    private fun <T> List<T>.evenlyCapped(limit: Int): List<T> {
        if (size <= limit) return this
        if (limit == 1) return listOf(this[size / 2])

        return List(limit) { outputIndex ->
            val sourceIndex = (outputIndex.toDouble() * lastIndex / (limit - 1)).toInt()
            this[sourceIndex]
        }
    }

    private fun interpolate(a: LatLng, b: LatLng, fraction: Double): LatLng {
        val lat1 = Math.toRadians(a.latitude)
        val lng1 = Math.toRadians(a.longitude)
        val lat2 = Math.toRadians(b.latitude)
        val lng2 = Math.toRadians(b.longitude)

        val d = 2 * asin(
            sqrt(
                sin((lat2 - lat1) / 2) * sin((lat2 - lat1) / 2) +
                        cos(lat1) * cos(lat2) * sin((lng2 - lng1) / 2) * sin((lng2 - lng1) / 2)
            )
        )

        if (d < 1e-10) return a

        val A = sin((1 - fraction) * d) / sin(d)
        val B = sin(fraction * d) / sin(d)
        val x = A * cos(lat1) * cos(lng1) + B * cos(lat2) * cos(lng2)
        val y = A * cos(lat1) * sin(lng1) + B * cos(lat2) * sin(lng2)
        val z = A * sin(lat1) + B * sin(lat2)

        return LatLng(
            Math.toDegrees(atan2(z, sqrt(x * x + y * y))),
            Math.toDegrees(atan2(y, x)),
        )
    }
}
