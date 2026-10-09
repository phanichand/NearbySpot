package com.nearbyspot.presentation.home

import android.location.Location
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.model.TravelInfo
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.domain.usecase.GetNearbyPlacesUseCase
import com.nearbyspot.domain.usecase.GetTravelTimesUseCase
import com.nearbyspot.domain.usecase.RankPlacesUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.presentation.route.MainDispatcherRule
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getNearbyPlaces = mockk<GetNearbyPlacesUseCase>()
    private val getTravelTimes = mockk<GetTravelTimesUseCase>()
    private val rankPlaces = mockk<RankPlacesUseCase>()
    private val locationHelper = mockk<LocationHelper>()
    private val photoProvider = mockk<PlacePhotoProvider>()
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>()
    private val repository = mockk<PlaceRepository>()

    @Test
    fun `cancelled search that returns late cannot replace newer results`() = runTest {
        val candidate = place("candidate")
        val stale = rankedPlace("stale")
        val fresh = rankedPlace("fresh")
        val firstTravelStarted = CompletableDeferred<Unit>()
        val travelCall = AtomicInteger()
        every { repository.observeFavoriteIds() } returns emptyFlow()
        coEvery { locationHelper.getCurrentLocation() } returns location()
        coEvery { getNearbyPlaces(any(), any(), any(), any(), any()) } returns listOf(candidate)
        coEvery { getTravelTimes(any(), any(), any(), any()) } coAnswers {
            if (travelCall.incrementAndGet() == 1) {
                firstTravelStarted.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                    emptyList()
                } catch (_: CancellationException) {
                    listOf(TravelInfo("stale", 60, 100))
                }
            } else {
                listOf(TravelInfo("fresh", 60, 100))
            }
        }
        every { rankPlaces(any(), match { it.first().placeId == "stale" }, any()) } returns listOf(stale)
        every { rankPlaces(any(), match { it.first().placeId == "fresh" }, any()) } returns listOf(fresh)
        val viewModel = viewModel()

        viewModel.onPermissionResult(true)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        firstTravelStarted.await()

        viewModel.onTransportModeChanged(TransportMode.WALK)
        advanceUntilIdle()

        assertEquals(listOf(fresh), viewModel.uiState.value.rankedPlaces)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    private fun viewModel() = HomeViewModel(
        getNearbyPlaces = getNearbyPlaces,
        getTravelTimes = getTravelTimes,
        rankPlaces = rankPlaces,
        locationHelper = locationHelper,
        photoProvider = photoProvider,
        toggleFavoriteUseCase = toggleFavoriteUseCase,
        repository = repository,
    )

    private fun location() = mockk<Location> {
        every { latitude } returns 12.9716
        every { longitude } returns 77.5946
    }

    private fun rankedPlace(id: String): RankedPlace {
        val place = place(id)
        return RankedPlace(place, TravelInfo(id, 60, 100), score = 1.0)
    }

    private fun place(id: String) = Place(
        placeId = id,
        name = id,
        address = "Address",
        latitude = 12.0,
        longitude = 77.0,
        rating = 4.5,
        userRatingCount = 500,
        priceLevel = 2,
        isOpen = true,
        photoRefs = emptyList(),
        reviews = emptyList(),
        types = listOf("restaurant"),
    )
}
