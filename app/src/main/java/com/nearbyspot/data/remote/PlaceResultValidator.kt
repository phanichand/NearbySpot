package com.nearbyspot.data.remote

/**
 * Validates the identity and coordinates required to safely expose a Places result.
 * Optional metadata is deliberately not part of this check.
 */
internal object PlaceResultValidator {
    fun isValid(
        placeId: String?,
        latitude: Double?,
        longitude: Double?,
    ): Boolean {
        return !placeId.isNullOrBlank() &&
            latitude != null &&
            latitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude != null &&
            longitude.isFinite() &&
            longitude in -180.0..180.0
    }
}
