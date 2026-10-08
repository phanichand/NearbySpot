package com.nearbyspot.domain.model

data class RankedPlace(
    val place: Place,
    val travelInfo: TravelInfo,
    val score: Double,
)
