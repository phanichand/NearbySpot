package com.nearbyspot.presentation.route

import androidx.compose.runtime.Immutable
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSearchProgress
import com.nearbyspot.domain.model.RouteSearchFailure

@Immutable
data class RouteUiState(
    val isLoadingRoute: Boolean = false,
    val routeSearchProgress: RouteSearchProgress? = null,
    val routeSearchFailure: RouteSearchFailure? = null,
    val isRefreshingPlaces: Boolean = false,
    val routeInfo: RouteInfo? = null,
    val destinationName: String = "",
    val destinationLatLng: LatLng? = null,
    val userLatLng: LatLng? = null,
    val currentPositionOnRoute: LatLng? = null,
    val currentRoutePointIndex: Int = 0,
    val selectedCategories: Set<Category> = setOf(Category.RESTAURANT),
    val filterSettings: RouteFilterSettings = RouteFilterSettings(),
    val alongRoutePlaces: List<AlongRoutePlace> = emptyList(),
    val highlightedPlaceId: String? = null,
    val selectedPlace: AlongRoutePlace? = null,
    val errorMessage: String? = null,
    val locationPermissionGranted: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
) {
    val isLoadingPlaces: Boolean
        get() = routeSearchProgress != null
}
