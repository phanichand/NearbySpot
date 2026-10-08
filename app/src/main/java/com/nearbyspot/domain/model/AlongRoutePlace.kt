package com.nearbyspot.domain.model

data class AlongRoutePlace(
    val place: Place,
    val reachableSeconds: Int,
    val detourSeconds: Int,
    val nearestRoutePointIndex: Int,
    val score: Double,
) {
    val reachableMinutes: Double get() = reachableSeconds / 60.0
    val detourMinutes: Double get() = detourSeconds / 60.0
}
