package com.nearbyspot.domain.usecase

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.PolylineUtils
import kotlinx.coroutines.CancellationException
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
        val diagnostics = RouteSearchDiagnostics { summary -> Log.d(TAG, summary) }
        var outcome = RouteSearchDiagnostics.Outcome.SUCCESS
        try {
            val (aheadPoints, waypoints) = diagnostics.measure(
                RouteSearchDiagnostics.Stage.WAYPOINT_PLANNING,
            ) {
                val points = PolylineUtils.getAheadPoints(routeInfo.decodedPoints, currentRouteIndex)

                if (points.size < 2) return@measure points to emptyList()

                val intervalMeters = when (filters.transportMode) {
                    TransportMode.DRIVE -> 12_000.0
                    TransportMode.WALK -> 1_500.0
                    TransportMode.BICYCLE -> 3_000.0
                }

                points to PolylineUtils.sampleWaypoints(
                    points = points,
                    intervalMeters = intervalMeters,
                )
            }

            if (aheadPoints.size < 2 || waypoints.isEmpty()) return@coroutineScope emptyList()

            val destination = routeInfo.decodedPoints.last()

            val filteredWaypoints = waypoints.filter { wp ->
                PolylineUtils.haversineDistance(wp, destination) > SKIP_NEAR_DESTINATION_METERS
            }

            val searchWaypoints = filteredWaypoints.ifEmpty { waypoints }

            val allPlaces = diagnostics.measure(RouteSearchDiagnostics.Stage.SEARCH_DISCOVERY) {
                searchWaypoints.map { wp ->
                    async {
                        diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_ATTEMPTED)
                        diagnostics.increment(RouteSearchDiagnostics.Counter.NEARBY_REPOSITORY_CALLS)
                        val result = runCatching {
                            repository.searchNearbyPlaces(
                                latitude = wp.latitude,
                                longitude = wp.longitude,
                                radiusMeters = SEARCH_RADIUS_METERS,
                                placeTypes = placeTypes,
                            )
                        }
                        if (result.isSuccess) {
                            diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_SUCCEEDED)
                        }
                        result.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            diagnostics.increment(
                RouteSearchDiagnostics.Counter.CANDIDATES_DISCOVERED,
                allPlaces.size,
            )

            val uniquePlaces = diagnostics.measure(RouteSearchDiagnostics.Stage.FILTERING) {
                val distinct = allPlaces.distinctBy { it.placeId }
                diagnostics.increment(
                    RouteSearchDiagnostics.Counter.CANDIDATES_DEDUPED,
                    distinct.size,
                )

                val afterReviewFilter = distinct.filter { it.userRatingCount >= MIN_REVIEW_COUNT }
                val afterOpenFilter = afterReviewFilter.filter {
                    !filters.openNowOnly || it.isOpen != false
                }
                val afterDestFilter = afterOpenFilter.filter { place ->
                    val placeLatLng = LatLng(place.latitude, place.longitude)
                    PolylineUtils.haversineDistance(placeLatLng, destination) >
                        SKIP_NEAR_DESTINATION_METERS
                }
                afterDestFilter.filter { place ->
                    filters.maxPriceLevel == null || place.priceLevel == null ||
                        place.priceLevel <= filters.maxPriceLevel
                }.also { filtered ->
                    diagnostics.increment(
                        RouteSearchDiagnostics.Counter.CANDIDATES_FILTERED,
                        filtered.size,
                    )
                }
            }

            if (uniquePlaces.isEmpty()) return@coroutineScope emptyList()

            val reachTimes = diagnostics.measure(RouteSearchDiagnostics.Stage.REACH_MATRICES) {
                computeReachTimes(currentLatLng, uniquePlaces, filters.transportMode, diagnostics)
            }
            diagnostics.increment(RouteSearchDiagnostics.Counter.REACH_VERIFIED, reachTimes.size)

            if (reachTimes.isEmpty()) return@coroutineScope emptyList()

            val nearestPoints = reachTimes.map { info ->
                val placeLatLng = LatLng(info.place.latitude, info.place.longitude)
                val nearestIdx = PolylineUtils.findNearestPointIndex(aheadPoints, placeLatLng)
                val globalIdx = nearestIdx + currentRouteIndex
                val nearestRoutePoint = aheadPoints[nearestIdx]
                PlaceWithContext(info.place, info.reachSeconds, globalIdx, nearestRoutePoint)
            }

            val detourInfos = diagnostics.measure(RouteSearchDiagnostics.Stage.DETOUR_MATRICES) {
                computeDetourTimes(nearestPoints, filters.transportMode, diagnostics)
            }
            diagnostics.increment(RouteSearchDiagnostics.Counter.DETOUR_VERIFIED, detourInfos.size)

            val detourFiltered = detourInfos.filter {
                it.detourSeconds / 60.0 <= filters.maxDetourMinutes
            }

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
                .also { results ->
                    diagnostics.increment(RouteSearchDiagnostics.Counter.FINAL_RESULTS, results.size)
                }
        } catch (cancellation: CancellationException) {
            outcome = RouteSearchDiagnostics.Outcome.CANCELLATION
            throw cancellation
        } catch (error: Throwable) {
            outcome = RouteSearchDiagnostics.Outcome.FAILURE
            throw error
        } finally {
            diagnostics.finish(outcome)
        }
    }

    private suspend fun computeReachTimes(
        currentLatLng: LatLng,
        places: List<Place>,
        mode: TransportMode,
        diagnostics: RouteSearchDiagnostics,
    ): List<ReachInfo> {
        val results = mutableListOf<ReachInfo>()
        for (batch in places.chunked(BATCH_SIZE)) {
            diagnostics.increment(RouteSearchDiagnostics.Counter.TRAVEL_TIME_REPOSITORY_CALLS)
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
        diagnostics: RouteSearchDiagnostics,
    ): List<FullTimeInfo> {
        val results = mutableListOf<FullTimeInfo>()
        for (batch in placesWithContext.chunked(BATCH_SIZE)) {
            val travelInfos = batch.flatMap { ctx ->
                diagnostics.increment(RouteSearchDiagnostics.Counter.TRAVEL_TIME_REPOSITORY_CALLS)
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
