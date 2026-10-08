package com.nearbyspot.domain.model

data class TravelInfo(
    val placeId: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
) {
    val durationMinutes: Double get() = durationSeconds / 60.0
}
