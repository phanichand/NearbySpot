package com.nearbyspot.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceResultValidatorTest {

    @Test
    fun `accepts valid identity and boundary coordinates including zero`() {
        assertTrue(PlaceResultValidator.isValid("place-id", 0.0, 0.0))
        assertTrue(PlaceResultValidator.isValid("place-id", -90.0, -180.0))
        assertTrue(PlaceResultValidator.isValid("place-id", 90.0, 180.0))
    }

    @Test
    fun `rejects missing or blank identity`() {
        assertFalse(PlaceResultValidator.isValid(null, 12.0, 77.0))
        assertFalse(PlaceResultValidator.isValid("", 12.0, 77.0))
        assertFalse(PlaceResultValidator.isValid("   ", 12.0, 77.0))
    }

    @Test
    fun `rejects missing non-finite or out-of-range latitude`() {
        assertFalse(PlaceResultValidator.isValid("id", null, 77.0))
        assertFalse(PlaceResultValidator.isValid("id", Double.NaN, 77.0))
        assertFalse(PlaceResultValidator.isValid("id", Double.POSITIVE_INFINITY, 77.0))
        assertFalse(PlaceResultValidator.isValid("id", 90.0001, 77.0))
        assertFalse(PlaceResultValidator.isValid("id", -90.0001, 77.0))
    }

    @Test
    fun `rejects missing non-finite or out-of-range longitude`() {
        assertFalse(PlaceResultValidator.isValid("id", 12.0, null))
        assertFalse(PlaceResultValidator.isValid("id", 12.0, Double.NaN))
        assertFalse(PlaceResultValidator.isValid("id", 12.0, Double.NEGATIVE_INFINITY))
        assertFalse(PlaceResultValidator.isValid("id", 12.0, 180.0001))
        assertFalse(PlaceResultValidator.isValid("id", 12.0, -180.0001))
    }
}
