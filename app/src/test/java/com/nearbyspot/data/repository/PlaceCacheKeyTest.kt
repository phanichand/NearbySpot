package com.nearbyspot.data.repository

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCacheKeyTest {

    @Test
    fun `cache key is stable in locales with decimal commas`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals(
                "12.346_77.654_1200_cafe,restaurant",
                buildPlaceCacheKey(
                    latitude = 12.3456,
                    longitude = 77.6543,
                    radiusMeters = 1_299.0,
                    placeTypes = listOf("restaurant", "cafe"),
                ),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
