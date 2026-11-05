package com.nearbyspot.domain.usecase

import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.GeoPoint
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteMatrixEntry
import com.nearbyspot.domain.model.RouteSearchException
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.RouteSearchProgress
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetAlongRoutePlacesUseCaseTest {

    private val repository = mockk<PlaceRepository>()
    private val useCase = GetAlongRoutePlacesUseCase(repository)

    @Test
    fun `detour uses verified via-stop formula and clamps negative values`() {
        assertEquals(100, GetAlongRoutePlacesUseCase.computeDetourSeconds(600, 500, 1_000))
        assertEquals(0, GetAlongRoutePlacesUseCase.computeDetourSeconds(300, 400, 1_000))
        assertEquals(
            Int.MAX_VALUE,
            GetAlongRoutePlacesUseCase.computeDetourSeconds(
                Int.MAX_VALUE,
                Int.MAX_VALUE,
                0,
            ),
        )
    }

    @Test
    fun `verified matrix legs produce truthful detour`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("verified"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), reachSeconds = 600, onwardSeconds = 500, baselineSeconds = 1_000)
        }

        val result = useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        assertEquals(1, result.size)
        assertEquals(600, result.single().reachableSeconds)
        assertEquals(100, result.single().detourSeconds)
        coVerify(exactly = 2) { repository.getRouteMatrix(any(), any(), TransportMode.DRIVE) }
    }

    @Test
    fun `candidate without onward leg is omitted instead of estimated`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("missing-onward"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size == 2) {
                listOf(
                    entry(0, 0, 600),
                    entry(0, 1, 1_000),
                )
            } else {
                emptyList()
            }
        }

        val result = useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `candidate without outbound leg is omitted instead of estimated`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("missing-outbound"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size == 2) {
                listOf(entry(0, 1, 1_000))
            } else {
                listOf(entry(0, 0, 500))
            }
        }

        val result = useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `max detour boundary is inclusive and values above it are omitted`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("boundary"))
        var onwardSeconds = 1_300
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, onwardSeconds, 1_000)
        }

        val atBoundary = useCase(
            route(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(maxDetourMinutes = 15),
        )
        onwardSeconds = 1_301
        val aboveBoundary = useCase(
            route(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(maxDetourMinutes = 15),
        )

        assertEquals(900, atBoundary.single().detourSeconds)
        assertTrue(aboveBoundary.isEmpty())
    }

    @Test
    fun `missing destination baseline is a typed failure`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("candidate"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size == 2) {
                listOf(entry(0, 0, 600))
            } else {
                listOf(entry(0, 0, 500))
            }
        }

        val failure = runCatching {
            useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())
        }.exceptionOrNull() as RouteSearchException

        assertEquals(RouteSearchFailure.DESTINATION_TRAVEL_TIME_UNAVAILABLE, failure.failure)
    }

    @Test
    fun `all discovery failures are typed while a partial failure still succeeds`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } throws
            IllegalStateException("network")

        val allFailed = runCatching {
            useCase(longRoute(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())
        }.exceptionOrNull() as RouteSearchException
        assertEquals(RouteSearchFailure.ALL_ANCHORS_FAILED, allFailed.failure)

        val call = AtomicInteger(0)
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } coAnswers {
            if (call.getAndIncrement() == 0) throw IllegalStateException("one anchor failed")
            listOf(place("place-${call.get()}"))
        }
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, 500, 1_000)
        }

        assertTrue(
            useCase(longRoute(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings()).isNotEmpty(),
        )
    }

    @Test
    fun `long routes cap discovery concurrency candidates and matrix work`() = runTest {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val placeCounter = AtomicInteger(0)
        val matrixShapes = mutableListOf<Pair<Int, Int>>()
        val progressValues = mutableListOf<Int>()

        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } coAnswers {
            val concurrent = active.incrementAndGet()
            maximumActive.updateAndGet { maxOf(it, concurrent) }
            delay(10)
            active.decrementAndGet()
            List(5) { place("p-${placeCounter.getAndIncrement()}") }
        }
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            synchronized(matrixShapes) { matrixShapes += origins.size to destinations.size }
            matrixFor(origins, destinations, 600, 500, 1_000)
        }

        useCase(
            longRoute(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(maxDetourMinutes = 30),
        ) { progress ->
            if (progress is RouteSearchProgress.SearchingStops) {
                progressValues += progress.completed
            }
        }

        assertTrue(maximumActive.get() <= GetAlongRoutePlacesUseCase.MAX_CONCURRENT_DISCOVERY_CALLS)
        coVerify(exactly = GetAlongRoutePlacesUseCase.MAX_SEARCH_ANCHORS) {
            repository.searchNearbyPlaces(any(), any(), any(), any())
        }
        assertEquals(2, matrixShapes.size)
        val candidateCount = matrixShapes.single { it.first == 1 }.second - 1
        assertEquals(GetAlongRoutePlacesUseCase.MAX_MATRIX_CANDIDATES, candidateCount)
        assertEquals(candidateCount * 2 + 1, matrixShapes.sumOf { it.first * it.second })
        assertEquals(progressValues.sorted(), progressValues)
        assertEquals(GetAlongRoutePlacesUseCase.MAX_SEARCH_ANCHORS, progressValues.last())
    }

    @Test
    fun `100 review boundary and other filters run before matrix budgeting`() = runTest {
        val good = place("good", reviewCount = 100)
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns listOf(
            good,
            good,
            place("below-review-threshold", reviewCount = 99),
            place("closed", isOpen = false),
            place("unknown", isOpen = null),
            place("expensive", priceLevel = 4),
            place("at-destination", latitude = 0.20, longitude = 0.0),
        )
        var outboundDestinationCount = 0
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size > 1) {
                outboundDestinationCount = destinations.size
            }
            matrixFor(origins, destinations, 600, 500, 1_000)
        }

        val result = useCase(
            route(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(openNowOnly = true, maxPriceLevel = 2),
        )

        assertEquals(listOf("good"), result.map { it.place.placeId })
        assertEquals(2, outboundDestinationCount) // one candidate plus the destination baseline
    }

    @Test
    fun `route search keeps destination eateries and orders by blended quality score`() = runTest {
        val mostReviewedAtDestination = place(
            id = "destination-favorite",
            reviewCount = 20_000,
            rating = 4.1,
            latitude = 0.20,
            longitude = 0.0,
        )
        coEvery { repository.searchAlongRoute("encoded-route", listOf("restaurant")) } returns listOf(
            place("higher-rating", reviewCount = 100, rating = 4.9),
            place("popular", reviewCount = 10_000, rating = 4.3),
        )
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(mostReviewedAtDestination)
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, 500, 1_000)
        }

        val result = useCase(
            route().copy(encodedPolyline = "encoded-route"),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(),
        )

        assertEquals(
            listOf("popular", "destination-favorite", "higher-rating"),
            result.map { it.place.placeId },
        )
        coVerify(exactly = 1) {
            repository.searchNearbyPlaces(
                latitude = 0.20,
                longitude = 0.0,
                radiusMeters = GetAlongRoutePlacesUseCase.DESTINATION_SEARCH_RADIUS_METERS,
                placeTypes = listOf("restaurant"),
            )
        }
    }

    @Test
    fun `route search spreads results across the route instead of clustering at the origin`() = runTest {
        val originCluster = (0 until 30).map { index ->
            place(
                id = "origin-$index",
                reviewCount = 30_000 - index,
                latitude = 0.12,
                longitude = 0.0,
            )
        }
        val corridorPlaces = listOf(
            place("corridor-2", reviewCount = 500, latitude = 0.72, longitude = 0.0),
            place("corridor-3", reviewCount = 400, latitude = 1.32, longitude = 0.0),
            place("corridor-4", reviewCount = 300, latitude = 1.92, longitude = 0.0),
        )
        val destinationPlace = place(
            "destination",
            reviewCount = 200,
            latitude = 2.0,
            longitude = 0.0,
        )
        coEvery { repository.searchAlongRoute("encoded-long-route", any()) } returns
            originCluster + corridorPlaces
        coEvery {
            repository.searchNearbyPlaces(
                latitude = 2.0,
                longitude = 0.0,
                radiusMeters = GetAlongRoutePlacesUseCase.DESTINATION_SEARCH_RADIUS_METERS,
                placeTypes = any(),
            )
        } returns listOf(destinationPlace)
        var matrixCandidateCount = 0
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size > 1) {
                matrixCandidateCount = destinations.size - 1
            }
            matrixFor(origins, destinations, 600, 500, 40_000)
        }

        val result = useCase(
            longEncodedRoute(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(),
        )

        assertEquals(GetAlongRoutePlacesUseCase.MAX_MATRIX_CANDIDATES, matrixCandidateCount)
        assertTrue(result.map { it.place.placeId }.containsAll(
            listOf("corridor-2", "corridor-3", "corridor-4", "destination"),
        ))
        assertEquals(result.map { it.score }.sortedDescending(), result.map { it.score })
        assertEquals(2, result.count { it.place.placeId.startsWith("origin-") })
        coVerify(exactly = 1) { repository.searchNearbyPlaces(any(), any(), any(), any()) }
    }

    @Test
    fun `ineligible route result triggers one bounded supplemental band search`() = runTest {
        coEvery { repository.searchAlongRoute("encoded-long-route", any()) } returns listOf(
            place("below-minimum", reviewCount = 99, latitude = 0.12, longitude = 0.0),
            place("corridor-2", reviewCount = 500, latitude = 0.72, longitude = 0.0),
            place("corridor-3", reviewCount = 400, latitude = 1.32, longitude = 0.0),
            place("corridor-4", reviewCount = 300, latitude = 1.92, longitude = 0.0),
        )
        coEvery {
            repository.searchNearbyPlaces(any(), any(), any(), any())
        } coAnswers {
            val latitude = firstArg<Double>()
            val radius = thirdArg<Double>()
            if (radius == GetAlongRoutePlacesUseCase.DESTINATION_SEARCH_RADIUS_METERS) {
                listOf(place("destination", reviewCount = 200, latitude = 2.0, longitude = 0.0))
            } else {
                listOf(place("supplemented", reviewCount = 100, latitude = latitude, longitude = 0.0))
            }
        }
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, 500, 1_000)
        }

        val result = useCase(
            longEncodedRoute(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(),
        )

        assertTrue("supplemented" in result.map { it.place.placeId })
        assertTrue("below-minimum" !in result.map { it.place.placeId })
        coVerify(exactly = 1) {
            repository.searchNearbyPlaces(
                latitude = any(),
                longitude = any(),
                radiusMeters = GetAlongRoutePlacesUseCase.SEARCH_RADIUS_METERS,
                placeTypes = listOf("restaurant"),
            )
        }
    }

    @Test
    fun `empty encoded route sources retain the full waypoint fallback`() = runTest {
        coEvery { repository.searchAlongRoute("encoded-long-route", any()) } returns emptyList()
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } coAnswers {
            val radius = thirdArg<Double>()
            if (radius == GetAlongRoutePlacesUseCase.DESTINATION_SEARCH_RADIUS_METERS) {
                emptyList()
            } else {
                listOf(place("fallback-${firstArg<Double>()}"))
            }
        }
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, 500, 1_000)
        }

        val result = useCase(
            longEncodedRoute(),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(),
        )

        assertTrue(result.isNotEmpty())
        coVerify(exactly = GetAlongRoutePlacesUseCase.MAX_SEARCH_ANCHORS) {
            repository.searchNearbyPlaces(
                latitude = any(),
                longitude = any(),
                radiusMeters = GetAlongRoutePlacesUseCase.SEARCH_RADIUS_METERS,
                placeTypes = listOf("restaurant"),
            )
        }
    }

    @Test
    fun `empty filtered pool performs no matrix calls`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("too-new", reviewCount = 1))

        val result = useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { repository.getRouteMatrix(any(), any(), any()) }
    }

    @Test
    fun `overall matrix timeout is a typed terminal failure`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("candidate"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            awaitCancellation()
        }

        val progress = mutableListOf<RouteSearchProgress>()
        val failure = runCatching {
            useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings()) {
                progress += it
            }
        }.exceptionOrNull() as RouteSearchException

        assertEquals(RouteSearchFailure.TIMED_OUT, failure.failure)
        assertTrue(progress.last() is RouteSearchProgress.CalculatingTravelTimes)
    }

    @Test(expected = AssertionError::class)
    fun `fatal discovery errors are not converted to partial failures`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } throws
            AssertionError("fatal")

        useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation during discovery propagates`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } coAnswers {
            awaitCancellation()
        }

        val search = async {
            useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())
        }
        search.cancel()
        search.await()
    }

    @Test
    fun `stops within minutes of the start are skipped on long trips only`() {
        assertTrue(GetAlongRoutePlacesUseCase.isTooCloseToStart(120, 38_000))
        assertTrue(GetAlongRoutePlacesUseCase.isTooCloseToStart(599, 3_600))
        assertEquals(false, GetAlongRoutePlacesUseCase.isTooCloseToStart(600, 38_000))
        assertEquals(false, GetAlongRoutePlacesUseCase.isTooCloseToStart(120, 3_599))
    }

    @Test
    fun `a well reviewed city restaurant at the start is dropped from a long trip`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("city", reviewCount = 18_000), place("road", reviewCount = 300))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            val origins = firstArg<List<GeoPoint>>()
            val destinations = secondArg<List<GeoPoint>>()
            if (origins.size == 1 && destinations.size > 1) {
                destinations.indices.map { index ->
                    entry(
                        originIndex = 0,
                        destinationIndex = index,
                        durationSeconds = when (index) {
                            destinations.lastIndex -> 38_000
                            0 -> 120
                            else -> 9_000
                        },
                    )
                }
            } else {
                origins.indices.map { entry(it, destinationIndex = 0, durationSeconds = 29_000) }
            }
        }

        val result = useCase(longRoute(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        assertEquals(1, result.size)
        assertEquals(9_000, result.single().reachableSeconds)
    }

    @Test
    fun `smoothed rating keeps a few reviews from beating many reviews`() {
        val thin = GetAlongRoutePlacesUseCase.smoothedRating(rating = 4.9, reviewCount = 100)
        val established = GetAlongRoutePlacesUseCase.smoothedRating(rating = 4.5, reviewCount = 18_000)
        assertTrue(thin < 4.5)
        assertTrue(established > 4.4)
    }

    @Test
    fun `score prefers a strong rating over raw review count`() {
        fun score(rating: Double, reviews: Int) =
            GetAlongRoutePlacesUseCase.computeScore(rating, reviews, 5.0, 15)

        assertTrue(score(rating = 4.7, reviews = 935) > score(rating = 3.3, reviews = 5_000))
        assertTrue(score(rating = 4.5, reviews = 18_000) > score(rating = 4.5, reviews = 900))
    }

    @Test
    fun `route slots scale with trip length and cover the remaining route`() {
        assertEquals(1, GetAlongRoutePlacesUseCase.routeSlotCount(1_000))
        assertEquals(8, GetAlongRoutePlacesUseCase.routeSlotCount(38_880))
        assertEquals(8, GetAlongRoutePlacesUseCase.routeSlotCount(500_000))
        assertEquals(0, GetAlongRoutePlacesUseCase.routeSlot(0, 0, 200, 8))
        assertEquals(4, GetAlongRoutePlacesUseCase.routeSlot(100, 0, 200, 8))
        assertEquals(7, GetAlongRoutePlacesUseCase.routeSlot(200, 0, 200, 8))
        assertEquals(0, GetAlongRoutePlacesUseCase.routeSlot(5, 5, 5, 8))
    }

    @Test
    fun `exhausted monthly budget is a non retryable typed failure`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } throws
            PlacesBudgetExhaustedException()

        val failure = runCatching {
            useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())
        }.exceptionOrNull() as RouteSearchException

        assertEquals(RouteSearchFailure.MONTHLY_LIMIT_REACHED, failure.failure)
        assertEquals(false, failure.retryable)
    }

    @Test
    fun `empty time slots get a bounded wider gap search`() = runTest {
        coEvery { repository.searchAlongRoute("encoded-long-route", any()) } returns
            listOf(place("origin", reviewCount = 500, latitude = 0.02, longitude = 0.0))
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns emptyList()
        coEvery {
            repository.searchNearbyPlaces(
                any(),
                any(),
                GetAlongRoutePlacesUseCase.GAP_FILL_RADIUS_METERS,
                any(),
            )
        } coAnswers {
            listOf(place("gap-${firstArg<Double>()}", latitude = firstArg(), longitude = 0.0))
        }
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 3_000, 500, 40_000)
        }

        val result = useCase(
            longEncodedRoute().copy(durationSeconds = 40_000),
            ORIGIN,
            0,
            listOf("restaurant"),
            RouteFilterSettings(),
        )

        coVerify(exactly = GetAlongRoutePlacesUseCase.MAX_GAP_FILL_SEARCHES) {
            repository.searchNearbyPlaces(
                any(),
                any(),
                GetAlongRoutePlacesUseCase.GAP_FILL_RADIUS_METERS,
                any(),
            )
        }
        assertTrue(result.any { it.place.placeId.startsWith("gap-") })
    }

    @Test
    fun `routes without gaps spend no extra searches`() = runTest {
        coEvery { repository.searchNearbyPlaces(any(), any(), any(), any()) } returns
            listOf(place("only"))
        coEvery { repository.getRouteMatrix(any(), any(), any()) } coAnswers {
            matrixFor(firstArg(), secondArg(), 600, 500, 1_000)
        }

        useCase(route(), ORIGIN, 0, listOf("restaurant"), RouteFilterSettings())

        coVerify(exactly = 0) {
            repository.searchNearbyPlaces(
                any(),
                any(),
                GetAlongRoutePlacesUseCase.GAP_FILL_RADIUS_METERS,
                any(),
            )
        }
    }

    private fun matrixFor(
        origins: List<GeoPoint>,
        destinations: List<GeoPoint>,
        reachSeconds: Int,
        onwardSeconds: Int,
        baselineSeconds: Int,
    ): List<RouteMatrixEntry> = if (origins.size == 1 && destinations.size > 1) {
        destinations.indices.map { destinationIndex ->
            entry(
                originIndex = 0,
                destinationIndex = destinationIndex,
                durationSeconds = if (destinationIndex == destinations.lastIndex) {
                    baselineSeconds
                } else {
                    reachSeconds
                },
            )
        }
    } else {
        origins.indices.map { originIndex ->
            entry(originIndex, destinationIndex = 0, durationSeconds = onwardSeconds)
        }
    }

    private fun entry(
        originIndex: Int,
        destinationIndex: Int,
        durationSeconds: Int,
    ) = RouteMatrixEntry(originIndex, destinationIndex, durationSeconds, distanceMeters = 1_000)

    private fun place(
        id: String,
        reviewCount: Int = 100,
        priceLevel: Int? = 2,
        isOpen: Boolean? = true,
        rating: Double = 4.5,
        latitude: Double = 0.10,
        longitude: Double = 0.02,
    ) = Place(
        placeId = id,
        name = id,
        address = "Address",
        latitude = latitude,
        longitude = longitude,
        rating = rating,
        userRatingCount = reviewCount,
        priceLevel = priceLevel,
        isOpen = isOpen,
        photoRefs = emptyList(),
        reviews = emptyList(),
        types = listOf("restaurant"),
    )

    private fun route() = RouteInfo(
        encodedPolyline = "",
        durationSeconds = 1_000,
        distanceMeters = 22_000,
        decodedPoints = listOf(ORIGIN, LatLng(0.20, 0.0)),
    )

    private fun longRoute() = RouteInfo(
        encodedPolyline = "",
        durationSeconds = 10_000,
        distanceMeters = 222_000,
        decodedPoints = (0..200).map { LatLng(it / 100.0, 0.0) },
    )

    private fun longEncodedRoute() = longRoute().copy(encodedPolyline = "encoded-long-route")

    private companion object {
        val ORIGIN = LatLng(0.0, 0.0)
    }
}
