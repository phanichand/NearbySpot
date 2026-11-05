package com.nearbyspot.presentation.route

import com.google.android.gms.maps.model.LatLng
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.Category
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.RouteInfo
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.domain.model.RouteSearchProgress
import com.nearbyspot.domain.model.RouteSearchFailure
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.domain.usecase.ComputeRouteUseCase
import com.nearbyspot.domain.usecase.GetRouteSectionsUseCase
import com.nearbyspot.domain.usecase.SectionPlanner
import com.nearbyspot.domain.usecase.SectionStability
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
    private val getRouteSectionsUseCase: GetRouteSectionsUseCase,
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
    private val sectionJobs = mutableMapOf<Int, Job>()
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
        repository.beginTrip()
        _uiState.update {
            it.copy(
                destinationName = name,
                destinationLatLng = LatLng(lat, lng),
                routeInfo = null,
                currentPositionOnRoute = null,
                currentRoutePointIndex = 0,
                sections = emptyList(),
                expandedSectionIndex = null,
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
        repository.beginTrip()
        _uiState.update {
            it.copy(
                filterSettings = it.filterSettings.copy(transportMode = mode),
                routeInfo = null,
                currentPositionOnRoute = null,
                currentRoutePointIndex = 0,
                sections = emptyList(),
                expandedSectionIndex = null,
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
        searchAlongRoute(stabilize = true)
    }

    fun onSectionExpanded(index: Int) {
        val state = _uiState.value
        val section = state.sections.firstOrNull { it.index == index } ?: return
        _uiState.update { it.copy(expandedSectionIndex = index) }
        if (section.status != SectionStatus.NOT_LOADED && section.status != SectionStatus.FAILED) return
        if (sectionJobs[index]?.isActive == true) return
        val routeInfo = state.routeInfo ?: return
        val generation = routeGeneration
        val placeTypes = state.selectedCategories.flatMap { it.placeTypes }.distinct()
        val filters = state.filterSettings

        updateForRouteGeneration(generation) { it.withSection(section.copy(status = SectionStatus.LOADING)) }
        sectionJobs[index] = viewModelScope.launch {
            val loaded = fetchSection(routeInfo, section, placeTypes, filters)
            updateForRouteGeneration(generation) { it.withSection(loaded) }
        }
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
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Unable to get your location. Please try again.")
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
                        searchAlongRoute(expectedRouteGeneration = generation, stabilize = true)
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
            } catch (_: Exception) {
                updateForRouteGeneration(generation) {
                    it.copy(
                        isLoadingRoute = false,
                        errorMessage = "Unable to calculate this route. Please try again.",
                    )
                }
            }
        }
    }

    /**
     * Re-plans the sections ahead and loads the first one (and the expanded one, if any). A refresh
     * caused by movement or time passes [stabilize] so shown picks only change for a clearly better
     * one; a changed category or filter replaces them outright.
     */
    private fun searchAlongRoute(
        expectedRouteGeneration: Long = routeGeneration,
        stabilize: Boolean = false,
    ) {
        if (expectedRouteGeneration != routeGeneration) return

        val state = _uiState.value
        val routeInfo = state.routeInfo ?: return
        state.currentPositionOnRoute ?: state.userLatLng ?: return
        val planned = SectionPlanner.plan(
            routeInfo = routeInfo,
            currentRouteIndex = state.currentRoutePointIndex,
            speedMetersPerSecond = null,
        )
        if (planned.isEmpty()) return
        val placeTypes = state.selectedCategories.flatMap { it.placeTypes }.distinct()
        val filters = state.filterSettings
        val generation = ++searchGeneration
        val isRefresh = state.sections.any { it.picks.isNotEmpty() }
        val previousByIndex = state.sections.associateBy { it.index }
        val sections = planned.map { fresh ->
            val previous = previousByIndex[fresh.index]
                ?.takeIf { it.picks.isNotEmpty() && (stabilize || fresh.index == 0) }
            previous?.copy(
                startRouteIndex = fresh.startRouteIndex,
                endRouteIndex = fresh.endRouteIndex,
                aheadSeconds = fresh.aheadSeconds,
            ) ?: fresh
        }
        val expandedIndex = state.expandedSectionIndex?.takeIf { index -> sections.any { it.index == index } }

        searchJob?.cancel()
        cancelSectionJobs()
        lastSearchAttemptLatLng = state.currentPositionOnRoute ?: state.userLatLng
        lastSearchAttemptTimeMs = System.currentTimeMillis()
        updateForSearchGeneration(expectedRouteGeneration, generation) {
            it.copy(
                sections = sections,
                expandedSectionIndex = expandedIndex,
                alongRoutePlaces = candidatesOf(sections),
                routeSearchProgress = RouteSearchProgress.Planning,
                routeSearchFailure = null,
                isRefreshingPlaces = isRefresh,
                errorMessage = null,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val first = sections.first()
                val loadedFirst = fetchSection(routeInfo, first, placeTypes, filters)
                if (!isCurrentSearch(expectedRouteGeneration, generation)) return@launch
                updateForSearchGeneration(expectedRouteGeneration, generation) { current ->
                    current.withLoadedSection(first, loadedFirst, stabilize).copy(
                        routeSearchProgress = null,
                        isRefreshingPlaces = false,
                        routeSearchFailure = loadedFirst.failure,
                        errorMessage = loadedFirst.failure?.let(::messageFor),
                    )
                }

                val expanded = sections.firstOrNull { it.index == expandedIndex && it.index != first.index }
                if (expanded != null) {
                    val loadedExpanded = fetchSection(routeInfo, expanded, placeTypes, filters)
                    updateForSearchGeneration(expectedRouteGeneration, generation) {
                        it.withLoadedSection(expanded, loadedExpanded, stabilize)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
    }

    private suspend fun fetchSection(
        routeInfo: RouteInfo,
        section: RouteSection,
        placeTypes: List<String>,
        filters: RouteFilterSettings,
    ): RouteSection = try {
        getRouteSectionsUseCase(routeInfo, section, placeTypes, filters)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        section.copy(
            status = SectionStatus.FAILED,
            picks = emptyList(),
            failure = RouteSearchFailure.SERVICE_UNAVAILABLE,
        )
    }

    /** Applies a freshly loaded section, keeping what was shown when the load failed or must stay stable. */
    private fun RouteUiState.withLoadedSection(
        requested: RouteSection,
        loaded: RouteSection,
        stabilize: Boolean,
    ): RouteUiState {
        val shown = sections.firstOrNull { it.index == requested.index }
        val result = when {
            shown != null && shown.picks.isNotEmpty() && loaded.status == SectionStatus.FAILED -> shown
            shown != null && stabilize && shown.picks.isNotEmpty() -> SectionStability.merge(shown, loaded)
            else -> loaded
        }
        return withSection(result)
    }

    private fun RouteUiState.withSection(section: RouteSection): RouteUiState {
        val replaced = SectionPlanner.mergeEmpty(
            sections.map { if (it.index == section.index) section else it },
        )
        return copy(sections = replaced, alongRoutePlaces = candidatesOf(replaced))
    }

    private fun candidatesOf(sections: List<RouteSection>) = sections
        .flatMap { it.picks }
        .map { it.candidate }
        .distinctBy { it.place.placeId }

    private fun messageFor(failure: RouteSearchFailure): String = when (failure) {
        RouteSearchFailure.ALL_ANCHORS_FAILED ->
            "Couldn't search stops along this route. Please try again."
        RouteSearchFailure.DESTINATION_TRAVEL_TIME_UNAVAILABLE ->
            "Couldn't verify travel times. Please try again."
        RouteSearchFailure.TIMED_OUT ->
            "Route stop search timed out. Please try again."
        RouteSearchFailure.SERVICE_UNAVAILABLE ->
            "Route search is temporarily unavailable. Please try again."
        RouteSearchFailure.MONTHLY_LIMIT_REACHED ->
            "You've reached this month's search limit. It resets next month."
        RouteSearchFailure.TRIP_LIMIT_REACHED ->
            "You've used this trip's search allowance. Pick a new destination to start again."
    }

    private fun cancelSectionJobs() {
        sectionJobs.values.forEach { it.cancel() }
        sectionJobs.clear()
    }

    override fun onCleared() {
        routeJob?.cancel()
        searchJob?.cancel()
        cancelSectionJobs()
        locationTrackingJob?.cancel()
        super.onCleared()
    }

    private fun invalidateRouteWork(): Long {
        routeGeneration++
        searchGeneration++
        routeJob?.cancel()
        searchJob?.cancel()
        cancelSectionJobs()
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

    companion object {
        private const val REFRESH_DISTANCE_METERS = 5000.0
        private const val REFRESH_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
