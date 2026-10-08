package com.nearbyspot.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.usecase.GetNearbyPlacesUseCase
import com.nearbyspot.domain.usecase.GetTravelTimesUseCase
import com.nearbyspot.domain.usecase.RankPlacesUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.LocationHelper
import com.nearbyspot.util.PlacePhotoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNearbyPlaces: GetNearbyPlacesUseCase,
    private val getTravelTimes: GetTravelTimesUseCase,
    private val rankPlaces: RankPlacesUseCase,
    private val locationHelper: LocationHelper,
    val photoProvider: PlacePhotoProvider,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val repository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

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
        if (granted) {
            fetchLocation()
        }
    }

    fun onCategoryToggled(category: Category) {
        _uiState.update {
            val current = it.selectedCategories
            val updated = if (category in current && current.size > 1) {
                current - category
            } else {
                current + category
            }
            it.copy(selectedCategories = updated)
        }
        searchPlaces()
    }

    fun onFilterChanged(settings: FilterSettings) {
        _uiState.update { it.copy(filterSettings = settings) }
        searchPlaces()
    }

    fun onTravelTimeChanged(minutes: Int) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(travelTimeMinutes = minutes))
        }
        searchPlaces()
    }

    fun onTransportModeChanged(mode: TransportMode) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(transportMode = mode))
        }
        searchPlaces()
    }

    fun onPriceLevelChanged(maxPrice: Int?) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(maxPriceLevel = maxPrice))
        }
        searchPlaces()
    }

    fun onOpenNowToggled(enabled: Boolean) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(openNowOnly = enabled))
        }
        searchPlaces()
    }

    fun onSortChanged(sortOption: com.nearbyspot.domain.model.SortOption) {
        _uiState.update {
            it.copy(filterSettings = it.filterSettings.copy(sortBy = sortOption))
        }
        searchPlaces()
    }

    fun onPlaceSelected(place: RankedPlace?) {
        _uiState.update { it.copy(selectedPlace = place) }
    }

    fun onPlaceHighlighted(placeId: String?) {
        _uiState.update { it.copy(highlightedPlaceId = placeId) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun retry() {
        fetchLocation()
    }

    private fun fetchLocation() {
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                _uiState.update {
                    it.copy(
                        userLatitude = location.latitude,
                        userLongitude = location.longitude,
                    )
                }
                searchPlaces()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Unable to get your location: ${e.message}")
                }
            }
        }
    }

    private fun searchPlaces() {
        val state = _uiState.value
        val lat = state.userLatitude ?: return
        val lng = state.userLongitude ?: return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val combinedTypes = state.selectedCategories.flatMap { it.placeTypes }.distinct()
                val places = getNearbyPlaces(
                    latitude = lat,
                    longitude = lng,
                    placeTypes = combinedTypes,
                    travelTimeMinutes = state.filterSettings.travelTimeMinutes,
                    transportMode = state.filterSettings.transportMode,
                )

                if (places.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, rankedPlaces = emptyList())
                    }
                    return@launch
                }

                val preFiltered = if (places.size > MAX_MATRIX_CANDIDATES) {
                    val origin = com.google.android.gms.maps.model.LatLng(lat, lng)
                    places.sortedBy { place ->
                        com.nearbyspot.util.PolylineUtils.haversineDistance(
                            origin,
                            com.google.android.gms.maps.model.LatLng(place.latitude, place.longitude),
                        )
                    }.take(MAX_MATRIX_CANDIDATES)
                } else places

                val travelInfos = getTravelTimes(
                    originLat = lat,
                    originLng = lng,
                    places = preFiltered,
                    mode = state.filterSettings.transportMode,
                )

                val ranked = rankPlaces(preFiltered, travelInfos, state.filterSettings)

                _uiState.update {
                    it.copy(isLoading = false, rankedPlaces = ranked)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Search failed: ${e.message}",
                    )
                }
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MAX_MATRIX_CANDIDATES = 15
    }
}
