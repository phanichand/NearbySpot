package com.nearbyspot.domain.usecase

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.GeoPoint
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteMatrixEntry
import com.nearbyspot.domain.model.RouteSearchException
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.RouteSearchProgress
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.PolylineUtils
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

class GetAlongRoutePlacesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {

    suspend operator fun invoke(
        routeInfo: RouteInfo,
        currentLatLng: LatLng,
        currentRouteIndex: Int,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
        onProgress: (RouteSearchProgress) -> Unit = {},
    ): List<AlongRoutePlace> {
        val diagnostics = RouteSearchDiagnostics { summary ->
            runCatching { Log.d(TAG, summary) }
        }
        var outcome = RouteSearchDiagnostics.Outcome.SUCCESS

        return try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                search(
                    routeInfo = routeInfo,
                    currentLatLng = currentLatLng,
                    currentRouteIndex = currentRouteIndex,
                    placeTypes = placeTypes,
                    filters = filters,
                    diagnostics = diagnostics,
                    onProgress = onProgress,
                )
            } ?: throw RouteSearchException(RouteSearchFailure.TIMED_OUT)
        } catch (cancellation: CancellationException) {
            outcome = RouteSearchDiagnostics.Outcome.CANCELLATION
            throw cancellation
        } catch (error: RouteSearchException) {
            outcome = RouteSearchDiagnostics.Outcome.FAILURE
            throw error
        } catch (error: Exception) {
            outcome = RouteSearchDiagnostics.Outcome.FAILURE
            throw RouteSearchException(RouteSearchFailure.SERVICE_UNAVAILABLE)
        } catch (error: Throwable) {
            outcome = RouteSearchDiagnostics.Outcome.FAILURE
            throw error
        } finally {
            diagnostics.finish(outcome)
        }
    }

    private suspend fun search(
        routeInfo: RouteInfo,
        currentLatLng: LatLng,
        currentRouteIndex: Int,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
        diagnostics: RouteSearchDiagnostics,
        onProgress: (RouteSearchProgress) -> Unit,
    ): List<AlongRoutePlace> = coroutineScope {
        onProgress(RouteSearchProgress.Planning)
        val destination = routeInfo.decodedPoints.last()
        val routePlaces = if (routeInfo.encodedPolyline.isNotBlank()) {
            onProgress(RouteSearchProgress.SearchingStops(completed = 0, total = 2))
            try {
                withTimeoutOrNull(ROUTE_SEARCH_TIMEOUT_MS) {
                    repository.searchAlongRoute(routeInfo.encodedPolyline, placeTypes)
                }?.also {
                    onProgress(RouteSearchProgress.SearchingStops(completed = 1, total = 2))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                diagnostics.increment(RouteSearchDiagnostics.Counter.PARTIAL_FAILURES)
                null
            }
        } else {
            null
        }
        val destinationPlaces = if (routeInfo.encodedPolyline.isNotBlank()) {
            try {
                withTimeoutOrNull(ANCHOR_SEARCH_TIMEOUT_MS) {
                    repository.searchNearbyPlaces(
                        latitude = destination.latitude,
                        longitude = destination.longitude,
                        radiusMeters = DESTINATION_SEARCH_RADIUS_METERS,
                        placeTypes = placeTypes,
                    )
                }?.also {
                    onProgress(RouteSearchProgress.SearchingStops(completed = 2, total = 2))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                diagnostics.increment(RouteSearchDiagnostics.Counter.PARTIAL_FAILURES)
                null
            }
        } else {
            null
        }
        val routeDiscoveryPlaces = (routePlaces.orEmpty() + destinationPlaces.orEmpty())
            .distinctBy(Place::placeId)
        val usedRouteSearch = routeDiscoveryPlaces.isNotEmpty()

        val discoveries = if (usedRouteSearch) {
            listOf(AnchorDiscovery(anchorIndex = 0, places = routeDiscoveryPlaces))
        } else {
            val anchors = diagnostics.measure(RouteSearchDiagnostics.Stage.WAYPOINT_PLANNING) {
                PolylineUtils.planStableRouteAnchors(
                    points = routeInfo.decodedPoints,
                    currentRouteIndex = currentRouteIndex,
                    intervalMeters = filters.transportMode.anchorIntervalMeters,
                    destinationExclusionMeters = SKIP_NEAR_DESTINATION_METERS,
                    maxAnchors = MAX_SEARCH_ANCHORS,
                )
            }

            if (anchors.isEmpty()) {
                onProgress(RouteSearchProgress.Complete)
                return@coroutineScope emptyList()
            }

            var completedAnchors = 0
            val progressLock = Any()
            val discoverySemaphore = Semaphore(MAX_CONCURRENT_DISCOVERY_CALLS)
            onProgress(RouteSearchProgress.SearchingStops(completed = 0, total = anchors.size))

            diagnostics.measure(RouteSearchDiagnostics.Stage.SEARCH_DISCOVERY) {
                anchors.mapIndexed { anchorIndex, anchor ->
                    async {
                        discoverySemaphore.withPermit {
                            diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_ATTEMPTED)
                            diagnostics.increment(RouteSearchDiagnostics.Counter.NEARBY_REPOSITORY_CALLS)
                            val places = try {
                                withTimeoutOrNull(ANCHOR_SEARCH_TIMEOUT_MS) {
                                    repository.searchNearbyPlaces(
                                        latitude = anchor.point.latitude,
                                        longitude = anchor.point.longitude,
                                        radiusMeters = SEARCH_RADIUS_METERS,
                                        placeTypes = placeTypes,
                                    )
                                } ?: throw AnchorSearchTimeoutException()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                diagnostics.increment(RouteSearchDiagnostics.Counter.PARTIAL_FAILURES)
                                null
                            }

                            if (places != null) {
                                diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_SUCCEEDED)
                            }
                            synchronized(progressLock) {
                                completedAnchors++
                                onProgress(
                                    RouteSearchProgress.SearchingStops(completedAnchors, anchors.size),
                                )
                            }
                            AnchorDiscovery(anchorIndex, places)
                        }
                    }
                }.awaitAll()
            }
        }

        if (discoveries.all { it.places == null }) {
            throw RouteSearchException(RouteSearchFailure.ALL_ANCHORS_FAILED)
        }

        diagnostics.increment(
            RouteSearchDiagnostics.Counter.CANDIDATES_DISCOVERED,
            discoveries.sumOf { it.places?.size ?: 0 },
        )

        val candidates = diagnostics.measure(RouteSearchDiagnostics.Stage.FILTERING) {
            budgetCandidates(
                discoveries = discoveries,
                destination = destination,
                filters = filters,
                diagnostics = diagnostics,
                maxCandidatesPerGroup = if (usedRouteSearch) {
                    MAX_MATRIX_CANDIDATES
                } else {
                    MAX_CANDIDATES_PER_ANCHOR
                },
                excludeNearDestination = !usedRouteSearch,
            )
        }
        if (candidates.isEmpty()) {
            onProgress(RouteSearchProgress.Complete)
            return@coroutineScope emptyList()
        }

        onProgress(RouteSearchProgress.CalculatingTravelTimes)
        val currentPoint = currentLatLng.toGeoPoint()
        val destinationPoint = destination.toGeoPoint()
        val candidatePoints = candidates.map { it.place.toGeoPoint() }

        diagnostics.increment(RouteSearchDiagnostics.Counter.TRAVEL_TIME_REPOSITORY_CALLS, 2)
        diagnostics.increment(
            RouteSearchDiagnostics.Counter.MATRIX_ELEMENTS_REQUESTED,
            candidatePoints.size * 2 + 1,
        )
        val (outboundEntries, onwardEntries) = diagnostics.measure(
            RouteSearchDiagnostics.Stage.TRAVEL_MATRICES,
        ) {
            val outbound = async {
                repository.getRouteMatrix(
                    origins = listOf(currentPoint),
                    destinations = candidatePoints + destinationPoint,
                    mode = filters.transportMode,
                )
            }
            val onward = async {
                repository.getRouteMatrix(
                    origins = candidatePoints,
                    destinations = listOf(destinationPoint),
                    mode = filters.transportMode,
                )
            }
            outbound.await() to onward.await()
        }

        val outboundByDestination = outboundEntries
            .filter { it.originIndex == 0 }
            .associateBy(RouteMatrixEntry::destinationIndex)
        val baselineSeconds = outboundByDestination[candidates.size]?.durationSeconds
            ?: throw RouteSearchException(RouteSearchFailure.DESTINATION_TRAVEL_TIME_UNAVAILABLE)
        val onwardByOrigin = onwardEntries
            .filter { it.destinationIndex == 0 }
            .associateBy(RouteMatrixEntry::originIndex)

        val verifiedLegs = candidates.mapIndexedNotNull { index, candidate ->
            val reachSeconds = outboundByDestination[index]?.durationSeconds
                ?: return@mapIndexedNotNull null
            val onwardSeconds = onwardByOrigin[index]?.durationSeconds
                ?: return@mapIndexedNotNull null
            val detourSeconds = computeDetourSeconds(
                currentToPlaceSeconds = reachSeconds,
                placeToDestinationSeconds = onwardSeconds,
                currentToDestinationSeconds = baselineSeconds,
            )
            VerifiedLegs(candidate, reachSeconds, detourSeconds)
        }
        diagnostics.increment(
            RouteSearchDiagnostics.Counter.REACH_VERIFIED,
            candidates.indices.count(outboundByDestination::containsKey),
        )
        diagnostics.increment(RouteSearchDiagnostics.Counter.DETOUR_VERIFIED, verifiedLegs.size)

        val eligible = verifiedLegs.mapNotNull { verified ->
            if (verified.detourSeconds / 60.0 > filters.maxDetourMinutes) {
                return@mapNotNull null
            }
            AlongRoutePlace(
                place = verified.candidate.place,
                reachableSeconds = verified.reachSeconds,
                detourSeconds = verified.detourSeconds,
                nearestRoutePointIndex = PolylineUtils.findNearestPointIndex(
                    routeInfo.decodedPoints,
                    LatLng(
                        verified.candidate.place.latitude,
                        verified.candidate.place.longitude,
                    ),
                ),
                score = computeScore(
                    rating = verified.candidate.place.rating,
                    reviewCount = verified.candidate.place.userRatingCount,
                    detourMinutes = verified.detourSeconds / 60.0,
                    maxDetourMinutes = filters.maxDetourMinutes,
                ),
            )
        }

        eligible
            .sortedWith(popularityComparator)
            .take(MAX_RESULTS)
            .also { results ->
                diagnostics.increment(RouteSearchDiagnostics.Counter.FINAL_RESULTS, results.size)
                onProgress(RouteSearchProgress.Complete)
            }
    }

    private fun budgetCandidates(
        discoveries: List<AnchorDiscovery>,
        destination: LatLng,
        filters: RouteFilterSettings,
        diagnostics: RouteSearchDiagnostics,
        maxCandidatesPerGroup: Int,
        excludeNearDestination: Boolean,
    ): List<AnchoredPlace> {
        val deduped = discoveries
            .flatMap { discovery ->
                discovery.places.orEmpty().map { AnchoredPlace(it, discovery.anchorIndex) }
            }
            .distinctBy { it.place.placeId }
        diagnostics.increment(RouteSearchDiagnostics.Counter.CANDIDATES_DEDUPED, deduped.size)

        val filtered = deduped.filter { candidate ->
            val place = candidate.place
            place.userRatingCount >= MIN_REVIEW_COUNT &&
                matchesOpenNowFilter(place.isOpen, filters.openNowOnly) &&
                (filters.maxPriceLevel == null || place.priceLevel == null ||
                    place.priceLevel <= filters.maxPriceLevel) &&
                (!excludeNearDestination || PolylineUtils.haversineDistance(
                    LatLng(place.latitude, place.longitude),
                    destination,
                ) > SKIP_NEAR_DESTINATION_METERS)
        }
        diagnostics.increment(RouteSearchDiagnostics.Counter.CANDIDATES_FILTERED, filtered.size)

        val perAnchor = filtered
            .groupBy(AnchoredPlace::anchorIndex)
            .toSortedMap()
            .values
            .map { group ->
                group.sortedWith(candidateQualityComparator).take(maxCandidatesPerGroup)
            }
        val diverse = buildList {
            for (rank in 0 until maxCandidatesPerGroup) {
                for (group in perAnchor) {
                    group.getOrNull(rank)?.let(::add)
                    if (size == MAX_MATRIX_CANDIDATES) return@buildList
                }
            }
        }
        diagnostics.increment(RouteSearchDiagnostics.Counter.CANDIDATES_BUDGETED, diverse.size)
        return diverse
    }

    private data class AnchorDiscovery(
        val anchorIndex: Int,
        val places: List<Place>?,
    )

    private data class AnchoredPlace(
        val place: Place,
        val anchorIndex: Int,
    )

    private data class VerifiedLegs(
        val candidate: AnchoredPlace,
        val reachSeconds: Int,
        val detourSeconds: Int,
    )

    private class AnchorSearchTimeoutException : Exception()

    companion object {
        private const val TAG = "AlongRoutePlaces"
        const val MIN_REVIEW_COUNT = 100
        const val MAX_RESULTS = 10
        const val SEARCH_RADIUS_METERS = 2000.0
        const val DESTINATION_SEARCH_RADIUS_METERS = 15_000.0
        const val SKIP_NEAR_DESTINATION_METERS = 3000.0
        const val MAX_SEARCH_ANCHORS = 8
        const val MAX_CONCURRENT_DISCOVERY_CALLS = 3
        const val MAX_CANDIDATES_PER_ANCHOR = 3
        const val MAX_MATRIX_CANDIDATES = 24
        const val SEARCH_TIMEOUT_MS = 30_000L
        const val ANCHOR_SEARCH_TIMEOUT_MS = 8_000L
        const val ROUTE_SEARCH_TIMEOUT_MS = 12_000L
        private const val MIN_RATING = 3.5

        private val candidateQualityComparator =
            compareByDescending<AnchoredPlace> { it.place.userRatingCount }
                .thenByDescending { it.place.rating }
                .thenBy { it.place.placeId }

        private val popularityComparator =
            compareByDescending<AlongRoutePlace> { it.place.userRatingCount }
                .thenByDescending { it.place.rating }
                .thenBy { it.detourSeconds }
                .thenBy { it.place.placeId }

        fun computeDetourSeconds(
            currentToPlaceSeconds: Int,
            placeToDestinationSeconds: Int,
            currentToDestinationSeconds: Int,
        ): Int = (
            currentToPlaceSeconds.toLong() + placeToDestinationSeconds -
                currentToDestinationSeconds
            )
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

        fun computeScore(
            rating: Double,
            reviewCount: Int,
            detourMinutes: Double,
            maxDetourMinutes: Int,
        ): Double {
            val popularityBase = Math.pow(reviewCount.toDouble(), 0.6)
            val ratingBoost = 1.0 +
                ((rating - MIN_RATING) / (5.0 - MIN_RATING)).coerceIn(0.0, 1.0)
            val effectiveMaxDetour = maxDetourMinutes.coerceAtLeast(1)
            val detourPenalty = 1.0 -
                (detourMinutes / (effectiveMaxDetour * 2.0)).coerceIn(0.0, 0.4)
            return popularityBase * ratingBoost * detourPenalty
        }
    }
}

private val TransportMode.anchorIntervalMeters: Double
    get() = when (this) {
        TransportMode.DRIVE -> 12_000.0
        TransportMode.WALK -> 1_500.0
        TransportMode.BICYCLE -> 3_000.0
    }

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

private fun Place.toGeoPoint() = GeoPoint(latitude, longitude)
