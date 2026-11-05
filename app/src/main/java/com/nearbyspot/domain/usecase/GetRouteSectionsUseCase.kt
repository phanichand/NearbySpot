package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
import com.nearbyspot.domain.model.Review
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSearchException
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionNote
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.domain.repository.PlaceRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Loads one section of the drive: finds candidates on that stretch with the existing along-route
 * search, checks recent reviews for a few finalists, and ranks Popular and Trending picks.
 */
class GetRouteSectionsUseCase(
    private val alongRoute: GetAlongRoutePlacesUseCase,
    private val repository: PlaceRepository,
    private val clock: () -> Long,
) {
    private val reviewCache = ConcurrentHashMap<String, List<Review>>()

    @Inject
    constructor(
        alongRoute: GetAlongRoutePlacesUseCase,
        repository: PlaceRepository,
    ) : this(alongRoute, repository, System::currentTimeMillis)

    suspend operator fun invoke(
        routeInfo: RouteInfo,
        section: RouteSection,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
    ): RouteSection {
        val points = routeInfo.decodedPoints
        val sectionPoints = points.subList(section.startRouteIndex, section.endRouteIndex + 1)
        // A blank polyline makes the along-route search use its anchor-based path, which suits a slice.
        val slice = RouteInfo(
            encodedPolyline = "",
            durationSeconds = (routeInfo.durationSeconds.toLong() * sectionPoints.size / points.size).toInt(),
            distanceMeters = (routeInfo.distanceMeters.toLong() * sectionPoints.size / points.size).toInt(),
            decodedPoints = sectionPoints,
        )

        var budgetLimited = false
        val candidates = try {
            alongRoute(
                routeInfo = slice,
                currentLatLng = sectionPoints.first(),
                currentRouteIndex = 0,
                placeTypes = placeTypes,
                filters = filters,
                skipTooCloseToStart = true,
                onBudgetLimited = { budgetLimited = true },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: RouteSearchException) {
            return section.copy(
                status = SectionStatus.FAILED,
                picks = emptyList(),
                note = SectionNote.BUDGET_LIMITED.takeIf { error.failure.isBudgetLimit() },
                failure = error.failure,
            )
        }

        // Slice positions are re-based to the full route so later steps can tell where a pick lies.
        val reachable = candidates
            .map { it.copy(nearestRoutePointIndex = it.nearestRoutePointIndex + section.startRouteIndex) }
            .filter { it.reachableMinutes <= SectionRanker.MAX_REACH_MINUTES }
        if (reachable.isEmpty()) {
            return section.copy(status = SectionStatus.NO_QUALIFYING_PLACE, picks = emptyList(), note = null, failure = null)
        }

        val (recency, reviewsFailed) = recencyForFinalists(reachable)
        val picks = SectionRanker.rank(reachable, recency).map {
            it.copy(timeAheadSeconds = section.aheadSeconds + it.candidate.reachableSeconds)
        }
        return section.copy(
            status = SectionStatus.LOADED,
            picks = picks,
            note = when {
                budgetLimited -> SectionNote.BUDGET_LIMITED
                reviewsFailed -> SectionNote.POPULAR_ONLY
                else -> null
            },
            failure = null,
        )
    }

    private suspend fun recencyForFinalists(
        reachable: List<AlongRoutePlace>,
    ): Pair<Map<String, RecencySignal?>, Boolean> {
        val finalists = reachable
            .sortedWith(
                compareByDescending<AlongRoutePlace> { SectionRanker.adjustedScore(it) }
                    .thenBy { it.place.placeId },
            )
            .take(MAX_FINALISTS)
        val outcomes = coroutineScope {
            finalists.map { finalist ->
                async { finalist.place.placeId to fetchReviewsOrNull(finalist.place.placeId) }
            }.awaitAll()
        }
        val recency = outcomes.associate { (placeId, reviews) ->
            placeId to reviews?.let { RecentReviewScorer.signal(it, clock()) }
        }
        return recency to outcomes.any { (_, reviews) -> reviews == null }
    }

    /** Forgets cached reviews; call when a new trip starts. */
    fun clearTripCache() = reviewCache.clear()

    private suspend fun fetchReviewsOrNull(placeId: String): List<Review>? = try {
        reviewCache[placeId] ?: repository.fetchReviews(placeId).also { reviewCache[placeId] = it }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: PlacesBudgetExhaustedException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun RouteSearchFailure.isBudgetLimit() =
        this == RouteSearchFailure.MONTHLY_LIMIT_REACHED || this == RouteSearchFailure.TRIP_LIMIT_REACHED

    private companion object {
        const val MAX_FINALISTS = 3
    }
}
