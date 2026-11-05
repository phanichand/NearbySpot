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
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionPick
import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.domain.usecase.ComputeRouteUseCase
import com.nearbyspot.domain.usecase.GetRouteSectionsUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
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
    private val getRouteSectionsUseCase = mockk<GetRouteSectionsUseCase>()
    private val locationHelper = mockk<LocationHelper>()
    private val photoProvider = mockk<PlacePhotoProvider>()
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>()
    private val repository = mockk<PlaceRepository>()

    private lateinit var viewModel: RouteViewModel

    @Before
    fun setUp() {
        every { repository.observeFavoriteIds() } returns emptyFlow()
        every { repository.beginTrip() } just Runs
        every { repository.endTrip() } just Runs
        every { locationHelper.observeLocation(any()) } returns emptyFlow()
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers { loaded(secondArg()) }

        viewModel = RouteViewModel(
            computeRouteUseCase = computeRouteUseCase,
            getRouteSectionsUseCase = getRouteSectionsUseCase,
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
        val verified = listOf("verified")
        val stale = listOf("stale")
        val refreshed = listOf("refreshed")
        val refreshStarted = CompletableDeferred<Unit>()
        var searchNumber = 0

        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns route
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers {
            val section = secondArg<RouteSection>()
            searchNumber++
            when (searchNumber) {
                1 -> loaded(section, *verified.toTypedArray())
                2 -> {
                    refreshStarted.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        loaded(section, *stale.toTypedArray())
                    } catch (_: CancellationException) {
                        loaded(section, *stale.toTypedArray())
                    }
                }
                else -> loaded(section, *refreshed.toTypedArray())
            }
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        assertEquals(verified, placeIds())

        viewModel.onRefreshPlaces()
        runCurrent()
        refreshStarted.await()

        assertEquals(verified, placeIds())
        assertTrue(viewModel.uiState.value.isRefreshingPlaces)
        assertTrue(viewModel.uiState.value.routeSearchProgress != null)

        viewModel.onOpenNowToggled(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(refreshed, placeIds())
        assertNull(state.routeSearchProgress)
        assertFalse(state.isRefreshingPlaces)
        assertNull(state.errorMessage)
    }

    @Test
    fun `refresh failure retains verified results and exposes the error`() = runTest {
        grantLocation()
        var searchNumber = 0

        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers {
            val section = secondArg<RouteSection>()
            searchNumber++
            if (searchNumber == 1) {
                loaded(section, "verified")
            } else {
                section.copy(status = SectionStatus.FAILED, picks = emptyList(), failure = RouteSearchFailure.TIMED_OUT)
            }
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        viewModel.onRefreshPlaces()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("verified"), placeIds())
        assertNull(state.routeSearchProgress)
        assertFalse(state.isRefreshingPlaces)
        assertEquals(RouteSearchFailure.TIMED_OUT, state.routeSearchFailure)
        assertTrue(state.errorMessage?.contains("timed out") == true)
    }

    @Test
    fun `initial search failure is distinct from a successful empty result`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers {
            secondArg<RouteSection>().copy(
                status = SectionStatus.FAILED,
                failure = RouteSearchFailure.ALL_ANCHORS_FAILED,
            )
        }

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
        val searchResult = CompletableDeferred<Unit>()
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers {
            searchStarted.complete(Unit)
            searchResult.await()
            loaded(secondArg())
        }

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        runCurrent()
        searchStarted.await()

        repeat(3) { index ->
            locations.emit(locationAt(ORIGIN.latitude + index + 1.0, ORIGIN.longitude))
            runCurrent()
        }
        coVerify(exactly = 1) { getRouteSectionsUseCase(any(), any(), any(), any()) }

        searchResult.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.routeSearchFailure)
    }

    @Test
    fun `selecting a destination loads only the first section`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)

        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()

        val sections = viewModel.uiState.value.sections
        assertTrue(sections.size > 1)
        assertEquals(SectionStatus.LOADED, sections.first().status)
        assertTrue(sections.drop(1).all { it.status == SectionStatus.NOT_LOADED })
        coVerify(exactly = 1) { getRouteSectionsUseCase(any(), any(), any(), any()) }
        verify(exactly = 1) { repository.beginTrip() }
    }

    @Test
    fun `expanding a later section loads it once even if tapped twice`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        val release = CompletableDeferred<Unit>()
        coEvery { getRouteSectionsUseCase(any(), match { it.index == 1 }, any(), any()) } coAnswers {
            release.await()
            loaded(secondArg(), "later")
        }

        viewModel.onSectionExpanded(1)
        runCurrent()
        viewModel.onSectionExpanded(1)
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { getRouteSectionsUseCase(any(), match { it.index == 1 }, any(), any()) }
        assertEquals(1, viewModel.uiState.value.expandedSectionIndex)
        assertTrue("later" in placeIds())
    }

    @Test
    fun `changing the destination cancels in flight section loads and starts a new trip`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        viewModel.onDestinationSelected("Old", 10.0, 10.0)
        advanceUntilIdle()
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        coEvery { getRouteSectionsUseCase(any(), match { it.index == 1 }, any(), any()) } coAnswers {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                loaded(secondArg(), "old-later")
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            }
        }
        viewModel.onSectionExpanded(1)
        runCurrent()
        started.await()

        viewModel.onDestinationSelected("New", 20.0, 20.0)
        advanceUntilIdle()

        assertTrue(cancelled)
        assertFalse("old-later" in placeIds())
        verify(exactly = 2) { repository.beginTrip() }
    }

    @Test
    fun `changing a category reloads without starting a new trip`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()

        viewModel.onCategoryToggled(com.nearbyspot.domain.model.Category.CAFE)
        advanceUntilIdle()

        coVerify(exactly = 2) { getRouteSectionsUseCase(any(), match { it.index == 0 }, any(), any()) }
        verify(exactly = 1) { repository.beginTrip() }
    }

    @Test
    fun `a refresh keeps a shown pick unless the new one clearly beats it`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        var searchNumber = 0
        coEvery { getRouteSectionsUseCase(any(), any(), any(), any()) } coAnswers {
            searchNumber++
            val section = secondArg<RouteSection>()
            when (searchNumber) {
                1 -> loaded(section, "first")
                2 -> loadedWithRating(section, "slightly-better", 4.55)
                else -> loadedWithRating(section, "much-better", 4.95, reviewCount = 5_000)
            }
        }
        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()

        viewModel.onRefreshPlaces()
        advanceUntilIdle()
        assertEquals(listOf("first"), placeIds())

        viewModel.onRefreshPlaces()
        advanceUntilIdle()
        assertEquals(listOf("much-better"), placeIds())
    }

    @Test
    fun `a loaded section with no qualifying place is merged into its neighbour`() = runTest {
        grantLocation()
        coEvery { computeRouteUseCase(any(), any(), any(), any(), any()) } returns routeTo(10.0, 10.0)
        viewModel.onDestinationSelected("Destination", 10.0, 10.0)
        advanceUntilIdle()
        val before = viewModel.uiState.value.sections.map { it.index }
        coEvery { getRouteSectionsUseCase(any(), match { it.index == 2 }, any(), any()) } coAnswers {
            secondArg<RouteSection>().copy(status = SectionStatus.NO_QUALIFYING_PLACE)
        }

        viewModel.onSectionExpanded(2)
        advanceUntilIdle()

        val after = viewModel.uiState.value.sections.map { it.index }
        assertEquals(before - 2, after)
    }

    private fun loadedWithRating(
        section: RouteSection,
        id: String,
        rating: Double,
        reviewCount: Int = 100,
    ) = section.copy(
        status = SectionStatus.LOADED,
        picks = listOf(
            SectionPick(
                candidate = alongRoutePlace(id, rating, reviewCount),
                kinds = setOf(PickKind.POPULAR),
                extraMinutesVsBaseline = 0,
                extraStarsVsBaseline = 0.0,
                recencySignal = null,
            ),
        ),
    )

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

    private fun placeIds() =
        viewModel.uiState.value.alongRoutePlaces.map { it.place.placeId }

    private fun loaded(section: RouteSection, vararg ids: String) = section.copy(
        status = SectionStatus.LOADED,
        picks = ids.map { id ->
            SectionPick(
                candidate = alongRoutePlace(id),
                kinds = setOf(PickKind.POPULAR),
                extraMinutesVsBaseline = 0,
                extraStarsVsBaseline = 0.0,
                recencySignal = null,
            )
        },
    )

    private fun alongRoutePlace(id: String, rating: Double = 4.5, reviewCount: Int = 100) = AlongRoutePlace(
        place = Place(
            placeId = id,
            name = id,
            address = "Address",
            latitude = 1.0,
            longitude = 1.0,
            rating = rating,
            userRatingCount = reviewCount,
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
