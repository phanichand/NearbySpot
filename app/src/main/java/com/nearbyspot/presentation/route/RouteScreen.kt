package com.nearbyspot.presentation.route

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.nearbyspot.domain.model.RouteSearchProgress
import com.google.maps.android.compose.rememberCameraPositionState
import com.nearbyspot.R
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.presentation.components.CategoryChipRow
import com.nearbyspot.presentation.components.DestinationSearchBar
import com.nearbyspot.presentation.detail.PlaceDetailSheet
import com.nearbyspot.presentation.util.DurationFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
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
    var topOverlayHeightPx by remember { mutableStateOf(0) }
    var bottomOverlayHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val mapContentPadding = PaddingValues(
        top = with(density) { topOverlayHeightPx.toDp() } + 8.dp,
        bottom = with(density) { bottomOverlayHeightPx.toDp() } + 8.dp,
    )

    if (showDetail && state.selectedPlace != null) {
        val arPlace = state.selectedPlace!!
        PlaceDetailSheet(
            place = arPlace.place,
            travelMinutes = arPlace.reachableSeconds / 60,
            travelLabel = DurationFormatter.ahead(arPlace.reachableSeconds),
            distanceMeters = 0,
            detourLabel = DurationFormatter.detour(arPlace.detourSeconds),
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

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            state.userLatLng ?: LatLng(0.0, 0.0), 12f,
        )
    }

    LaunchedEffect(state.routeInfo, topOverlayHeightPx, bottomOverlayHeightPx) {
        val routeInfo = state.routeInfo
        if (routeInfo == null) {
            bottomOverlayHeightPx = 0
            return@LaunchedEffect
        }
        if (routeInfo.decodedPoints.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                routeInfo.decodedPoints.forEach { include(it) }
            }.build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 100),
                durationMs = 800,
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
                    15f,
                ),
                durationMs = 500,
            )
        }
    }

    val bubbleListState = rememberLazyListState()
    // After the user swipes the carousel, highlight the stop that settled into view so the map
    // follows the card. Ignore programmatic scrolls and stops that are already visible.
    LaunchedEffect(bubbleListState) {
        var wasScrolling = false
        snapshotFlow { bubbleListState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling) {
                val visibleKeys = bubbleListState.layoutInfo.visibleItemsInfo.map { it.key }
                val current = viewModel.uiState.value
                val alreadyVisible = current.highlightedPlaceId?.let { it in visibleKeys } == true
                val target = current.alongRoutePlaces
                    .getOrNull(bubbleListState.firstVisibleItemIndex)
                if (!alreadyVisible && target != null) {
                    viewModel.onPlaceHighlighted(target.place.placeId)
                }
            }
            wasScrolling = scrolling
        }
    }
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val darkMapStyle = remember(isDarkTheme) {
        if (isDarkTheme) {
            runCatching {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            }.getOrNull()
        } else null
    }
    val startMarkerTitle = stringResource(R.string.route_marker_start)
    val destinationMarkerTitle = stringResource(R.string.route_marker_destination)
    val userMarkerTitle = stringResource(R.string.route_marker_you)

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = mapContentPadding,
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

                Polyline(
                    points = routeInfo.decodedPoints,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    width = 20f,
                    zIndex = 0f,
                )

                if (routeIndex > 0 && routeIndex < routeInfo.decodedPoints.size) {
                    val behindPoints = routeInfo.decodedPoints.subList(0, routeIndex + 1)
                    Polyline(
                        points = behindPoints,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        width = 10f,
                        zIndex = 1f,
                    )

                    val aheadPoints = routeInfo.decodedPoints.subList(routeIndex, routeInfo.decodedPoints.size)
                    Polyline(
                        points = aheadPoints,
                        color = MaterialTheme.colorScheme.primary,
                        width = 12f,
                        zIndex = 1f,
                    )
                } else {
                    Polyline(
                        points = routeInfo.decodedPoints,
                        color = MaterialTheme.colorScheme.primary,
                        width = 12f,
                        zIndex = 1f,
                    )
                }

                if (routeInfo.decodedPoints.isNotEmpty()) {
                    Marker(
                        state = MarkerState(position = routeInfo.decodedPoints.first()),
                        title = startMarkerTitle,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        zIndex = 2f,
                    )
                    Marker(
                        state = MarkerState(position = routeInfo.decodedPoints.last()),
                        title = state.destinationName.ifBlank { destinationMarkerTitle },
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                        zIndex = 2f,
                    )
                }

                state.currentPositionOnRoute?.let { pos ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = userMarkerTitle,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                        zIndex = 3f,
                    )
                }
            }

            state.alongRoutePlaces.forEach { arPlace ->
                val isHighlighted = arPlace.place.placeId == state.highlightedPlaceId
                Marker(
                    state = MarkerState(
                        position = LatLng(arPlace.place.latitude, arPlace.place.longitude)
                    ),
                    title = arPlace.place.name,
                    snippet = "${DurationFormatter.ahead(arPlace.reachableSeconds)}  \u00B7  " +
                        DurationFormatter.detour(arPlace.detourSeconds),
                    icon = if (isHighlighted) {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    },
                    zIndex = if (isHighlighted) 1f else 0f,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onPlaceHighlighted(arPlace.place.placeId)
                        val idx = state.alongRoutePlaces.indexOf(arPlace)
                        if (idx >= 0) {
                            scope.launch { bubbleListState.animateScrollToItem(idx) }
                        }
                        true
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .onSizeChanged { topOverlayHeightPx = it.height }
                .align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
            }

            CategoryChipRow(
                selectedCategories = state.selectedCategories,
                onCategoryToggled = { viewModel.onCategoryToggled(it) },
                chipContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            )

            Spacer(Modifier.height(8.dp))

            state.routeInfo?.let { routeInfo ->
                RouteSummaryCard(
                    distanceMeters = routeInfo.distanceMeters,
                    durationSeconds = routeInfo.durationSeconds,
                    resultCount = state.alongRoutePlaces.size,
                    isLoading = state.isLoadingPlaces,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onRefreshPlaces()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            AnimatedContent(
                targetState = RouteResultState.from(state),
                transitionSpec = { fadeIn(initialAlpha = 0.3f) togetherWith fadeOut() },
                label = "routeResults",
            ) { resultState ->
                when (resultState) {
                    RouteResultState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.route_idle_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    RouteResultState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (state.isLoadingRoute) {
                                        stringResource(R.string.route_computing)
                                    } else {
                                        when (val progress = state.routeSearchProgress) {
                                            RouteSearchProgress.Planning ->
                                                stringResource(R.string.route_planning_stops)
                                            is RouteSearchProgress.SearchingStops ->
                                                stringResource(
                                                    R.string.route_searching_areas,
                                                    progress.completed,
                                                    progress.total,
                                                )
                                            RouteSearchProgress.CalculatingTravelTimes ->
                                                stringResource(R.string.route_verifying_times)
                                            RouteSearchProgress.Complete, null ->
                                                stringResource(R.string.route_finding_stops)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    RouteResultState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .alpha(0.7f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.route_empty_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.route_empty_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = { showFilters = true }) {
                                    Text(stringResource(R.string.route_adjust_filters))
                                }
                            }
                        }
                    }

                    RouteResultState.Failure -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.route_failure_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = viewModel::onRefreshPlaces) {
                                    Text(stringResource(R.string.route_try_again))
                                }
                            }
                        }
                    }

                    is RouteResultState.Results -> Spacer(Modifier.height(4.dp))
                }
            }

        }

        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.route_filter_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(R.string.route_filter_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showFilters = false }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
                RouteFilterBar(
                    filterSettings = state.filterSettings,
                    onMaxDetourChanged = viewModel::onMaxDetourChanged,
                    onTransportModeChanged = viewModel::onTransportModeChanged,
                    onPriceLevelChanged = viewModel::onPriceLevelChanged,
                    onOpenNowToggled = viewModel::onOpenNowToggled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        val activeRouteInfo = state.routeInfo
        if (activeRouteInfo != null) {
            val highlighted = state.alongRoutePlaces.find {
                it.place.placeId == state.highlightedPlaceId
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomOverlayHeightPx = it.height },
            ) {
                if (state.alongRoutePlaces.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 3.dp,
                    ) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.route_results_title),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.route_results_subtitle),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "${state.alongRoutePlaces.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyRow(
                                state = bubbleListState,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                itemsIndexed(
                                    items = state.alongRoutePlaces,
                                    key = { _, item -> item.place.placeId },
                                ) { index, arPlace ->
                                    val fraction = routeStopFraction(
                                        arPlace.nearestRoutePointIndex,
                                        activeRouteInfo.decodedPoints.lastIndex,
                                    )
                                    val zoneLabel = when (routeStopZone(fraction)) {
                                        RouteStopZone.EARLY ->
                                            stringResource(R.string.route_zone_early)
                                        RouteStopZone.MIDDLE ->
                                            stringResource(R.string.route_zone_middle)
                                        RouteStopZone.DESTINATION -> stringResource(
                                            R.string.route_zone_destination,
                                            state.destinationName.ifBlank { destinationMarkerTitle },
                                        )
                                    }
                                    RouteStopCard(
                                        arPlace = arPlace,
                                        rank = index + 1,
                                        routeFraction = fraction,
                                        routeZoneLabel = zoneLabel,
                                        distanceAlongRouteMeters =
                                            (fraction * activeRouteInfo.distanceMeters).roundToInt(),
                                        isHighlighted = arPlace.place.placeId ==
                                            state.highlightedPlaceId,
                                        isFavorite = state.favoriteIds.contains(arPlace.place.placeId),
                                        onToggleFavorite = { viewModel.onToggleFavorite(arPlace.place) },
                                        onNavigate = {
                                            openNavigation(
                                                context = context,
                                                latitude = arPlace.place.latitude,
                                                longitude = arPlace.place.longitude,
                                            )
                                        },
                                        onClick = {
                                            viewModel.onPlaceHighlighted(arPlace.place.placeId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                RouteActionDock(
                    filtersVisible = showFilters,
                    activeFilterCount = activeRouteFilterCount(state.filterSettings),
                    highlightedPlaceName = highlighted?.place?.name,
                    onToggleFilters = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showFilters = !showFilters
                    },
                    onViewDetails = {
                        highlighted?.let {
                            viewModel.onPlaceSelected(it)
                            showDetail = true
                        }
                    },
                    modifier = if (highlighted == null) {
                        Modifier.align(Alignment.End)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp),
        )
    }
}

private sealed interface RouteResultState {
    data object Idle : RouteResultState
    data object Loading : RouteResultState
    data object Empty : RouteResultState
    data object Failure : RouteResultState
    data class Results(val places: List<AlongRoutePlace>) : RouteResultState

    companion object {
        fun from(state: RouteUiState): RouteResultState = when {
            state.alongRoutePlaces.isNotEmpty() -> Results(state.alongRoutePlaces)
            state.isLoadingRoute || state.isLoadingPlaces -> Loading
            state.routeInfo == null -> Idle
            state.routeSearchFailure != null -> Failure
            state.alongRoutePlaces.isEmpty() -> Empty
            else -> Empty
        }
    }
}

private fun openNavigation(context: Context, latitude: Double, longitude: Double) {
    val destination = "$latitude,$longitude"
    val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$destination"))
        .setPackage(GOOGLE_MAPS_PACKAGE)
    try {
        context.startActivity(navigation)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$destination")))
    }
}

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
