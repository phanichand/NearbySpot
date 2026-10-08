package com.nearbyspot.domain.model

data class RouteMatrixEntry(
    val originIndex: Int,
    val destinationIndex: Int,
    val durationSeconds: Int,
    val distanceMeters: Int,
)
