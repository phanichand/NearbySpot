package com.nearbyspot.util

internal enum class LocationAccuracy {
    HIGH,
    BALANCED,
}

internal object LocationPermissionPolicy {
    fun hasPermission(hasFinePermission: Boolean, hasCoarsePermission: Boolean): Boolean =
        hasFinePermission || hasCoarsePermission

    fun accuracy(hasFinePermission: Boolean, hasCoarsePermission: Boolean): LocationAccuracy? =
        when {
            hasFinePermission -> LocationAccuracy.HIGH
            hasCoarsePermission -> LocationAccuracy.BALANCED
            else -> null
        }
}
