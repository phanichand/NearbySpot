package com.nearbyspot.domain.usecase

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.GeoPoint
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
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
import kotlin.math.log10
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
        } catch (error: PlacesBudgetExhaustedException) {
            outcome = RouteSearchDiagnostics.Outcome.FAILURE
            throw RouteSearchException(RouteSearchFailure.MONTHLY_LIMIT_REACHED, retryable = false)
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
        val coverageAnchors = diagnostics.measure(RouteSearchDiagnostics.Stage.WAYPOINT_PLANNING) {
            PolylineUtils.planStableRouteAnchors(
                points = routeInfo.decodedPoints,
                currentRouteIndex = currentRouteIndex,
                intervalMeters = filters.transportMode.anchorIntervalMeters,
                destinationExclusionMeters = SKIP_NEAR_DESTINATION_METERS,
                maxAnchors = MAX_COVERAGE_ANCHORS,
            )
        }
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
            } catch (budget: PlacesBudgetExhaustedException) {
                throw budget
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
            } catch (budget: PlacesBudgetExhaustedException) {
                throw budget
            } catch (_: Exception) {
                diagnostics.increment(RouteSearchDiagnostics.Counter.PARTIAL_FAILURES)
                null
            }
        } else {
            null
        }
        val hasInitialPlaces = routePlaces.orEmpty().isNotEmpty() ||
            destinationPlaces.orEmpty().isNotEmpty()
        val usedFullFallback = routeInfo.encodedPolyline.isBlank() || !hasInitialPlaces

        val discoveries = if (!usedFullFallback) {
            val destinationGroup = coverageAnchors.size
            val destinationDiscovery = AnchorDiscovery(
                anchorIndex = destinationGroup,
                places = destinationPlaces,
            )
            val routeDiscoveries = routePlaces.orEmpty()
                .groupBy { place ->
                    nearestCoverageGroup(
                        place = place,
                        routePoints = routeInfo.decodedPoints,
                        coverageAnchors = coverageAnchors,
                    )
                }
                .map { (anchorIndex, places) -> AnchorDiscovery(anchorIndex, places) }

            // Destination results come first so a duplicate found by both APIs remains assigned to
            // the destination group rather than consuming a corridor group's reserved slot.
            val initialDiscoveries = listOf(destinationDiscovery) + routeDiscoveries
            val representedCorridorGroups = initialDiscoveries
                .flatMap { discovery ->
                    discovery.places.orEmpty().map { place ->
                        AnchoredPlace(place, discovery.anchorIndex)
                    }
                }
                .distinctBy { it.place.placeId }
                .filter { candidate -> matchesCandidateFilters(candidate.place, filters) }
                .mapTo(mutableSetOf(), AnchoredPlace::anchorIndex)
            val missingAnchors = coverageAnchors
                .mapIndexedNotNull { anchorIndex, anchor ->
                    anchor.takeIf { anchorIndex !in representedCorridorGroups }
                        ?.let { anchorIndex to it }
                }
                .take(MAX_SUPPLEMENTAL_ANCHORS)

            val supplementalDiscoveries = if (missingAnchors.isEmpty()) {
                emptyList()
            } else {
                var completedSearches = 2
                val totalSearches = 2 + missingAnchors.size
                val progressLock = Any()
                val discoverySemaphore = Semaphore(MAX_CONCURRENT_DISCOVERY_CALLS)
                onProgress(RouteSearchProgress.SearchingStops(completedSearches, totalSearches))

                diagnostics.measure(RouteSearchDiagnostics.Stage.SEARCH_DISCOVERY) {
                    missingAnchors.map { (anchorIndex, anchor) ->
                        async {
                            discoverySemaphore.withPermit {
                                diagnostics.increment(
                                    RouteSearchDiagnostics.Counter.WAYPOINTS_ATTEMPTED,
                                )
                                diagnostics.increment(
                                    RouteSearchDiagnostics.Counter.NEARBY_REPOSITORY_CALLS,
                                )
                                val places = searchAnchor(
                                    anchor = anchor.point,
                                    placeTypes = placeTypes,
                                    diagnostics = diagnostics,
                                )
                                if (places != null) {
                                    diagnostics.increment(
                                        RouteSearchDiagnostics.Counter.WAYPOINTS_SUCCEEDED,
                                    )
                                }
                                synchronized(progressLock) {
                                    completedSearches++
                                    onProgress(
                                        RouteSearchProgress.SearchingStops(
                                            completedSearches,
                                            totalSearches,
                                        ),
                                    )
                                }
                                AnchorDiscovery(anchorIndex, places)
                            }
                        }
                    }.awaitAll()
                }
            }
            val gapDiscoveries = fillRouteGaps(
                routeInfo = routeInfo,
                currentRouteIndex = currentRouteIndex,
                knownPlaces = (initialDiscoveries + supplementalDiscoveries)
                    .flatMap { it.places.orEmpty() },
                groupOffset = coverageAnchors.size + 1,
                placeTypes = placeTypes,
                filters = filters,
                diagnostics = diagnostics,
            )
            initialDiscoveries + supplementalDiscoveries + gapDiscoveries
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
                            val places = searchAnchor(
                                anchor = anchor.point,
                                placeTypes = placeTypes,
                                diagnostics = diagnostics,
                            )

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
                maxCandidatesPerGroup = if (usedFullFallback) {
                    MAX_CANDIDATES_PER_ANCHOR
                } else {
                    MAX_MATRIX_CANDIDATES
                },
                excludeNearDestination = usedFullFallback,
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
            if (isTooCloseToStart(verified.reachSeconds, baselineSeconds)) {
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

        selectSpreadAcrossRoute(
            eligible = eligible,
            currentRouteIndex = currentRouteIndex,
            lastRouteIndex = routeInfo.decodedPoints.lastIndex,
            remainingSeconds = baselineSeconds,
        )
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
            matchesCandidateFilters(place, filters) &&
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

    /**
     * Long routes are split into time slots; any slot ahead of the traveller that has no usable
     * candidate gets one wider search around its midpoint. Midpoints depend only on the route, so
     * repeat searches hit the nearby-results cache instead of spending quota.
     */
    private suspend fun fillRouteGaps(
        routeInfo: RouteInfo,
        currentRouteIndex: Int,
        knownPlaces: List<Place>,
        groupOffset: Int,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
        diagnostics: RouteSearchDiagnostics,
    ): List<AnchorDiscovery> {
        val points = routeInfo.decodedPoints
        val lastIndex = points.lastIndex
        val slotCount = routeSlotCount(routeInfo.durationSeconds)
        if (slotCount < 2 || lastIndex <= 0) return emptyList()

        val coveredSlots = knownPlaces
            .filter { matchesCandidateFilters(it, filters) }
            .mapTo(mutableSetOf()) { place ->
                routeSlot(
                    PolylineUtils.findNearestPointIndex(points, LatLng(place.latitude, place.longitude)),
                    0,
                    lastIndex,
                    slotCount,
                )
            }
        val destination = points.last()
        val gaps = (0 until slotCount)
            .filter { it !in coveredSlots }
            .map { slot -> slot to points[((slot + 0.5) / slotCount * lastIndex).toInt()] }
            .filter { (_, midpoint) ->
                PolylineUtils.findNearestPointIndex(points, midpoint) > currentRouteIndex &&
                    PolylineUtils.haversineDistance(midpoint, destination) >
                    DESTINATION_SEARCH_RADIUS_METERS
            }
            .take(MAX_GAP_FILL_SEARCHES)
        if (gaps.isEmpty()) return emptyList()

        diagnostics.increment(RouteSearchDiagnostics.Counter.WAYPOINTS_ATTEMPTED, gaps.size)
        diagnostics.increment(RouteSearchDiagnostics.Counter.NEARBY_REPOSITORY_CALLS, gaps.size)
        return coroutineScope {
            gaps.map { (slot, midpoint) ->
                async {
                    AnchorDiscovery(
                        anchorIndex = groupOffset + slot,
                        places = searchAnchor(
                            anchor = midpoint,
                            placeTypes = placeTypes,
                            diagnostics = diagnostics,
                            radiusMeters = GAP_FILL_RADIUS_METERS,
                        ),
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun searchAnchor(
        anchor: LatLng,
        placeTypes: List<String>,
        diagnostics: RouteSearchDiagnostics,
        radiusMeters: Double = SEARCH_RADIUS_METERS,
    ): List<Place>? = try {
        withTimeoutOrNull(ANCHOR_SEARCH_TIMEOUT_MS) {
            repository.searchNearbyPlaces(
                latitude = anchor.latitude,
                longitude = anchor.longitude,
                radiusMeters = radiusMeters,
                placeTypes = placeTypes,
            )
        } ?: throw AnchorSearchTimeoutException()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (budget: PlacesBudgetExhaustedException) {
        throw budget
    } catch (_: Exception) {
        diagnostics.increment(RouteSearchDiagnostics.Counter.PARTIAL_FAILURES)
        null
    }

    private fun matchesCandidateFilters(place: Place, filters: RouteFilterSettings): Boolean =
        place.userRatingCount >= MIN_REVIEW_COUNT &&
            matchesOpenNowFilter(place.isOpen, filters.openNowOnly) &&
            (filters.maxPriceLevel == null || place.priceLevel == null ||
                place.priceLevel <= filters.maxPriceLevel)

    private fun nearestCoverageGroup(
        place: Place,
        routePoints: List<LatLng>,
        coverageAnchors: List<PolylineUtils.RouteAnchor>,
    ): Int {
        if (coverageAnchors.isEmpty()) return 0
        val nearestRouteIndex = PolylineUtils.findNearestPointIndex(
            routePoints,
            LatLng(place.latitude, place.longitude),
        )
        return coverageAnchors.indices.minBy { anchorIndex ->
            kotlin.math.abs(coverageAnchors[anchorIndex].routeIndex - nearestRouteIndex)
        }
    }

    private fun selectSpreadAcrossRoute(
        eligible: List<AlongRoutePlace>,
        currentRouteIndex: Int,
        lastRouteIndex: Int,
        remainingSeconds: Int,
    ): List<AlongRoutePlace> {
        val slotCount = routeSlotCount(remainingSeconds)
        val maxPerSlot = maxOf(MIN_RESULTS_PER_SLOT, ceilDiv(MAX_RESULTS, slotCount))
        val slots = eligible
            .sortedWith(resultScoreComparator)
            .groupBy { result ->
                routeSlot(result.nearestRoutePointIndex, currentRouteIndex, lastRouteIndex, slotCount)
            }
            .toSortedMap()
            .values
            .map { slot -> slot.take(maxPerSlot) }

        // Round-robin so every part of the route gets its best stop before any part gets a second.
        return buildList {
            for (rank in 0 until maxPerSlot) {
                for (slot in slots) {
                    slot.getOrNull(rank)?.let(::add)
                    if (size == MAX_RESULTS) return@buildList
                }
            }
        }.sortedWith(resultScoreComparator)
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
        const val MIN_RESULTS_PER_SLOT = 2
        const val MAX_ROUTE_SLOTS = 8
        const val ROUTE_SLOT_SECONDS = 5_400
        const val LONG_TRIP_SECONDS = 3_600
        const val MIN_REACH_SECONDS_ON_LONG_TRIP = 600
        const val SEARCH_RADIUS_METERS = 2000.0
        const val DESTINATION_SEARCH_RADIUS_METERS = 15_000.0
        const val SKIP_NEAR_DESTINATION_METERS = 3000.0
        const val MAX_SEARCH_ANCHORS = 8
        const val MAX_COVERAGE_ANCHORS = 4
        const val MAX_GAP_FILL_SEARCHES = 4
        const val GAP_FILL_RADIUS_METERS = 12_000.0
        const val MAX_SUPPLEMENTAL_ANCHORS = 4
        const val MAX_CONCURRENT_DISCOVERY_CALLS = 3
        const val MAX_CANDIDATES_PER_ANCHOR = 3
        const val MAX_MATRIX_CANDIDATES = 24
        const val SEARCH_TIMEOUT_MS = 30_000L
        const val ANCHOR_SEARCH_TIMEOUT_MS = 8_000L
        const val ROUTE_SEARCH_TIMEOUT_MS = 12_000L
        private const val PRIOR_RATING = 4.0
        private const val PRIOR_WEIGHT_REVIEWS = 200.0
        private const val POPULARITY_WEIGHT = 0.25
        private const val MAX_DETOUR_PENALTY = 0.3

        private val candidateQualityComparator =
            compareByDescending<AnchoredPlace> {
                smoothedRating(it.place.rating, it.place.userRatingCount)
            }
                .thenByDescending { it.place.userRatingCount }
                .thenBy { it.place.placeId }

        private val resultScoreComparator =
            compareByDescending<AlongRoutePlace> { it.score }
                .thenByDescending { it.place.userRatingCount }
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

        /** Rating pulled toward [PRIOR_RATING] until a place has many reviews behind it. */
        fun smoothedRating(rating: Double, reviewCount: Int): Double =
            (reviewCount * rating + PRIOR_WEIGHT_REVIEWS * PRIOR_RATING) /
                (reviewCount + PRIOR_WEIGHT_REVIEWS)

        fun computeScore(
            rating: Double,
            reviewCount: Int,
            detourMinutes: Double,
            maxDetourMinutes: Int,
        ): Double {
            val popularityBonus = POPULARITY_WEIGHT * log10(reviewCount.coerceAtLeast(1).toDouble())
            val effectiveMaxDetour = maxDetourMinutes.coerceAtLeast(1)
            val detourPenalty = MAX_DETOUR_PENALTY *
                (detourMinutes / effectiveMaxDetour).coerceIn(0.0, 1.0)
            return smoothedRating(rating, reviewCount) + popularityBonus - detourPenalty
        }

        /** Stops a few minutes from the start of a long trip are not meaningful meal stops. */
        fun isTooCloseToStart(reachSeconds: Int, remainingSeconds: Int): Boolean =
            remainingSeconds >= LONG_TRIP_SECONDS && reachSeconds < MIN_REACH_SECONDS_ON_LONG_TRIP

        fun routeSlotCount(remainingSeconds: Int): Int =
            ceilDiv(remainingSeconds.coerceAtLeast(1), ROUTE_SLOT_SECONDS)
                .coerceIn(1, MAX_ROUTE_SLOTS)

        fun routeSlot(
            nearestRouteIndex: Int,
            currentRouteIndex: Int,
            lastRouteIndex: Int,
            slotCount: Int,
        ): Int {
            val span = lastRouteIndex - currentRouteIndex
            if (span <= 0) return 0
            val fraction = ((nearestRouteIndex - currentRouteIndex).toDouble() / span)
                .coerceIn(0.0, 1.0)
            return (fraction * slotCount).toInt().coerceAtMost(slotCount - 1)
        }
    }
}

private val TransportMode.anchorIntervalMeters: Double
    get() = when (this) {
        TransportMode.DRIVE -> 12_000.0
        TransportMode.WALK -> 1_500.0
        TransportMode.BICYCLE -> 3_000.0
    }

private fun ceilDiv(dividend: Int, divisor: Int): Int = (dividend + divisor - 1) / divisor

private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

private fun Place.toGeoPoint() = GeoPoint(latitude, longitude)
