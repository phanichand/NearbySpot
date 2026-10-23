package com.nearbyspot.presentation.route

import com.google.android.gms.maps.model.LatLng
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.usecase.ComputeRouteUseCase
import com.nearbyspot.domain.usecase.GetAlongRoutePlacesUseCase
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import com.nearbyspot.util.PolylineUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val computeRouteUseCase: ComputeRouteUseCase,
    private val getAlongRoutePlacesUseCase: GetAlongRoutePlacesUseCase,
    private val locationHelper: LocationHelper,
    val photoProvider: PlacePhotoProvider,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private var locationTrackingJob: Job? = null
    private var searchJob: Job? = null
    private var lastSearchLatLng: LatLng? = null
    private var lastSearchTimeMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids.toSet()) }
            }
        }
    }

    suspend fun fetchReviews(placeId: String): List<com.nearbyspot.domain.model.Review> {
        return repository.fetchReviews(placeId)
    }

    fun onToggleFavorite(place: com.nearbyspot.domain.model.Place) {
        viewModelScope.launch {
            toggleFavoriteUseCase(place)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted) fetchLocation()
    }

    fun onDestinationSelected(name: String, lat: Double, lng: Double) {
        _uiState.update {
            it.copy(
                destinationName = name,
                destinationLatLng = LatLng(lat, lng),
            )
        }
        computeRouteAndSearch()
    }

    fun onCategoryToggled(category: Category) {
        _uiState.update {
            val current = it.selectedCategories
            val updated = if (category in current && current.size > 1) {
                current - category
            } else {
                current + category
            }
            it.copy(selectedCategories = updated, highlightedPlaceId = null)
        }
        searchAlongRoute()
    }

    fun onMaxDetourChanged(minutes: Int) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(maxDetourMinutes = minutes))
        }
        searchAlongRoute()
    }

    fun onTransportModeChanged(mode: TransportMode) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(transportMode = mode))
        }
        computeRouteAndSearch()
    }

    fun onPriceLevelChanged(maxPrice: Int?) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(maxPriceLevel = maxPrice))
        }
        searchAlongRoute()
    }

    fun onOpenNowToggled(enabled: Boolean) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(openNowOnly = enabled))
        }
        searchAlongRoute()
    }

    fun onFiltersReset() {
        _uiState.update { it.copy(filterSettings = RouteFilterSettings()) }
        computeRouteAndSearch()
    }

    fun onPlaceHighlighted(placeId: String?) {
        _uiState.update { it.copy(highlightedPlaceId = placeId) }
    }

    fun onPlaceSelected(place: AlongRoutePlace?) {
        _uiState.update { it.copy(selectedPlace = place) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onRefreshPlaces() {
        searchAlongRoute()
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                _uiState.update {
                    it.copy(userLatLng = LatLng(location.latitude, location.longitude))
                }
                if (_uiState.value.destinationLatLng != null && _uiState.value.routeInfo == null) {
                    computeRouteAndSearch()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Unable to get your location: ${e.message}")
                }
            }
        }
    }

    private fun startLiveTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = viewModelScope.launch {
            locationHelper.observeLocation(intervalMs = 10_000L)
                .catch { /* ignore location errors during tracking */ }
                .collect { location ->
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    val state = _uiState.value
                    val routeInfo = state.routeInfo ?: return@collect

                    val routeIndex = PolylineUtils.findPositionOnRoute(
                        routeInfo.decodedPoints, currentLatLng,
                    )

                    _uiState.update {
                        it.copy(
                            userLatLng = currentLatLng,
                            currentPositionOnRoute = currentLatLng,
                            currentRoutePointIndex = routeIndex,
                        )
                    }

                    val now = System.currentTimeMillis()
                    val distanceMoved = lastSearchLatLng?.let {
                        PolylineUtils.haversineDistance(currentLatLng, it)
                    } ?: Double.MAX_VALUE
                    val timeSinceLastSearch = now - lastSearchTimeMs

                    if (distanceMoved > REFRESH_DISTANCE_METERS && timeSinceLastSearch > REFRESH_MIN_INTERVAL_MS) {
                        searchAlongRoute()
                    }
                }
        }
    }

    private fun computeRouteAndSearch() {
        val state = _uiState.value
        val userLatLng = state.userLatLng ?: return
        val destLatLng = state.destinationLatLng ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingRoute = true, errorMessage = null, alongRoutePlaces = emptyList())
            }
            try {
                val routeInfo = computeRouteUseCase(
                    originLat = userLatLng.latitude,
                    originLng = userLatLng.longitude,
                    destLat = destLatLng.latitude,
                    destLng = destLatLng.longitude,
                    mode = state.filterSettings.transportMode,
                )

                val routeIndex = PolylineUtils.findPositionOnRoute(
                    routeInfo.decodedPoints, userLatLng,
                )

                _uiState.update {
                    it.copy(
                        routeInfo = routeInfo,
                        isLoadingRoute = false,
                        currentPositionOnRoute = userLatLng,
                        currentRoutePointIndex = routeIndex,
                    )
                }

                startLiveTracking()
                searchAlongRoute()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        errorMessage = "Failed to compute route: ${e.message}",
                    )
                }
            }
        }
    }

    private fun searchAlongRoute() {
        val state = _uiState.value
        val routeInfo = state.routeInfo ?: return
        val currentLatLng = state.currentPositionOnRoute ?: state.userLatLng ?: return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPlaces = true, errorMessage = null) }
            try {
                val places = getAlongRoutePlacesUseCase(
                    routeInfo = routeInfo,
                    currentLatLng = currentLatLng,
                    currentRouteIndex = state.currentRoutePointIndex,
                    placeTypes = state.selectedCategories.flatMap { it.placeTypes }.distinct(),
                    filters = state.filterSettings,
                )
                lastSearchLatLng = currentLatLng
                lastSearchTimeMs = System.currentTimeMillis()
                _uiState.update {
                    it.copy(isLoadingPlaces = false, alongRoutePlaces = places)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPlaces = false,
                        errorMessage = "Search failed: ${e.message}",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTrackingJob?.cancel()
    }

    companion object {
        private const val REFRESH_DISTANCE_METERS = 5000.0
        private const val REFRESH_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
