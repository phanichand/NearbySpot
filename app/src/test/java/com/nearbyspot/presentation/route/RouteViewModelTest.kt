package com.nearbyspot.presentation.route

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.PhotoRef
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.Review
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSearchException
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.domain.usecase.ComputeRouteUseCase
import com.nearbyspot.domain.usecase.GetAlongRoutePlacesUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val computeRouteUseCase = mockk<ComputeRouteUseCase>()
    private val getAlongRoutePlacesUseCase = mockk<GetAlongRoutePlacesUseCase>()
    private val locationHelper = mockk<LocationHelper>()
    private val photoProvider = mockk<PlacePhotoProvider>()
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>()
    private val repository = mockk<PlaceRepository>()

    private lateinit var viewModel: RouteViewModel

    @Before
    fun setUp() {
        every { repository.observeFavoriteIds() } returns emptyFlow()
        every { locationHelper.observeLocation(any()) } returns emptyFlow()
        coEvery {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        } returns emptyList()

        viewModel = RouteViewModel(
            computeRouteUseCase = computeRouteUseCase,
            getAlongRoutePlacesUseCase = getAlongRoutePlacesUseCase,
            locationHelper = locationHelper,
            photoProvider = photoProvider,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            repository = repository,
        )
    }

    @Test
    fun `new destination prevents cancelled route from overwriting current route or tracking`() = runTest {
        grantLocation()
        val oldStarted = CompletableDeferred<Unit>()
        val oldRoute = routeTo(10.0, 10.0)
        val newRoute = routeTo(20.0, 20.0)

        coEvery {
            computeRouteUseCase(any(), any(), 10.0, 10.0, TransportMode.DRIVE)
        } coAnswers {
            oldStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                oldRoute
            } catch (_: CancellationException) {
                // Simulate a dependency that returns after cancellation.
                oldRoute
            }
        }
        coEvery {
            computeRouteUseCase(any(), any(), 20.0, 20.0, TransportMode.DRIVE)
        } returns newRoute

        viewModel.onDestinationSelected("Old", 10.0, 10.0)
        runCurrent()
        oldStarted.await()

        viewModel.onDestinationSelected("New", 20.0, 20.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("New", state.destinationName)
        assertSame(newRoute, state.routeInfo)
        assertNull(state.errorMessage)
        assertFalse(state.isLoadingRoute)
        verify(exactly = 1) { locationHelper.observeLocation(any()) }
    }

    @Test
    fun `transport mode change cancels obsolete computation and uses immutable mode snapshot`() = runTest {
        grantLocation()
        val driveStarted = CompletableDeferred<Unit>()
        val driveRoute = routeTo(10.0, 10.0)
        val walkRoute = routeTo(11.0, 11.0)

        coEvery {
            computeRouteUseCase(any(), any(), any(), any(), TransportMode.DRIVE)
        } coAnswers {
            driveStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                driveRoute
            } catch (_: CancellationException) {
                driveRoute
            }
        }
        coEvery {
            computeRouteUseCase(any(), any(), any(), any(), TransportMode.WALK)
        } returns walkRoute

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        runCurrent()
        driveStarted.await()

        viewModel.onTransportModeChanged(TransportMode.WALK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TransportMode.WALK, state.filterSettings.transportMode)
        assertSame(walkRoute, state.routeInfo)
        assertNull(state.errorMessage)
        assertFalse(state.isLoadingRoute)
    }

    @Test
    fun `refresh keeps verified results visible and cancelled refresh cannot replace them`() = runTest {
        grantLocation()
        val route = routeTo(10.0, 10.0)
        val verified = listOf(alongRoutePlace("verified"))
        val stale = listOf(alongRoutePlace("stale"))
        val refreshed = listOf(alongRoutePlace("refreshed"))
        val refreshStarted = CompletableDeferred<Unit>()
        var searchNumber = 0

        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns route
        coEvery {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        } coAnswers {
            searchNumber++
            when (searchNumber) {
                1 -> verified
                2 -> {
                    refreshStarted.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        stale
                    } catch (_: CancellationException) {
                        stale
                    }
                }
                else -> refreshed
            }
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        assertEquals(verified, viewModel.uiState.value.alongRoutePlaces)

        viewModel.onRefreshPlaces()
        runCurrent()
        refreshStarted.await()

        assertEquals(verified, viewModel.uiState.value.alongRoutePlaces)
        assertTrue(viewModel.uiState.value.isRefreshingPlaces)
        assertTrue(viewModel.uiState.value.routeSearchProgress != null)

        viewModel.onOpenNowToggled(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(refreshed, state.alongRoutePlaces)
        assertNull(state.routeSearchProgress)
        assertFalse(state.isRefreshingPlaces)
        assertNull(state.errorMessage)
    }

    @Test
    fun `refresh failure retains verified results and exposes the error`() = runTest {
        grantLocation()
        val verified = listOf(alongRoutePlace("verified"))
        var searchNumber = 0

        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        coEvery {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        } coAnswers {
            searchNumber++
            if (searchNumber == 1) {
                verified
            } else {
                throw RouteSearchException(RouteSearchFailure.TIMED_OUT)
            }
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        viewModel.onRefreshPlaces()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(verified, state.alongRoutePlaces)
        assertNull(state.routeSearchProgress)
        assertFalse(state.isRefreshingPlaces)
        assertEquals(RouteSearchFailure.TIMED_OUT, state.routeSearchFailure)
        assertTrue(state.errorMessage?.contains("timed out") == true)
    }

    @Test
    fun `initial search failure is distinct from a successful empty result`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        coEvery {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        } throws RouteSearchException(RouteSearchFailure.ALL_ANCHORS_FAILED)

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.alongRoutePlaces.isEmpty())
        assertEquals(RouteSearchFailure.ALL_ANCHORS_FAILED, state.routeSearchFailure)
        assertFalse(state.isLoadingPlaces)
    }

    @Test
    fun `live location emissions do not restart an active search`() = runTest {
        grantLocation()
        val locations = MutableSharedFlow<Location>(extraBufferCapacity = 4)
        every { locationHelper.observeLocation(any()) } returns locations
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        val searchStarted = CompletableDeferred<Unit>()
        val searchResult = CompletableDeferred<List<AlongRoutePlace>>()
        coEvery {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        } coAnswers {
            searchStarted.complete(Unit)
            searchResult.await()
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        runCurrent()
        searchStarted.await()

        repeat(3) { index ->
            locations.emit(locationAt(ORIGIN.latitude + index + 1.0, ORIGIN.longitude))
            runCurrent()
        }
        coVerify(exactly = 1) {
            getAlongRoutePlacesUseCase(any(), any(), any(), any(), any(), any())
        }

        searchResult.complete(emptyList())
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.routeSearchFailure)
    }

    private fun grantLocation() {
        val location = locationAt(ORIGIN.latitude, ORIGIN.longitude)
        coEvery { locationHelper.getCurrentLocation() } returns location

        viewModel.onPermissionResult(true)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    private fun locationAt(latitude: Double, longitude: Double): Location = mockk<Location>().also {
        every { it.latitude } returns latitude
        every { it.longitude } returns longitude
    }

    private fun routeTo(latitude: Double, longitude: Double) = RouteInfo(
        encodedPolyline = "",
        durationSeconds = 3_600,
        distanceMeters = 100_000,
        decodedPoints = listOf(ORIGIN, LatLng(latitude, longitude)),
    )

    private fun alongRoutePlace(id: String) = AlongRoutePlace(
        place = Place(
            placeId = id,
            name = id,
            address = "Address",
            latitude = 1.0,
            longitude = 1.0,
            rating = 4.5,
            userRatingCount = 100,
            priceLevel = null,
            isOpen = true,
            photoRefs = emptyList<PhotoRef>(),
            reviews = emptyList<Review>(),
            types = listOf("restaurant"),
        ),
        reachableSeconds = 600,
        detourSeconds = 120,
        nearestRoutePointIndex = 1,
        score = 1.0,
    )

    private companion object {
        val ORIGIN = LatLng(12.9716, 77.5946)
    }
}
