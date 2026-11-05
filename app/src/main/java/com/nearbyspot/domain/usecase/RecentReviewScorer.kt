package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Review

/** How many recent reviews a place has and how many of them are positive. */
data class RecencySignal(
    val recentCount: Int,
    val recentPositiveCount: Int,
)

object RecentReviewScorer {
    const val WINDOW_DAYS = 180
    const val MIN_RECENT_REVIEWS = 3
    private const val POSITIVE_RATING = 4.0
    private const val DAY_MILLIS = 86_400_000L

    /** Null when fewer than [MIN_RECENT_REVIEWS] dated reviews fall inside the recency window. */
    fun signal(reviews: List<Review>, nowMillis: Long): RecencySignal? {
        val cutoffMillis = nowMillis - WINDOW_DAYS * DAY_MILLIS
        val recent = reviews.filter { review ->
            review.publishedAtEpochMillis?.let { it >= cutoffMillis } == true
        }
        if (recent.size < MIN_RECENT_REVIEWS) return null
        return RecencySignal(
            recentCount = recent.size,
            recentPositiveCount = recent.count { it.rating >= POSITIVE_RATING },
        )
    }

    /** Share of recent reviews that are positive, from 0.0 to 1.0. */
    fun trendingScore(signal: RecencySignal): Double =
        signal.recentPositiveCount.toDouble() / signal.recentCount.coerceAtLeast(1)
}
