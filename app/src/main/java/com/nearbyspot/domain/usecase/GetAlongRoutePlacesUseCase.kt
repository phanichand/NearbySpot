package com.nearbyspot.domain.usecase

import com.google.android.gms.maps.model.LatLng
import android.util.Log
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.PolylineUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class GetAlongRoutePlacesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {

    suspend operator fun invoke(
        routeInfo: RouteInfo,
        currentLatLng: LatLng,
        currentRouteIndex: Int,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
    ): List<AlongRoutePlace> = coroutineScope {
        val aheadPoints = PolylineUtils.getAheadPoints(routeInfo.decodedPoints, currentRouteIndex)

        if (aheadPoints.size < 2) return@coroutineScope emptyList()

        val intervalMeters = when (filters.transportMode) {
            TransportMode.DRIVE -> 12_000.0
            TransportMode.WALK -> 1_500.0
            TransportMode.BICYCLE -> 3_000.0
        }

        val waypoints = PolylineUtils.sampleWaypoints(
            points = aheadPoints,
            intervalMeters = intervalMeters,
        )

        if (waypoints.isEmpty()) return@coroutineScope emptyList()

        val destination = routeInfo.decodedPoints.last()

        val filteredWaypoints = waypoints.filter { wp ->
            PolylineUtils.haversineDistance(wp, destination) > SKIP_NEAR_DESTINATION_METERS
        }

        val searchWaypoints = filteredWaypoints.ifEmpty { waypoints }
        Log.d(TAG, "Waypoints: total=${waypoints.size}, filtered=${filteredWaypoints.size}, searching=${searchWaypoints.size}")

        val allPlaces = searchWaypoints.map { wp ->
            async {
                val result = runCatching {
                    repository.searchNearbyPlaces(
                        latitude = wp.latitude,
                        longitude = wp.longitude,
                        radiusMeters = SEARCH_RADIUS_METERS,
                        placeTypes = placeTypes,
                    )
                }
                if (result.isFailure) {
                    Log.w(TAG, "searchNearby failed at (${wp.latitude},${wp.longitude}): ${result.exceptionOrNull()?.message}")
                }
                result.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
        Log.d(TAG, "Total places from all waypoints: ${allPlaces.size}")

        val distinct = allPlaces.distinctBy { it.placeId }
        Log.d(TAG, "After dedup: ${distinct.size}")

        val afterReviewFilter = distinct.filter { it.userRatingCount >= MIN_REVIEW_COUNT }
        Log.d(TAG, "After min review ($MIN_REVIEW_COUNT): ${afterReviewFilter.size}")

        val afterOpenFilter = afterReviewFilter.filter { !filters.openNowOnly || it.isOpen != false }
        Log.d(TAG, "After open-now filter (openNowOnly=${filters.openNowOnly}): ${afterOpenFilter.size}")

        val afterDestFilter = afterOpenFilter.filter { place ->
            val placeLatLng = LatLng(place.latitude, place.longitude)
            PolylineUtils.haversineDistance(placeLatLng, destination) > SKIP_NEAR_DESTINATION_METERS
        }
        Log.d(TAG, "After destination proximity filter: ${afterDestFilter.size}")

        val uniquePlaces = afterDestFilter.filter { place ->
            filters.maxPriceLevel == null || place.priceLevel == null
                    || place.priceLevel <= filters.maxPriceLevel
        }
        Log.d(TAG, "After price filter: ${uniquePlaces.size}")

        if (uniquePlaces.isEmpty()) return@coroutineScope emptyList()

        val reachTimes = computeReachTimes(currentLatLng, uniquePlaces, filters.transportMode)
        Log.d(TAG, "Reach times computed for ${reachTimes.size} places")

        if (reachTimes.isEmpty()) return@coroutineScope emptyList()

        val nearestPoints = reachTimes.map { info ->
            val placeLatLng = LatLng(info.place.latitude, info.place.longitude)
            val nearestIdx = PolylineUtils.findNearestPointIndex(aheadPoints, placeLatLng)
            val globalIdx = nearestIdx + currentRouteIndex
            val nearestRoutePoint = aheadPoints[nearestIdx]
            PlaceWithContext(info.place, info.reachSeconds, globalIdx, nearestRoutePoint)
        }

        val detourInfos = computeDetourTimes(nearestPoints, filters.transportMode)
        Log.d(TAG, "Detour times computed for ${detourInfos.size} places, maxDetour=${filters.maxDetourMinutes} min")

        val detourFiltered = detourInfos.filter {
            it.detourSeconds / 60.0 <= filters.maxDetourMinutes
        }
        Log.d(TAG, "After detour filter: ${detourFiltered.size}")

        return@coroutineScope detourFiltered
            .map { info ->
                val score = computeScore(
                    rating = info.place.rating,
                    reviewCount = info.place.userRatingCount,
                    detourMinutes = info.detourSeconds / 60.0,
                    maxDetourMinutes = filters.maxDetourMinutes,
                )
                AlongRoutePlace(
                    place = info.place,
                    reachableSeconds = info.reachSeconds,
                    detourSeconds = info.detourSeconds,
                    nearestRoutePointIndex = info.nearestIdx,
                    score = score,
                )
            }
            .sortedByDescending { it.score }
            .take(MAX_RESULTS)
    }

    private suspend fun computeReachTimes(
        currentLatLng: LatLng,
        places: List<Place>,
        mode: TransportMode,
    ): List<ReachInfo> {
        val results = mutableListOf<ReachInfo>()
        for (batch in places.chunked(BATCH_SIZE)) {
            val travelInfos = runCatching {
                repository.getTravelTimes(
                    originLat = currentLatLng.latitude,
                    originLng = currentLatLng.longitude,
                    destinations = batch,
                    mode = mode,
                )
            }.getOrDefault(emptyList())

            val travelMap = travelInfos.associateBy { it.placeId }
            for (place in batch) {
                val seconds = travelMap[place.placeId]?.durationSeconds ?: continue
                results.add(ReachInfo(place, seconds))
            }
        }
        return results
    }

    private suspend fun computeDetourTimes(
        placesWithContext: List<PlaceWithContext>,
        mode: TransportMode,
    ): List<FullTimeInfo> {
        val results = mutableListOf<FullTimeInfo>()
        for (batch in placesWithContext.chunked(BATCH_SIZE)) {
            val travelInfos = batch.flatMap { ctx ->
                runCatching {
                    repository.getTravelTimes(
                        originLat = ctx.nearestRoutePoint.latitude,
                        originLng = ctx.nearestRoutePoint.longitude,
                        destinations = listOf(ctx.place),
                        mode = mode,
                    )
                }.getOrDefault(emptyList())
            }

            val travelMap = travelInfos.associateBy { it.placeId }
            for (ctx in batch) {
                val oneWayFromRoute = travelMap[ctx.place.placeId]?.durationSeconds ?: (ctx.reachSeconds / 3)
                val detourSeconds = oneWayFromRoute * 2
                results.add(
                    FullTimeInfo(
                        place = ctx.place,
                        reachSeconds = ctx.reachSeconds,
                        detourSeconds = detourSeconds,
                        nearestIdx = ctx.nearestIdx,
                    )
                )
            }
        }
        return results
    }

    private data class ReachInfo(val place: Place, val reachSeconds: Int) {
        val reachMinutes: Double get() = reachSeconds / 60.0
    }

    private data class PlaceWithContext(
        val place: Place,
        val reachSeconds: Int,
        val nearestIdx: Int,
        val nearestRoutePoint: LatLng,
    )

    private data class FullTimeInfo(
        val place: Place,
        val reachSeconds: Int,
        val detourSeconds: Int,
        val nearestIdx: Int,
    )

    companion object {
        private const val TAG = "AlongRoutePlaces"
        const val MIN_REVIEW_COUNT = 20
        const val MAX_RESULTS = 10
        const val SEARCH_RADIUS_METERS = 2000.0
        const val BATCH_SIZE = 10
        const val SKIP_NEAR_DESTINATION_METERS = 3000.0
        private const val MIN_RATING = 3.5

        fun computeScore(
            rating: Double,
            reviewCount: Int,
            detourMinutes: Double,
            maxDetourMinutes: Int,
        ): Double {
            val popularityBase = Math.pow(reviewCount.toDouble(), 0.6)
            val ratingBoost = 1.0 + ((rating - MIN_RATING) / (5.0 - MIN_RATING)).coerceIn(0.0, 1.0)
            val detourPenalty = 1.0 - (detourMinutes / (maxDetourMinutes * 2.0)).coerceIn(0.0, 0.4)
            return popularityBase * ratingBoost * detourPenalty
        }
    }
}
