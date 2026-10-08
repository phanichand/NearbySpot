package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.SortOption
import com.nearbyspot.domain.model.TravelInfo
import javax.inject.Inject
import kotlin.math.ln

class RankPlacesUseCase @Inject constructor() {

    operator fun invoke(
        places: List<Place>,
        travelInfos: List<TravelInfo>,
        filters: FilterSettings,
    ): List<RankedPlace> {
        val travelMap = travelInfos.associateBy { it.placeId }

        return places
            .mapNotNull { place ->
                if (place.userRatingCount < MIN_REVIEW_COUNT) return@mapNotNull null
                if (filters.openNowOnly && place.isOpen == false) return@mapNotNull null

                val travel = travelMap[place.placeId] ?: return@mapNotNull null
                val travelMinutes = travel.durationMinutes

                if (travelMinutes > filters.travelTimeMinutes) return@mapNotNull null
                if (filters.maxPriceLevel != null && place.priceLevel != null
                    && place.priceLevel > filters.maxPriceLevel
                ) return@mapNotNull null

                val score = computeScore(
                    rating = place.rating,
                    reviewCount = place.userRatingCount,
                    travelMinutes = travelMinutes,
                    maxMinutes = filters.travelTimeMinutes,
                )
                RankedPlace(place = place, travelInfo = travel, score = score)
            }
            .let { list ->
                when (filters.sortBy) {
                    SortOption.SMART -> list.sortedByDescending { it.score }
                    SortOption.RATING -> list.sortedByDescending { it.place.rating }
                    SortOption.REVIEW_COUNT -> list.sortedByDescending { it.place.userRatingCount }
                    SortOption.DISTANCE -> list.sortedBy { it.travelInfo.durationSeconds }
                }
            }
            .take(MAX_RESULTS)
    }

    companion object {
        const val MIN_REVIEW_COUNT = 50
        const val MAX_RESULTS = 5
        const val MIN_RATING = 3.5

        /**
         * Popularity-first scoring:
         *   score = reviewCount^0.6 * ratingBoost * proximityBoost
         *
         * - reviewCount^0.6: primary signal. Sublinear so 10000 reviews isn't
         *   100x better than 100, but a place with 3000 reviews clearly beats
         *   one with 80. Power of 0.6 keeps the spread meaningful.
         * - ratingBoost: only kicks in above 4.0 (normalized 0-1 from the
         *   3.5-5.0 range). A 4.5 place gets a 67% boost, a 3.5 gets 0%.
         *   This rewards quality but doesn't let a 4.8 with 60 reviews beat
         *   a 4.2 with 3000 reviews.
         * - proximityBoost: mild preference for closer places (keeps 60% even
         *   at max distance so a truly popular distant place still wins).
         */
        fun computeScore(
            rating: Double,
            reviewCount: Int,
            travelMinutes: Double,
            maxMinutes: Int,
        ): Double {
            val popularityBase = Math.pow(reviewCount.toDouble(), 0.6)
            val ratingBoost = 1.0 + ((rating - MIN_RATING) / (5.0 - MIN_RATING)).coerceIn(0.0, 1.0)
            val proximityBoost = 0.6 + 0.4 * (1.0 - travelMinutes / maxMinutes.toDouble())
            return popularityBase * ratingBoost * proximityBoost
        }
    }
}
