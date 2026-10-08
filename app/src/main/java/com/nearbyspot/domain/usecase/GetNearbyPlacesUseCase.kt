package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.RadiusEstimator
import com.nearbyspot.domain.model.TransportMode
import javax.inject.Inject

class GetNearbyPlacesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        placeTypes: List<String>,
        travelTimeMinutes: Int,
        transportMode: TransportMode,
    ): List<Place> {
        val radius = RadiusEstimator.estimateRadiusMeters(travelTimeMinutes, transportMode)
        return repository.searchNearbyPlaces(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radius,
            placeTypes = placeTypes,
        )
    }
}
