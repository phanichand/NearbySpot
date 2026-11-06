package com.nearbyspot.data.remote.dto

import com.google.gson.annotations.SerializedName

// --- Shared DTOs ---

data class Waypoint(
    val location: WaypointLocation,
)

data class WaypointLocation(
    val latLng: LatLngDto,
)

data class LatLngDto(
    val latitude: Double,
    val longitude: Double,
)

// --- computeRouteMatrix Request ---

data class RouteMatrixRequest(
    val origins: List<RouteMatrixOrigin>,
    val destinations: List<RouteMatrixDestination>,
    val travelMode: String,
)

data class RouteMatrixOrigin(
    val waypoint: Waypoint,
)

data class RouteMatrixDestination(
    val waypoint: Waypoint,
)

// --- computeRouteMatrix Response ---

data class RouteMatrixElement(
    @SerializedName("originIndex") val originIndex: Int?,
    @SerializedName("destinationIndex") val destinationIndex: Int?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("distanceMeters") val distanceMeters: Int?,
    @SerializedName("status") val status: RouteMatrixStatus?,
    @SerializedName("condition") val condition: String?,
)

data class RouteMatrixStatus(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
)

// --- computeRoutes Request ---

data class ComputeRoutesRequest(
    val origin: RouteEndpoint,
    val destination: RouteEndpoint,
    val travelMode: String,
    val routingPreference: String? = null,
    val departureTime: String? = null,
)

data class RouteEndpoint(
    val location: WaypointLocation,
)

// --- computeRoutes Response ---

data class ComputeRoutesResponse(
    @SerializedName("routes") val routes: List<RouteDto>?,
)

data class RouteDto(
    @SerializedName("duration") val duration: String?,
    @SerializedName("distanceMeters") val distanceMeters: Int?,
    @SerializedName("polyline") val polyline: PolylineDto?,
)

data class PolylineDto(
    @SerializedName("encodedPolyline") val encodedPolyline: String?,
)
