package com.nearbyspot.domain.model

data class RouteFilterSettings(
    val maxDetourMinutes: Int = 15,
    val transportMode: TransportMode = TransportMode.DRIVE,
    val maxPriceLevel: Int? = null,
    val openNowOnly: Boolean = false,
)
