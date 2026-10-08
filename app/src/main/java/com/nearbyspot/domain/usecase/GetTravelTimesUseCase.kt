package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.model.TravelInfo
import com.nearbyspot.domain.repository.PlaceRepository
import javax.inject.Inject

class GetTravelTimesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(
        originLat: Double,
        originLng: Double,
        places: List<Place>,
        mode: TransportMode,
    ): List<TravelInfo> {
        if (places.isEmpty()) return emptyList()

        val results = mutableListOf<TravelInfo>()
        // Routes API computeRouteMatrix supports up to 25 destinations per call
        places.chunked(25).forEach { batch ->
            results.addAll(
                repository.getTravelTimes(originLat, originLng, batch, mode)
            )
        }
        return results
    }
}
