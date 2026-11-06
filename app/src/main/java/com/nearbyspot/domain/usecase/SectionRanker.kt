package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.SectionPick
import kotlin.math.roundToInt

/** Picks the best stops in a section, trading how long they take to reach against how good they are. */
object SectionRanker {
    const val TARGET_REACH_MINUTES = 15
    const val MAX_REACH_MINUTES = 45
    private const val EARLY_PENALTY_PER_MINUTE = 0.005
    private const val LATE_PENALTY_PER_SQUARED_MINUTE = 0.002
    private const val ACCEPTABLE_RATING_GAP = 0.5

    /**
     * Arriving early costs almost nothing, while each minute past the 15-minute target costs more
     * than the one before: a few extra minutes are cheap, 25 extra need a clearly better place.
     */
    fun timeAdjustedScore(quality: Double, reachMinutes: Double): Double {
        val early = (TARGET_REACH_MINUTES - reachMinutes).coerceAtLeast(0.0)
        val late = (reachMinutes - TARGET_REACH_MINUTES).coerceAtLeast(0.0)
        return quality - EARLY_PENALTY_PER_MINUTE * early - LATE_PENALTY_PER_SQUARED_MINUTE * late * late
    }

    /**
     * Returns at most two picks: the best overall ("Popular") and the one with the strongest recent
     * reviews ("Trending"). A place winning both appears once with both kinds, and the second slot
     * goes to the next best Trending candidate, or the next best overall when there is none.
     */
    fun rank(
        candidates: List<AlongRoutePlace>,
        recency: Map<String, RecencySignal?>,
    ): List<SectionPick> {
        val eligible = candidates.filter { it.reachableMinutes <= MAX_REACH_MINUTES }
        if (eligible.isEmpty()) return emptyList()

        val byOverallScore = eligible.sortedWith(
            compareByDescending<AlongRoutePlace> { adjustedScore(it) }
                .thenByDescending { it.place.userRatingCount }
                .thenBy { it.place.placeId },
        )
        val byRecentSentiment = eligible
            .filter { recency[it.place.placeId] != null }
            .sortedWith(
                compareByDescending<AlongRoutePlace> {
                    RecentReviewScorer.trendingScore(recency.getValue(it.place.placeId)!!)
                }
                    .thenByDescending { adjustedScore(it) }
                    .thenBy { it.place.placeId },
            )

        val popular = byOverallScore.first()
        val trending = byRecentSentiment.firstOrNull()
        val baseline = baselineOf(eligible)

        fun pick(candidate: AlongRoutePlace, kinds: Set<PickKind>) = SectionPick(
            candidate = candidate,
            kinds = kinds,
            extraMinutesVsBaseline = (
                (candidate.reachableSeconds - baseline.reachableSeconds) / 60.0
                ).roundToInt().coerceAtLeast(0),
            extraStarsVsBaseline = candidate.place.rating - baseline.place.rating,
            recencySignal = recency[candidate.place.placeId],
        )

        return when {
            trending == null -> listOf(pick(popular, setOf(PickKind.POPULAR)))
            trending.place.placeId != popular.place.placeId -> listOf(
                pick(popular, setOf(PickKind.POPULAR)),
                pick(trending, setOf(PickKind.TRENDING)),
            )
            else -> {
                val bothKinds = pick(popular, setOf(PickKind.POPULAR, PickKind.TRENDING))
                val nextTrending = byRecentSentiment.firstOrNull { it.place.placeId != popular.place.placeId }
                val nextOverall = byOverallScore.firstOrNull { it.place.placeId != popular.place.placeId }
                listOfNotNull(
                    bothKinds,
                    nextTrending?.let { pick(it, setOf(PickKind.TRENDING)) }
                        ?: nextOverall?.let { pick(it, setOf(PickKind.POPULAR)) },
                )
            }
        }
    }

    fun adjustedScore(candidate: AlongRoutePlace): Double = timeAdjustedScore(
        quality = quality(candidate),
        reachMinutes = candidate.reachableMinutes,
    )

    private fun quality(candidate: AlongRoutePlace): Double =
        GetAlongRoutePlacesUseCase.computeScore(
            rating = candidate.place.rating,
            reviewCount = candidate.place.userRatingCount,
            detourMinutes = 0.0,
            maxDetourMinutes = 1,
        )

    /** The nearest place whose rating is within reach of the best one: what "no extra wait" looks like. */
    private fun baselineOf(eligible: List<AlongRoutePlace>): AlongRoutePlace {
        val bestSmoothed = eligible.maxOf { smoothed(it) }
        return eligible
            .filter { smoothed(it) >= bestSmoothed - ACCEPTABLE_RATING_GAP }
            .minWith(compareBy<AlongRoutePlace> { it.reachableSeconds }.thenBy { it.place.placeId })
    }

    private fun smoothed(candidate: AlongRoutePlace): Double =
        GetAlongRoutePlacesUseCase.smoothedRating(candidate.place.rating, candidate.place.userRatingCount)
}
