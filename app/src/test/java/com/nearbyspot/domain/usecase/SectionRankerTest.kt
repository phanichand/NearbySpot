package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRankerTest {

    private fun candidate(
        id: String,
        rating: Double,
        reachMinutes: Int,
        reviewCount: Int = 1_000,
    ) = AlongRoutePlace(
        place = Place(
            placeId = id,
            name = id,
            address = "",
            latitude = 0.0,
            longitude = 0.0,
            rating = rating,
            userRatingCount = reviewCount,
            priceLevel = null,
            isOpen = true,
            photoRefs = emptyList(),
            reviews = emptyList(),
            types = emptyList(),
        ),
        reachableSeconds = reachMinutes * 60,
        detourSeconds = 0,
        nearestRoutePointIndex = 0,
        score = 0.0,
    )

    private val strongRecency = RecencySignal(recentCount = 5, recentPositiveCount = 5)

    @Test
    fun `a 4_6 place at 30 minutes beats a 3_9 place at 5 minutes`() {
        val picks = SectionRanker.rank(
            candidates = listOf(candidate("worse-near", 3.9, 5), candidate("better-far", 4.6, 30)),
            recency = emptyMap(),
        )

        assertEquals("better-far", picks.first().candidate.place.placeId)
    }

    @Test
    fun `a slightly better place 40 minutes out does not beat a good one at 15 minutes`() {
        val picks = SectionRanker.rank(
            candidates = listOf(
                candidate("near-good", 4.2, 15, reviewCount = 300),
                candidate("far-famous", 4.5, 40, reviewCount = 20_000),
            ),
            recency = emptyMap(),
        )

        assertEquals("near-good", picks.first().candidate.place.placeId)
    }

    @Test
    fun `a 4_8 place at 30 minutes still beats a 3_6 place at 10 minutes`() {
        val picks = SectionRanker.rank(
            candidates = listOf(candidate("poor-near", 3.6, 10), candidate("great-far", 4.8, 30)),
            recency = emptyMap(),
        )

        assertEquals("great-far", picks.first().candidate.place.placeId)
    }

    @Test
    fun `a candidate at 46 minutes is excluded and one at 45 is kept`() {
        val picks = SectionRanker.rank(
            candidates = listOf(candidate("too-far", 4.9, 46), candidate("limit", 4.0, 45)),
            recency = emptyMap(),
        )

        assertEquals(listOf("limit"), picks.map { it.candidate.place.placeId })
    }

    @Test
    fun `one place winning both gets both kinds and appears once`() {
        val picks = SectionRanker.rank(
            candidates = listOf(candidate("winner", 4.8, 15), candidate("runner-up", 4.2, 20)),
            recency = mapOf("winner" to strongRecency, "runner-up" to strongRecency),
        )

        assertEquals(1, picks.count { it.candidate.place.placeId == "winner" })
        assertEquals(setOf(PickKind.POPULAR, PickKind.TRENDING), picks.first().kinds)
        assertEquals("runner-up", picks[1].candidate.place.placeId)
        assertEquals(2, picks.size)
    }

    @Test
    fun `separate winners are labelled Popular and Trending`() {
        val picks = SectionRanker.rank(
            candidates = listOf(
                candidate("popular", 4.7, 15, reviewCount = 20_000),
                candidate("trending", 4.3, 15, reviewCount = 300),
            ),
            recency = mapOf(
                "popular" to RecencySignal(recentCount = 5, recentPositiveCount = 2),
                "trending" to strongRecency,
            ),
        )

        assertEquals(setOf(PickKind.POPULAR), picks[0].kinds)
        assertEquals("popular", picks[0].candidate.place.placeId)
        assertEquals(setOf(PickKind.TRENDING), picks[1].kinds)
        assertEquals("trending", picks[1].candidate.place.placeId)
    }

    @Test
    fun `no recency data returns the Popular pick only`() {
        val picks = SectionRanker.rank(
            candidates = listOf(candidate("a", 4.5, 15), candidate("b", 4.4, 20)),
            recency = mapOf("a" to null, "b" to null),
        )

        assertEquals(1, picks.size)
        assertEquals(setOf(PickKind.POPULAR), picks.single().kinds)
    }

    @Test
    fun `empty candidates returns an empty list`() {
        assertTrue(SectionRanker.rank(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `extra minutes and stars are measured against the nearest acceptable pick`() {
        val picks = SectionRanker.rank(
            candidates = listOf(
                candidate("nearest", 4.0, 10, reviewCount = 500),
                candidate("worth-the-wait", 4.5, 32, reviewCount = 5_000),
            ),
            recency = emptyMap(),
        )
        val pick = picks.first { it.candidate.place.placeId == "worth-the-wait" }
        val baseline = picks.firstOrNull { it.candidate.place.placeId == "nearest" }

        assertEquals(22, pick.extraMinutesVsBaseline)
        assertEquals(0.5, pick.extraStarsVsBaseline, 1e-9)
        assertTrue(baseline == null || baseline.extraMinutesVsBaseline == 0)
    }

    @Test
    fun `time adjusted score falls as reach moves away from 15 minutes`() {
        val atTarget = SectionRanker.timeAdjustedScore(quality = 4.0, reachMinutes = 15.0)

        assertTrue(SectionRanker.timeAdjustedScore(4.0, 30.0) < atTarget)
        assertTrue(SectionRanker.timeAdjustedScore(4.0, 5.0) < atTarget)
    }
}
