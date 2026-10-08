package com.nearbyspot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionPolicyTest {

    @Test
    fun `fine permission enables high accuracy location`() {
        assertTrue(LocationPermissionPolicy.hasPermission(true, false))
        assertEquals(
            LocationAccuracy.HIGH,
            LocationPermissionPolicy.accuracy(true, false),
        )
    }

    @Test
    fun `coarse only permission enables balanced accuracy location`() {
        assertTrue(LocationPermissionPolicy.hasPermission(false, true))
        assertEquals(
            LocationAccuracy.BALANCED,
            LocationPermissionPolicy.accuracy(false, true),
        )
    }

    @Test
    fun `fine permission takes precedence when both permissions are granted`() {
        assertTrue(LocationPermissionPolicy.hasPermission(true, true))
        assertEquals(
            LocationAccuracy.HIGH,
            LocationPermissionPolicy.accuracy(true, true),
        )
    }

    @Test
    fun `no permission disables location`() {
        assertFalse(LocationPermissionPolicy.hasPermission(false, false))
        assertNull(LocationPermissionPolicy.accuracy(false, false))
    }
}
