package com.nearbyspot.domain.usecase

import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.PickKind
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRouteSectionsUseCaseTest {

    private val alongRoute = mockk<GetAlongRoutePlacesUseCase>()
    private val repository = mockk<PlaceRepository>()
    private val useCase = GetRouteSectionsUseCase(alongRoute, repository, clock = { NOW })

    private val points = (0..100).map { LatLng(0.0, it * 0.01) }
    private val route = RouteInfo("encoded-full-route", 7_200, 100_000, points)
    private val section = RouteSection(
        index = 1,
        startRouteIndex = 10,
        endRouteIndex = 60,
        aheadSeconds = 2_700,
        status = SectionStatus.NOT_LOADED,
        picks = emptyList(),
    )

    private fun recentReviews() = List(3) {
        Review("a", 5.0, "", "", publishedAtEpochMillis = NOW - DAY_MILLIS)
    }

    private fun candidates(count: Int) = (1..count).map { alongRoutePlace("p$it", rating = 4.0 + it / 20.0, reachMinutes = 10 + it) }

    private fun stubSearch(result: List<com.nearbyspot.domain.model.AlongRoutePlace>) {
        coEvery { alongRoute.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } returns result
    }

    private suspend fun load() = useCase(route, section, listOf("restaurant"), RouteFilterSettings())

    @Test
    fun `loads picks for the section slice and fetches reviews for at most 3 finalists`() = runTest {
        stubSearch(candidates(5))
        coEvery { repository.fetchReviews(any()) } returns recentReviews()

        val loaded = load()

        assertEquals(SectionStatus.LOADED, loaded.status)
        assertTrue(loaded.picks.isNotEmpty())
        coVerify(exactly = 3) { repository.fetchReviews(any()) }
        coVerify {
            alongRoute.invoke(
                match { it.decodedPoints.size == 51 && it.encodedPolyline.isBlank() },
                points[10],
                0,
                listOf("restaurant"),
                RouteFilterSettings(),
                true,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `a review fetch failure still returns the Popular pick with POPULAR_ONLY`() = runTest {
        stubSearch(candidates(3))
        coEvery { repository.fetchReviews(any()) } throws RuntimeException("network")

        val loaded = load()

        assertEquals(SectionStatus.LOADED, loaded.status)
        assertEquals(SectionNote.POPULAR_ONLY, loaded.note)
        assertEquals(setOf(PickKind.POPULAR), loaded.picks.single().kinds)
    }

    @Test
    fun `a budget error while fetching reviews also returns the Popular pick only`() = runTest {
        stubSearch(candidates(3))
        coEvery { repository.fetchReviews(any()) } throws PlacesBudgetExhaustedException()

        val loaded = load()

        assertEquals(SectionNote.POPULAR_ONLY, loaded.note)
        assertEquals(setOf(PickKind.POPULAR), loaded.picks.single().kinds)
    }

    @Test
    fun `no candidates marks the section as having no qualifying place`() = runTest {
        stubSearch(emptyList())

        val loaded = load()

        assertEquals(SectionStatus.NO_QUALIFYING_PLACE, loaded.status)
        assertTrue(loaded.picks.isEmpty())
        coVerify(exactly = 0) { repository.fetchReviews(any()) }
    }

    @Test
    fun `candidates beyond 45 minutes mark the section as having no qualifying place`() = runTest {
        stubSearch(listOf(alongRoutePlace("far", reachMinutes = 50)))

        assertEquals(SectionStatus.NO_QUALIFYING_PLACE, load().status)
    }

    @Test
    fun `a budget failure with nothing found marks the section failed and budget limited`() = runTest {
        coEvery { alongRoute.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RouteSearchException(RouteSearchFailure.TRIP_LIMIT_REACHED, retryable = false)

        val loaded = load()

        assertEquals(SectionStatus.FAILED, loaded.status)
        assertEquals(SectionNote.BUDGET_LIMITED, loaded.note)
        assertEquals(RouteSearchFailure.TRIP_LIMIT_REACHED, loaded.failure)
    }

    @Test
    fun `any other search failure marks the section failed without a note`() = runTest {
        coEvery { alongRoute.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RouteSearchException(RouteSearchFailure.TIMED_OUT)

        val loaded = load()

        assertEquals(SectionStatus.FAILED, loaded.status)
        assertEquals(null, loaded.note)
        assertEquals(RouteSearchFailure.TIMED_OUT, loaded.failure)
    }

    @Test
    fun `a partial result after a budget error carries BUDGET_LIMITED`() = runTest {
        coEvery { alongRoute.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            arg<() -> Unit>(6).invoke()
            candidates(2)
        }
        coEvery { repository.fetchReviews(any()) } returns recentReviews()

        val loaded = load()

        assertEquals(SectionStatus.LOADED, loaded.status)
        assertEquals(SectionNote.BUDGET_LIMITED, loaded.note)
    }

    @Test
    fun `picks carry full route positions and the time ahead from the traveller`() = runTest {
        val sectionAhead = section.copy(aheadSeconds = 2_700)
        stubSearch(listOf(alongRoutePlace("p", reachMinutes = 10).copy(nearestRoutePointIndex = 5)))
        coEvery { repository.fetchReviews(any()) } returns recentReviews()

        val loaded = useCase(route, sectionAhead, listOf("restaurant"), RouteFilterSettings())

        val pick = loaded.picks.single()
        assertEquals(15, pick.candidate.nearestRoutePointIndex)
        assertEquals(2_700 + 600, pick.timeAheadSeconds)
        assertEquals(600, pick.candidate.reachableSeconds)
    }

    @Test
    fun `reviews are fetched once per place across loads until the trip cache is cleared`() = runTest {
        stubSearch(candidates(3))
        coEvery { repository.fetchReviews(any()) } returns recentReviews()

        load()
        load()
        coVerify(exactly = 3) { repository.fetchReviews(any()) }

        useCase.clearTripCache()
        load()
        coVerify(exactly = 6) { repository.fetchReviews(any()) }
    }

    @Test
    fun `cancellation propagates`() = runTest {
        coEvery { alongRoute.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            CancellationException("cancelled")

        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { load() } }
    }

    @Test
    fun `a slice of a real route yields picks through the anchor based search`() = runTest {
        val realUseCase = GetRouteSectionsUseCase(GetAlongRoutePlacesUseCase(repository), repository, clock = { NOW })
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(com.nearbyspot.domain.model.Place(
                placeId = "real",
                name = "real",
                address = "",
                latitude = 0.0,
                longitude = 0.2,
                rating = 4.5,
                userRatingCount = 500,
                priceLevel = null,
                isOpen = true,
                photoRefs = emptyList(),
                reviews = emptyList(),
                types = emptyList(),
            ))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<com.nearbyspot.domain.model.GeoPoint>>()
            val destinations = secondArg<List<com.nearbyspot.domain.model.GeoPoint>>()
            if (origins.size == 1) {
                destinations.indices.map { index ->
                    com.nearbyspot.domain.model.RouteMatrixEntry(0, index, if (index == destinations.lastIndex) 2_700 else 600, 1_000)
                }
            } else {
                origins.indices.map { com.nearbyspot.domain.model.RouteMatrixEntry(it, 0, 500, 1_000) }
            }
        }
        coEvery { repository.fetchReviews(any()) } returns recentReviews()

        val loaded = realUseCase(route, section, listOf("restaurant"), RouteFilterSettings())

        assertEquals(SectionStatus.LOADED, loaded.status)
        assertEquals("real", loaded.picks.first().candidate.place.placeId)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY_MILLIS = 86_400_000L
    }
}
