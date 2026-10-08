package com.nearbyspot.presentation.route

import com.google.android.gms.maps.model.LatLng
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSearchProgress
import com.nearbyspot.domain.model.RouteSearchException
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.domain.usecase.ComputeRouteUseCase
import com.nearbyspot.domain.usecase.GetAlongRoutePlacesUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import com.nearbyspot.util.PolylineUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private var routeJob: Job? = null
    private var searchJob: Job? = null
    private var routeGeneration: Long = 0L
    private var searchGeneration: Long = 0L
    private var lastSearchAttemptLatLng: LatLng? = null
    private var lastSearchAttemptTimeMs: Long = 0L

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
        val generation = invalidateRouteWork()
        _uiState.update {
            it.copy(
                destinationName = name,
                destinationLatLng = LatLng(lat, lng),
                routeInfo = null,
                currentPositionOnRoute = null,
                currentRoutePointIndex = 0,
                alongRoutePlaces = emptyList(),
                highlightedPlaceId = null,
                selectedPlace = null,
                isLoadingRoute = false,
                routeSearchProgress = null,
                routeSearchFailure = null,
                isRefreshingPlaces = false,
                errorMessage = null,
            )
        }
        computeRouteAndSearch(generation)
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
        if (_uiState.value.filterSettings.transportMode == mode) return
        val generation = invalidateRouteWork()
        _uiState.update {
            it.copy(
                filterSettings = it.filterSettings.copy(transportMode = mode),
                routeInfo = null,
                currentPositionOnRoute = null,
                currentRoutePointIndex = 0,
                alongRoutePlaces = emptyList(),
                highlightedPlaceId = null,
                selectedPlace = null,
                isLoadingRoute = false,
                routeSearchProgress = null,
                routeSearchFailure = null,
                isRefreshingPlaces = false,
                errorMessage = null,
            )
        }
        computeRouteAndSearch(generation)
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
                    restartRouteForCurrentSelection()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Unable to get your location: ${e.message}")
                }
            }
        }
    }

    private fun startLiveTracking(
        generation: Long,
        routeInfo: RouteInfo,
    ) {
        locationTrackingJob?.cancel()
        locationTrackingJob = viewModelScope.launch {
            locationHelper.observeLocation(intervalMs = 10_000L)
                .catch { /* ignore location errors during tracking */ }
                .collect { location ->
                    if (generation != routeGeneration) return@collect

                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    val routeIndex = PolylineUtils.findPositionOnRoute(
                        routeInfo.decodedPoints, currentLatLng,
                    )

                    _uiState.update { state ->
                        if (generation != routeGeneration || state.routeInfo !== routeInfo) {
                            state
                        } else {
                            state.copy(
                                userLatLng = currentLatLng,
                                currentPositionOnRoute = currentLatLng,
                                currentRoutePointIndex = routeIndex,
                            )
                        }
                    }

                    if (_uiState.value.isLoadingPlaces || searchJob?.isActive == true) {
                        return@collect
                    }

                    val now = System.currentTimeMillis()
                    val distanceMoved = lastSearchAttemptLatLng?.let {
                        PolylineUtils.haversineDistance(currentLatLng, it)
                    } ?: Double.MAX_VALUE
                    val timeSinceLastSearch = now - lastSearchAttemptTimeMs

                    if (distanceMoved > REFRESH_DISTANCE_METERS && timeSinceLastSearch > REFRESH_MIN_INTERVAL_MS) {
                        searchAlongRoute(expectedRouteGeneration = generation)
                    }
                }
        }
    }

    private fun restartRouteForCurrentSelection() {
        val generation = invalidateRouteWork()
        computeRouteAndSearch(generation)
    }

    private fun computeRouteAndSearch(generation: Long) {
        val snapshot = _uiState.value
        val userLatLng = snapshot.userLatLng ?: return
        val destLatLng = snapshot.destinationLatLng ?: return
        val mode = snapshot.filterSettings.transportMode

        routeJob = viewModelScope.launch {
            updateForRouteGeneration(generation) {
                it.copy(
                    isLoadingRoute = true,
                    errorMessage = null,
                )
            }
            try {
                val routeInfo = computeRouteUseCase(
                    originLat = userLatLng.latitude,
                    originLng = userLatLng.longitude,
                    destLat = destLatLng.latitude,
                    destLng = destLatLng.longitude,
                    mode = mode,
                )

                if (generation != routeGeneration) return@launch

                val routeIndex = PolylineUtils.findPositionOnRoute(
                    routeInfo.decodedPoints, userLatLng,
                )

                updateForRouteGeneration(generation) {
                    it.copy(
                        routeInfo = routeInfo,
                        isLoadingRoute = false,
                        currentPositionOnRoute = userLatLng,
                        currentRoutePointIndex = routeIndex,
                    )
                }

                if (generation != routeGeneration) return@launch
                startLiveTracking(generation, routeInfo)
                searchAlongRoute(expectedRouteGeneration = generation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                updateForRouteGeneration(generation) {
                    it.copy(
                        isLoadingRoute = false,
                        errorMessage = "Failed to compute route: ${e.message}",
                    )
                }
            }
        }
    }

    private fun searchAlongRoute(expectedRouteGeneration: Long = routeGeneration) {
        if (expectedRouteGeneration != routeGeneration) return

        val state = _uiState.value
        val routeInfo = state.routeInfo ?: return
        val currentLatLng = state.currentPositionOnRoute ?: state.userLatLng ?: return
        val request = RouteSearchRequest(
            routeInfo = routeInfo,
            currentLatLng = currentLatLng,
            currentRouteIndex = state.currentRoutePointIndex,
            placeTypes = state.selectedCategories.flatMap { it.placeTypes }.distinct(),
            filters = state.filterSettings,
        )
        val generation = ++searchGeneration
        val isRefresh = state.alongRoutePlaces.isNotEmpty()

        searchJob?.cancel()
        lastSearchAttemptLatLng = request.currentLatLng
        lastSearchAttemptTimeMs = System.currentTimeMillis()
        updateForSearchGeneration(expectedRouteGeneration, generation) {
            it.copy(
                routeSearchProgress = RouteSearchProgress.Planning,
                routeSearchFailure = null,
                isRefreshingPlaces = isRefresh,
                errorMessage = null,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val places = getAlongRoutePlacesUseCase(
                    routeInfo = request.routeInfo,
                    currentLatLng = request.currentLatLng,
                    currentRouteIndex = request.currentRouteIndex,
                    placeTypes = request.placeTypes,
                    filters = request.filters,
                    onProgress = { progress ->
                        updateForSearchGeneration(expectedRouteGeneration, generation) {
                            it.copy(routeSearchProgress = progress)
                        }
                    },
                )

                if (!isCurrentSearch(expectedRouteGeneration, generation)) return@launch
                updateForSearchGeneration(expectedRouteGeneration, generation) {
                    it.copy(
                        routeSearchProgress = null,
                        routeSearchFailure = null,
                        isRefreshingPlaces = false,
                        alongRoutePlaces = places,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                val failure = (e as? RouteSearchException)?.failure
                    ?: RouteSearchFailure.SERVICE_UNAVAILABLE
                updateForSearchGeneration(expectedRouteGeneration, generation) {
                    it.copy(
                        routeSearchProgress = null,
                        routeSearchFailure = failure,
                        isRefreshingPlaces = false,
                        errorMessage = when (failure) {
                            RouteSearchFailure.ALL_ANCHORS_FAILED ->
                                "Couldn't search stops along this route. Please try again."
                            RouteSearchFailure.DESTINATION_TRAVEL_TIME_UNAVAILABLE ->
                                "Couldn't verify travel times. Please try again."
                            RouteSearchFailure.TIMED_OUT ->
                                "Route stop search timed out. Please try again."
                            RouteSearchFailure.SERVICE_UNAVAILABLE ->
                                "Route search is temporarily unavailable. Please try again."
                        },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        routeJob?.cancel()
        searchJob?.cancel()
        locationTrackingJob?.cancel()
        super.onCleared()
    }

    private fun invalidateRouteWork(): Long {
        routeGeneration++
        searchGeneration++
        routeJob?.cancel()
        searchJob?.cancel()
        locationTrackingJob?.cancel()
        return routeGeneration
    }

    private inline fun updateForRouteGeneration(
        generation: Long,
        transform: (RouteUiState) -> RouteUiState,
    ) {
        _uiState.update { state ->
            if (generation == routeGeneration) transform(state) else state
        }
    }

    private inline fun updateForSearchGeneration(
        expectedRouteGeneration: Long,
        generation: Long,
        transform: (RouteUiState) -> RouteUiState,
    ) {
        _uiState.update { state ->
            if (isCurrentSearch(expectedRouteGeneration, generation)) transform(state) else state
        }
    }

    private fun isCurrentSearch(expectedRouteGeneration: Long, generation: Long): Boolean =
        expectedRouteGeneration == routeGeneration && generation == searchGeneration

    private data class RouteSearchRequest(
        val routeInfo: RouteInfo,
        val currentLatLng: LatLng,
        val currentRouteIndex: Int,
        val placeTypes: List<String>,
        val filters: RouteFilterSettings,
    )

    companion object {
        private const val REFRESH_DISTANCE_METERS = 5000.0
        private const val REFRESH_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
