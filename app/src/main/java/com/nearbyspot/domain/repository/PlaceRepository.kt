package com.nearbyspot.domain.repository

import com.nearbyspot.domain.model.GeoPoint
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteMatrixEntry
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.model.TravelInfo
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    /** Restarts the per-trip Places call ceiling; call when the traveller picks a new destination. */
    fun beginTrip()

    /** Ends the trip so later searches (such as Home) are not held to its call ceiling. */
    fun endTrip()

    suspend fun searchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        placeTypes: List<String>,
    ): List<Place>

    suspend fun searchAlongRoute(
        encodedPolyline: String,
        placeTypes: List<String>,
    ): List<Place>

    suspend fun getTravelTimes(
        originLat: Double,
        originLng: Double,
        destinations: List<Place>,
        mode: TransportMode,
    ): List<TravelInfo>

    suspend fun getRouteMatrix(
        origins: List<GeoPoint>,
        destinations: List<GeoPoint>,
        mode: TransportMode,
    ): List<RouteMatrixEntry>

    suspend fun computeRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TransportMode,
        departureTimeMillis: Long? = null,
    ): RouteInfo

    suspend fun fetchReviews(placeId: String): List<com.nearbyspot.domain.model.Review>

    suspend fun saveFavorite(place: Place)
    suspend fun removeFavorite(placeId: String)
    suspend fun getFavorites(): List<Place>
    suspend fun isFavorite(placeId: String): Boolean
    fun observeFavoriteIds(): Flow<List<String>>
}
