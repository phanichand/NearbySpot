package com.nearbyspot.util

import com.nearbyspot.domain.model.TransportMode

object RadiusEstimator {

    private const val WALK_SPEED_MPS = 1.4        // ~5 km/h
    private const val BICYCLE_SPEED_MPS = 4.2     // ~15 km/h
    private const val DRIVE_SPEED_MPS = 11.1      // ~40 km/h (urban average)

    /**
     * Converts travel-time minutes + transport mode into an approximate radius in meters
     * for the Places nearby search. Adds a 20% buffer to avoid under-fetching.
     */
    fun estimateRadiusMeters(travelTimeMinutes: Int, mode: TransportMode): Double {
        val speedMps = when (mode) {
            TransportMode.WALK -> WALK_SPEED_MPS
            TransportMode.BICYCLE -> BICYCLE_SPEED_MPS
            TransportMode.DRIVE -> DRIVE_SPEED_MPS
        }
        val radiusMeters = speedMps * travelTimeMinutes * 60.0
        return (radiusMeters * 1.2).coerceAtMost(50_000.0)
    }
}
