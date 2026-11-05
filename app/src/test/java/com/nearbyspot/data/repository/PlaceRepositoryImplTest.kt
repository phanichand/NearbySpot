package com.nearbyspot.data.repository

import com.google.gson.Gson
import com.nearbyspot.data.local.PlaceDao
import com.nearbyspot.data.local.PlaceEntity
import com.nearbyspot.data.remote.PlacesDataSource
import com.nearbyspot.data.remote.RoutesApiService
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceRepositoryImplTest {

    private val placesDataSource = mockk<PlacesDataSource>()
    private val routesApiService = mockk<RoutesApiService>()
    private val placeDao = mockk<PlaceDao>()
    private val gson = Gson()
    private val repository = PlaceRepositoryImpl(
        placesDataSource = placesDataSource,
        routesApiService = routesApiService,
        placeDao = placeDao,
        gson = gson,
    )

    @Test
    fun `valid cached places skip the network`() = runTest {
        val cachedPlace = place("cached")
        coEvery { placeDao.getCachedPlaces(any(), any()) } returns listOf(
            cachedEntity(gson.toJson(cachedPlace)),
        )

        val result = repository.searchNearbyPlaces(
            latitude = 12.9716,
            longitude = 77.5946,
            radiusMeters = 1_000.0,
            placeTypes = listOf("restaurant"),
        )

        assertEquals(listOf(cachedPlace), result)
        coVerify(exactly = 0) { placesDataSource.searchNearby(any(), any(), any(), any()) }
    }

    @Test
    fun `fully corrupt cache falls back to network and replaces cache`() = runTest {
        val remotePlace = place("remote")
        coEvery { placeDao.getCachedPlaces(any(), any()) } returns listOf(
            cachedEntity("not-json"),
        )
        coEvery {
            placesDataSource.searchNearby(any(), any(), any(), any())
        } returns listOf(remotePlace)
        coEvery { placeDao.insertPlaces(any()) } just runs
        coEvery { placeDao.deleteStale(any()) } just runs

        val result = repository.searchNearbyPlaces(
            latitude = 12.9716,
            longitude = 77.5946,
            radiusMeters = 1_000.0,
            placeTypes = listOf("restaurant"),
        )

        assertEquals(listOf(remotePlace), result)
        coVerify(exactly = 1) {
            placesDataSource.searchNearby(12.9716, 77.5946, 1_000.0, listOf("restaurant"))
        }
        coVerify(exactly = 1) { placeDao.insertPlaces(match { it.single().placeId == "remote" }) }
    }

    @Test
    fun `reviews are empty instead of failing when the Places budget is exhausted`() = runTest {
        coEvery { placesDataSource.fetchReviews(any()) } throws PlacesBudgetExhaustedException()

        assertEquals(emptyList<com.nearbyspot.domain.model.Review>(), repository.fetchReviews("place"))
    }

    private fun cachedEntity(json: String) = PlaceEntity(
        placeId = "cached",
        placeJson = json,
        cacheKey = "cache-key",
        lastFetchedEpochMs = Long.MAX_VALUE,
    )

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
