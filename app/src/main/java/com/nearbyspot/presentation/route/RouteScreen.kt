package com.nearbyspot.presentation.route

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.nearbyspot.R
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.presentation.components.CategoryChipRow
import com.nearbyspot.presentation.components.DestinationSearchBar
import com.nearbyspot.presentation.components.MapHeaderCard
import com.nearbyspot.presentation.components.MapResultsTray
import com.nearbyspot.presentation.components.PlaceCard
import com.nearbyspot.presentation.components.PlaceCarousel
import com.nearbyspot.presentation.components.StatusMessage
import com.nearbyspot.presentation.detail.PlaceDetailSheet
import com.nearbyspot.presentation.filter.FilterSheet
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_ZOOM = 12f
private const val HIGHLIGHT_ZOOM = 15f
private const val HIGHLIGHT_ANIMATION_MS = 500
private const val ROUTE_FIT_ANIMATION_MS = 800
private const val ROUTE_BOUNDS_PADDING_PX = 100
private const val ROUTE_AHEAD_WIDTH_PX = 14f
private const val ROUTE_BEHIND_WIDTH_PX = 10f
private const val SECONDS_PER_MINUTE = 60
private const val METERS_PER_KM = 1000.0

@Composable
fun RouteScreen(
    placesClient: PlacesClient,
    onNavigateBack: () -> Unit,
    viewModel: RouteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissError()
        }
    }

    var showDetail by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val hasActiveFilters = state.filterSettings != RouteFilterSettings()

    if (showDetail && state.selectedPlace != null) {
        val arPlace = state.selectedPlace!!
        val reachMinutes = arPlace.reachableMinutes.roundToInt()
        PlaceDetailSheet(
            place = arPlace.place,
            travelMinutes = reachMinutes,
            travelLabel = stringResource(R.string.place_minutes_away, reachMinutes),
            distanceMeters = 0,
            detourLabel = stringResource(R.string.place_detour, arPlace.detourMinutes.roundToInt()),
            onDismiss = {
                showDetail = false
                viewModel.onPlaceSelected(null)
            },
            photoProvider = viewModel.photoProvider,
            isFavorite = state.favoriteIds.contains(arPlace.place.placeId),
            onToggleFavorite = { viewModel.onToggleFavorite(arPlace.place) },
            onFetchReviews = viewModel::fetchReviews,
        )
    }

    if (showFilters) {
        FilterSheet(
            onDismiss = { showFilters = false },
            onReset = viewModel::onFiltersReset,
            resetEnabled = hasActiveFilters,
        ) {
            RouteFilterBar(
                filterSettings = state.filterSettings,
                onMaxDetourChanged = viewModel::onMaxDetourChanged,
                onTransportModeChanged = viewModel::onTransportModeChanged,
                onPriceLevelChanged = viewModel::onPriceLevelChanged,
                onOpenNowToggled = viewModel::onOpenNowToggled,
            )
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            state.userLatLng ?: LatLng(0.0, 0.0),
            DEFAULT_ZOOM,
        )
    }

    LaunchedEffect(state.routeInfo) {
        val routeInfo = state.routeInfo ?: return@LaunchedEffect
        if (routeInfo.decodedPoints.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                routeInfo.decodedPoints.forEach { include(it) }
            }.build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_BOUNDS_PADDING_PX),
                durationMs = ROUTE_FIT_ANIMATION_MS,
            )
        }
    }

    LaunchedEffect(state.highlightedPlaceId) {
        val highlighted = state.alongRoutePlaces.find {
            it.place.placeId == state.highlightedPlaceId
        }
        if (highlighted != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(highlighted.place.latitude, highlighted.place.longitude),
                    HIGHLIGHT_ZOOM,
                ),
                durationMs = HIGHLIGHT_ANIMATION_MS,
            )
        }
    }

    val carouselState = rememberLazyListState()

    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val darkMapStyle = remember(isDarkTheme) {
        if (isDarkTheme) {
            runCatching {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            }.getOrNull()
        } else {
            null
        }
    }
    val routeAheadColor = MaterialTheme.colorScheme.primary
    val routeBehindColor = MaterialTheme.colorScheme.outline
    val startLabel = stringResource(R.string.route_start)
    val destinationFallback = stringResource(R.string.route_destination)
    val destinationLabel = state.destinationName.ifBlank { destinationFallback }
    val youLabel = stringResource(R.string.route_you)

    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var trayHeightPx by remember { mutableIntStateOf(0) }
    val mapPadding = with(density) {
        PaddingValues(top = headerHeightPx.toDp(), bottom = trayHeightPx.toDp())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = mapPadding,
            properties = MapProperties(
                isMyLocationEnabled = state.locationPermissionGranted,
                mapStyleOptions = darkMapStyle,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { viewModel.onPlaceHighlighted(null) },
        ) {
            state.routeInfo?.let { routeInfo ->
                val routeIndex = state.currentRoutePointIndex
                val points = routeInfo.decodedPoints

                if (routeIndex > 0 && routeIndex < points.size) {
                    Polyline(
                        points = points.subList(0, routeIndex + 1),
                        color = routeBehindColor,
                        width = ROUTE_BEHIND_WIDTH_PX,
                    )
                    Polyline(
                        points = points.subList(routeIndex, points.size),
                        color = routeAheadColor,
                        width = ROUTE_AHEAD_WIDTH_PX,
                    )
                } else {
                    Polyline(
                        points = points,
                        color = routeAheadColor,
                        width = ROUTE_AHEAD_WIDTH_PX,
                    )
                }

                if (points.isNotEmpty()) {
                    Marker(
                        state = MarkerState(position = points.first()),
                        title = startLabel,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        zIndex = 2f,
                    )
                    Marker(
                        state = MarkerState(position = points.last()),
                        title = destinationLabel,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                        zIndex = 2f,
                    )
                }

                state.currentPositionOnRoute?.let { pos ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = youLabel,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                        zIndex = 3f,
                    )
                }
            }

            state.alongRoutePlaces.forEachIndexed { index, arPlace ->
                val isHighlighted = arPlace.place.placeId == state.highlightedPlaceId
                Marker(
                    state = MarkerState(
                        position = LatLng(arPlace.place.latitude, arPlace.place.longitude),
                    ),
                    title = arPlace.place.name,
                    icon = if (isHighlighted) {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    },
                    zIndex = if (isHighlighted) 1f else 0f,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onPlaceHighlighted(arPlace.place.placeId)
                        scope.launch { carouselState.animateScrollToItem(index) }
                        true
                    },
                )
            }
        }

        MapHeaderCard(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height },
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                DestinationSearchBar(
                    placesClient = placesClient,
                    selectedName = state.destinationName,
                    onDestinationSelected = { result ->
                        viewModel.onDestinationSelected(
                            result.name,
                            result.latLng.latitude,
                            result.latLng.longitude,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            CategoryChipRow(
                selectedCategories = state.selectedCategories,
                onCategoryToggled = viewModel::onCategoryToggled,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val resultState = RouteResultState.from(state)
        MapResultsTray(
            title = stringResource(
                if (state.routeInfo == null) R.string.route_idle_title else R.string.route_results_title,
            ),
            subtitle = state.routeInfo?.let { routeInfo ->
                stringResource(
                    R.string.route_summary,
                    String.format(Locale.getDefault(), "%.1f", routeInfo.distanceMeters / METERS_PER_KM),
                    routeInfo.durationSeconds / SECONDS_PER_MINUTE,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { trayHeightPx = it.height },
            actions = {
                if (state.routeInfo != null) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onRefreshPlaces()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh_places),
                        )
                    }
                }
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(
                        badge = {
                            if (hasActiveFilters) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(
                                if (hasActiveFilters) R.string.action_filters_active else R.string.action_filters,
                            ),
                        )
                    }
                }
            },
        ) {
            AnimatedContent(
                targetState = resultState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "routeResults",
            ) { target ->
                when (target) {
                    RouteResultState.Idle -> StatusMessage(
                        title = stringResource(R.string.route_idle_body),
                        icon = Icons.Filled.TravelExplore,
                    )

                    RouteResultState.Loading -> StatusMessage(
                        title = stringResource(
                            if (state.isLoadingRoute) R.string.route_loading_route else R.string.route_loading_places,
                        ),
                        isLoading = true,
                    )

                    RouteResultState.Empty -> StatusMessage(
                        title = stringResource(R.string.route_empty_title),
                        body = stringResource(R.string.route_empty_body),
                        icon = Icons.Filled.SearchOff,
                        actionLabel = stringResource(R.string.action_adjust_filters),
                        onAction = { showFilters = true },
                    )

                    is RouteResultState.Results -> PlaceCarousel(
                        items = target.places,
                        itemKey = { it.place.placeId },
                        state = carouselState,
                    ) { index, arPlace, itemModifier ->
                        PlaceCard(
                            place = arPlace.place,
                            rank = index + 1,
                            travelMinutes = arPlace.reachableMinutes.roundToInt(),
                            detourMinutes = arPlace.detourMinutes.roundToInt(),
                            isHighlighted = arPlace.place.placeId == state.highlightedPlaceId,
                            onClick = {
                                viewModel.onPlaceHighlighted(arPlace.place.placeId)
                                viewModel.onPlaceSelected(arPlace)
                                showDetail = true
                            },
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { trayHeightPx.toDp() } + 8.dp),
        )
    }
}

private sealed interface RouteResultState {
    data object Idle : RouteResultState
    data object Loading : RouteResultState
    data object Empty : RouteResultState
    data class Results(val places: List<AlongRoutePlace>) : RouteResultState

    companion object {
        fun from(state: RouteUiState): RouteResultState = when {
            state.isLoadingRoute || state.isLoadingPlaces -> Loading
            state.routeInfo == null -> Idle
            state.alongRoutePlaces.isEmpty() -> Empty
            else -> Results(state.alongRoutePlaces)
        }
    }
}
