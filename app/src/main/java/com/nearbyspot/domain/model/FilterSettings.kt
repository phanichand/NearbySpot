package com.nearbyspot.domain.model

data class FilterSettings(
    val travelTimeMinutes: Int = 10,
    val transportMode: TransportMode = TransportMode.WALK,
    val maxPriceLevel: Int? = null,
    val openNowOnly: Boolean = false,
    val sortBy: SortOption = SortOption.SMART,
)
