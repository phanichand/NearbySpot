package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.model.TravelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.ln

class RankPlacesUseCaseTest {

    private lateinit var useCase: RankPlacesUseCase

    @Before
    fun setup() {
        useCase = RankPlacesUseCase()
    }

    private fun createPlace(
        id: String,
        rating: Double = 4.0,
        reviewCount: Int = 100,
        priceLevel: Int? = 2,
        isOpen: Boolean? = true,
    ) = Place(
        placeId = id,
        name = "Place $id",
        address = "Address $id",
        latitude = 0.0,
        longitude = 0.0,
        rating = rating,
        userRatingCount = reviewCount,
        priceLevel = priceLevel,
        isOpen = isOpen,
        photoRefs = emptyList(),
        reviews = emptyList(),
        types = listOf("restaurant"),
    )

    private fun createTravel(placeId: String, durationSeconds: Int) = TravelInfo(
        placeId = placeId,
        durationSeconds = durationSeconds,
        distanceMeters = 500,
    )

    @Test
    fun `places outside travel time are filtered out`() {
        val places = listOf(createPlace("a"), createPlace("b"))
        val travels = listOf(
            createTravel("a", 300),  // 5 min
            createTravel("b", 900),  // 15 min
        )
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertEquals(1, result.size)
        assertEquals("a", result[0].place.placeId)
    }

    @Test
    fun `higher rated place with more reviews ranks first`() {
        val places = listOf(
            createPlace("a", rating = 4.5, reviewCount = 2000),
            createPlace("b", rating = 3.5, reviewCount = 200),
        )
        val travels = listOf(
            createTravel("a", 300),
            createTravel("b", 180),
        )
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertEquals(2, result.size)
        assertEquals("a", result[0].place.placeId)
        assertTrue(result[0].score > result[1].score)
    }

    @Test
    fun `places below minimum review count are filtered out`() {
        val places = listOf(
            createPlace("popular", reviewCount = 500),
            createPlace("unpopular", reviewCount = 10),
        )
        val travels = listOf(
            createTravel("popular", 300),
            createTravel("unpopular", 300),
        )
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertEquals(1, result.size)
        assertEquals("popular", result[0].place.placeId)
    }

    @Test
    fun `results are capped at 5`() {
        val places = (1..10).map { createPlace("p$it", reviewCount = 200) }
        val travels = (1..10).map { createTravel("p$it", 300) }
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertEquals(5, result.size)
    }

    @Test
    fun `closer place gets higher score when rating and reviews are equal`() {
        val places = listOf(
            createPlace("far", rating = 4.0, reviewCount = 100),
            createPlace("near", rating = 4.0, reviewCount = 100),
        )
        val travels = listOf(
            createTravel("far", 540),   // 9 min
            createTravel("near", 120),  // 2 min
        )
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertEquals("near", result[0].place.placeId)
    }

    @Test
    fun `price filter excludes expensive places`() {
        val places = listOf(
            createPlace("cheap", priceLevel = 1),
            createPlace("expensive", priceLevel = 4),
        )
        val travels = listOf(
            createTravel("cheap", 300),
            createTravel("expensive", 300),
        )
        val filters = FilterSettings(
            travelTimeMinutes = 10,
            transportMode = TransportMode.WALK,
            maxPriceLevel = 2,
        )

        val result = useCase(places, travels, filters)

        assertEquals(1, result.size)
        assertEquals("cheap", result[0].place.placeId)
    }

    @Test
    fun `null price filter includes all price levels`() {
        val places = listOf(
            createPlace("a", priceLevel = 1),
            createPlace("b", priceLevel = 4),
            createPlace("c", priceLevel = null),
        )
        val travels = listOf(
            createTravel("a", 300),
            createTravel("b", 300),
            createTravel("c", 300),
        )
        val filters = FilterSettings(
            travelTimeMinutes = 10,
            transportMode = TransportMode.WALK,
            maxPriceLevel = null,
        )

        val result = useCase(places, travels, filters)

        assertEquals(3, result.size)
    }

    @Test
    fun `open now filter only includes places confirmed open`() {
        val places = listOf(
            createPlace("open", isOpen = true),
            createPlace("closed", isOpen = false),
            createPlace("unknown", isOpen = null),
        )
        val travels = places.map { createTravel(it.placeId, 300) }

        val result = useCase(
            places,
            travels,
            FilterSettings(openNowOnly = true),
        )

        assertEquals(listOf("open"), result.map { it.place.placeId })
    }

    @Test
    fun `disabled open now filter includes unknown and closed status`() {
        assertTrue(matchesOpenNowFilter(isOpen = null, openNowOnly = false))
        assertTrue(matchesOpenNowFilter(isOpen = false, openNowOnly = false))
    }

    @Test
    fun `place with many reviews beats place with higher rating but few reviews`() {
        val popularScore = RankPlacesUseCase.computeScore(
            rating = 4.2,
            reviewCount = 3000,
            travelMinutes = 5.0,
            maxMinutes = 10,
        )
        val niceButUnknownScore = RankPlacesUseCase.computeScore(
            rating = 4.8,
            reviewCount = 80,
            travelMinutes = 5.0,
            maxMinutes = 10,
        )

        assertTrue(
            "Popular place (3000 reviews, 4.2) should beat lesser-known (80 reviews, 4.8)",
            popularScore > niceButUnknownScore,
        )
    }

    @Test
    fun `closer place gets mild boost but popularity still dominates`() {
        val farButPopular = RankPlacesUseCase.computeScore(
            rating = 4.3,
            reviewCount = 2000,
            travelMinutes = 9.0,
            maxMinutes = 10,
        )
        val nearButUnpopular = RankPlacesUseCase.computeScore(
            rating = 4.3,
            reviewCount = 100,
            travelMinutes = 1.0,
            maxMinutes = 10,
        )

        assertTrue(
            "Far but popular place should still beat near but unpopular place",
            farButPopular > nearButUnpopular,
        )
    }

    @Test
    fun `empty places list returns empty results`() {
        val result = useCase(
            emptyList(),
            emptyList(),
            FilterSettings(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `place without matching travel info is excluded`() {
        val places = listOf(createPlace("a"))
        val travels = listOf(createTravel("b", 300))
        val filters = FilterSettings(travelTimeMinutes = 10, transportMode = TransportMode.WALK)

        val result = useCase(places, travels, filters)

        assertTrue(result.isEmpty())
    }
}
