package com.nearbyspot.domain.model

import com.google.android.gms.maps.model.LatLng

data class RouteInfo(
    val encodedPolyline: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val decodedPoints: List<LatLng>,
)
