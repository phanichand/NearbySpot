package com.nearbyspot.util

import com.nearbyspot.domain.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadiusEstimatorTest {

    @Test
    fun `walk 5 min gives approximately 500m radius`() {
        val radius = RadiusEstimator.estimateRadiusMeters(5, TransportMode.WALK)
        // 1.4 m/s * 300s * 1.2 = 504m
        assertEquals(504.0, radius, 1.0)
    }

    @Test
    fun `drive 15 min gives approximately 12km radius`() {
        val radius = RadiusEstimator.estimateRadiusMeters(15, TransportMode.DRIVE)
        // 11.1 m/s * 900s * 1.2 = 11988m
        assertEquals(11988.0, radius, 1.0)
    }

    @Test
    fun `bicycle 10 min gives reasonable radius`() {
        val radius = RadiusEstimator.estimateRadiusMeters(10, TransportMode.BICYCLE)
        // 4.2 m/s * 600s * 1.2 = 3024m
        assertEquals(3024.0, radius, 1.0)
    }

    @Test
    fun `radius is capped at 50km`() {
        val radius = RadiusEstimator.estimateRadiusMeters(120, TransportMode.DRIVE)
        assertEquals(50_000.0, radius, 0.1)
    }

    @Test
    fun `walking radius is smaller than driving radius for same time`() {
        val walkRadius = RadiusEstimator.estimateRadiusMeters(10, TransportMode.WALK)
        val driveRadius = RadiusEstimator.estimateRadiusMeters(10, TransportMode.DRIVE)
        assertTrue(walkRadius < driveRadius)
    }
}
