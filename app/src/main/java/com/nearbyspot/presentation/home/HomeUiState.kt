package com.nearbyspot.presentation.home

import androidx.compose.runtime.Immutable
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.RankedPlace

@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val selectedCategories: Set<Category> = setOf(Category.RESTAURANT),
    val filterSettings: FilterSettings = FilterSettings(),
    val rankedPlaces: List<RankedPlace> = emptyList(),
    val selectedPlace: RankedPlace? = null,
    val highlightedPlaceId: String? = null,
    val locationPermissionGranted: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
)
