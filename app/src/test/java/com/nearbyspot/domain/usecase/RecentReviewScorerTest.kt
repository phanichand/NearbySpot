package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentReviewScorerTest {

    private val nowMillis = 1_800_000_000_000L

    private fun review(rating: Double, ageDays: Int?) = Review(
        authorName = "a",
        rating = rating,
        text = "",
        relativeTimeDescription = "",
        publishedAtEpochMillis = ageDays?.let { nowMillis - it * DAY_MILLIS },
    )

    @Test
    fun `ignores reviews older than 180 days`() {
        val reviews = List(3) { review(5.0, ageDays = 10) } + List(5) { review(1.0, ageDays = 181) }

        val signal = RecentReviewScorer.signal(reviews, nowMillis)

        assertEquals(RecencySignal(recentCount = 3, recentPositiveCount = 3), signal)
    }

    @Test
    fun `a review exactly 180 days old still counts as recent`() {
        val reviews = List(3) { review(4.0, ageDays = 180) }

        assertEquals(3, RecentReviewScorer.signal(reviews, nowMillis)?.recentCount)
    }

    @Test
    fun `returns null with fewer than 3 recent reviews`() {
        val reviews = List(2) { review(5.0, ageDays = 5) }

        assertNull(RecentReviewScorer.signal(reviews, nowMillis))
    }

    @Test
    fun `reviews without a date are ignored`() {
        val reviews = List(3) { review(5.0, ageDays = null) } + List(2) { review(5.0, ageDays = 5) }

        assertNull(RecentReviewScorer.signal(reviews, nowMillis))
    }

    @Test
    fun `empty list returns null`() {
        assertNull(RecentReviewScorer.signal(emptyList(), nowMillis))
    }

    @Test
    fun `rating of 4 counts as positive and 3_9 does not`() {
        val reviews = listOf(review(4.0, 1), review(3.9, 1), review(5.0, 1))

        assertEquals(2, RecentReviewScorer.signal(reviews, nowMillis)?.recentPositiveCount)
    }

    @Test
    fun `18 of 20 recent positive scores 0_9`() {
        val signal = RecencySignal(recentCount = 20, recentPositiveCount = 18)

        assertEquals(0.9, RecentReviewScorer.trendingScore(signal), 1e-9)
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
