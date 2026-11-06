package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import javax.inject.Inject

class ComputeRouteUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TransportMode,
        departureTimeMillis: Long? = null,
    ): RouteInfo {
        return repository.computeRoute(originLat, originLng, destLat, destLng, mode, departureTimeMillis)
    }
}
