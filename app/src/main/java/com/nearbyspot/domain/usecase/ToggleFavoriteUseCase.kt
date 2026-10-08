package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.repository.PlaceRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(place: Place): Boolean {
        val wasFavorite = repository.isFavorite(place.placeId)
        if (wasFavorite) {
            repository.removeFavorite(place.placeId)
        } else {
            repository.saveFavorite(place)
        }
        return !wasFavorite
    }
}
