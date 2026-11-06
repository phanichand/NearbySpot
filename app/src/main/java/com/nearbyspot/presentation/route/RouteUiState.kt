package com.nearbyspot.presentation.route

import androidx.compose.runtime.Immutable
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.usecase.SectionPlanner
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
    /** A start point chosen by the traveller; null means their current location. */
    val originName: String = "",
    val originLatLng: LatLng? = null,
    /** When the traveller leaves; null means now. */
    val departureTimeMillis: Long? = null,
    val userLatLng: LatLng? = null,
    val currentPositionOnRoute: LatLng? = null,
    val currentRoutePointIndex: Int = 0,
    val selectedCategories: Set<Category> = setOf(Category.RESTAURANT),
    val filterSettings: RouteFilterSettings = RouteFilterSettings(),
    val sections: List<RouteSection> = emptyList(),
    val expandedSectionIndex: Int? = null,
    val horizonSeconds: Int = SectionPlanner.HORIZON_SECONDS,
    val hasMoreSections: Boolean = false,
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
