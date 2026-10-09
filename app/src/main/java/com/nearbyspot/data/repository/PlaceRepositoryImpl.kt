package com.nearbyspot.data.repository

import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.nearbyspot.data.local.FavoriteEntity
import com.nearbyspot.data.local.PlaceDao
import com.nearbyspot.data.local.PlaceEntity
import com.nearbyspot.data.remote.PlacesDataSource
import com.nearbyspot.data.remote.RoutesApiService
import com.nearbyspot.data.remote.dto.ComputeRoutesRequest
import com.nearbyspot.data.remote.dto.LatLngDto
import com.nearbyspot.data.remote.dto.RouteEndpoint
import com.nearbyspot.data.remote.dto.RouteMatrixDestination
import com.nearbyspot.data.remote.dto.RouteMatrixOrigin
import com.nearbyspot.data.remote.dto.RouteMatrixRequest
import com.nearbyspot.data.remote.dto.Waypoint
import com.nearbyspot.data.remote.dto.WaypointLocation
import com.nearbyspot.domain.model.GeoPoint
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteMatrixEntry
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.model.TravelInfo
import com.nearbyspot.domain.repository.PlaceRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placesDataSource: PlacesDataSource,
    private val routesApiService: RoutesApiService,
    private val placeDao: PlaceDao,
    private val gson: Gson,
) : PlaceRepository {

    override suspend fun searchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        placeTypes: List<String>,
    ): List<Place> {
        val cacheKey = buildCacheKey(latitude, longitude, radiusMeters, placeTypes)
        val minEpoch = System.currentTimeMillis() - CACHE_TTL_MS

        val cached = placeDao.getCachedPlaces(cacheKey, minEpoch)
        if (cached.isNotEmpty()) {
            val decoded = cached.mapNotNull { entity ->
                runCatching { gson.fromJson(entity.placeJson, Place::class.java) }.getOrNull()
            }
            if (decoded.isNotEmpty()) return decoded
        }

        val places = placesDataSource.searchNearby(latitude, longitude, radiusMeters, placeTypes)

        val now = System.currentTimeMillis()
        val entities = places.map { place ->
            PlaceEntity(
                placeId = place.placeId,
                placeJson = gson.toJson(place),
                cacheKey = cacheKey,
                lastFetchedEpochMs = now,
            )
        }
        if (entities.isNotEmpty()) {
            placeDao.insertPlaces(entities)
        }

        placeDao.deleteStale(System.currentTimeMillis() - STALE_CLEANUP_MS)

        return places
    }

    private fun buildCacheKey(lat: Double, lng: Double, radius: Double, types: List<String>): String {
        return buildPlaceCacheKey(lat, lng, radius, types)
    }

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val STALE_CLEANUP_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    override suspend fun getTravelTimes(
        originLat: Double,
        originLng: Double,
        destinations: List<Place>,
        mode: TransportMode,
    ): List<TravelInfo> {
        val entries = getRouteMatrix(
            origins = listOf(GeoPoint(originLat, originLng)),
            destinations = destinations.map { GeoPoint(it.latitude, it.longitude) },
            mode = mode,
        )
        return entries.map { entry ->
            TravelInfo(
                placeId = destinations[entry.destinationIndex].placeId,
                durationSeconds = entry.durationSeconds,
                distanceMeters = entry.distanceMeters,
            )
        }
    }

    override suspend fun getRouteMatrix(
        origins: List<GeoPoint>,
        destinations: List<GeoPoint>,
        mode: TransportMode,
    ): List<RouteMatrixEntry> {
        if (origins.isEmpty() || destinations.isEmpty()) return emptyList()

        val request = RouteMatrixRequest(
            origins = origins.map { point -> point.toRouteMatrixOrigin() },
            destinations = destinations.map { point -> point.toRouteMatrixDestination() },
            travelMode = mode.routesApiValue,
        )
        val response = routesApiService.computeRouteMatrix(request = request)

        return mapRouteMatrixElements(
            elements = response,
            originCount = origins.size,
            destinationCount = destinations.size,
        )
    }

    override suspend fun computeRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TransportMode,
    ): RouteInfo {
        val request = ComputeRoutesRequest(
            origin = RouteEndpoint(
                location = WaypointLocation(LatLngDto(originLat, originLng))
            ),
            destination = RouteEndpoint(
                location = WaypointLocation(LatLngDto(destLat, destLng))
            ),
            travelMode = mode.routesApiValue,
        )

        val response = routesApiService.computeRoutes(request = request)
        val route = response.routes?.firstOrNull()
            ?: throw IllegalStateException("No route found")

        val encodedPolyline = route.polyline?.encodedPolyline
            ?: throw IllegalStateException("Route has no polyline")

        val durationSeconds = route.duration
            ?.removeSuffix("s")
            ?.toIntOrNull() ?: 0

        val decodedPoints = PolyUtil.decode(encodedPolyline)

        return RouteInfo(
            encodedPolyline = encodedPolyline,
            durationSeconds = durationSeconds,
            distanceMeters = route.distanceMeters ?: 0,
            decodedPoints = decodedPoints,
        )
    }

    override suspend fun fetchReviews(placeId: String): List<com.nearbyspot.domain.model.Review> {
        return placesDataSource.fetchReviews(placeId)
    }

    override suspend fun saveFavorite(place: Place) {
        placeDao.insertFavorite(
            FavoriteEntity(
                placeId = place.placeId,
                placeJson = gson.toJson(place),
                savedAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun removeFavorite(placeId: String) {
        placeDao.deleteFavorite(placeId)
    }

    override suspend fun getFavorites(): List<Place> {
        return placeDao.getAllFavorites().mapNotNull { entity ->
            runCatching { gson.fromJson(entity.placeJson, Place::class.java) }.getOrNull()
        }
    }

    override suspend fun isFavorite(placeId: String): Boolean {
        return placeDao.isFavorite(placeId)
    }

    override fun observeFavoriteIds(): Flow<List<String>> {
        return placeDao.observeFavoriteIds()
    }
}

private fun GeoPoint.toWaypoint(): Waypoint = Waypoint(
    location = WaypointLocation(LatLngDto(latitude, longitude)),
)

private fun GeoPoint.toRouteMatrixOrigin(): RouteMatrixOrigin = RouteMatrixOrigin(
    waypoint = toWaypoint(),
)

private fun GeoPoint.toRouteMatrixDestination(): RouteMatrixDestination = RouteMatrixDestination(
    waypoint = toWaypoint(),
)

internal fun buildPlaceCacheKey(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    placeTypes: List<String>,
): String {
    val roundedLatitude = String.format(Locale.ROOT, "%.3f", latitude)
    val roundedLongitude = String.format(Locale.ROOT, "%.3f", longitude)
    val roundedRadius = (radiusMeters / 100).toInt() * 100
    return "${roundedLatitude}_${roundedLongitude}_${roundedRadius}_${placeTypes.sorted().joinToString(",")}"
}
