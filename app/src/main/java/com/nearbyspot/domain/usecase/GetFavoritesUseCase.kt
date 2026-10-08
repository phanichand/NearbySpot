package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.repository.PlaceRepository
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(): List<Place> {
        return repository.getFavorites()
    }
}
