package com.nearbyspot.presentation.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.nearbyspot.R
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.presentation.components.CategoryChipRow
import com.nearbyspot.presentation.components.MapHeaderCard
import com.nearbyspot.presentation.components.MapResultsTray
import com.nearbyspot.presentation.components.PlaceCard
import com.nearbyspot.presentation.components.PlaceCarousel
import com.nearbyspot.presentation.components.StatusMessage
import com.nearbyspot.presentation.detail.PlaceDetailSheet
import com.nearbyspot.presentation.filter.FilterBar
import com.nearbyspot.presentation.filter.FilterSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_ZOOM = 14f
private const val HIGHLIGHT_ZOOM = 15f
private const val HIGHLIGHT_ANIMATION_MS = 500

@Composable
fun HomeScreen(
    onNavigateToRoute: () -> Unit = {},
    onNavigateToSaved: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
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
    val requestLocationPermission = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    LaunchedEffect(Unit) { requestLocationPermission() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissError()
        }
    }

    var showDetail by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val hasActiveFilters = state.filterSettings != FilterSettings()

    if (showDetail && state.selectedPlace != null) {
        val selectedRanked = state.selectedPlace!!
        PlaceDetailSheet(
            rankedPlace = selectedRanked,
            onDismiss = {
                showDetail = false
                viewModel.onPlaceSelected(null)
            },
            photoProvider = viewModel.photoProvider,
            isFavorite = state.favoriteIds.contains(selectedRanked.place.placeId),
            onToggleFavorite = { viewModel.onToggleFavorite(selectedRanked.place) },
            onFetchReviews = viewModel::fetchReviews,
        )
    }

    if (showFilters) {
        FilterSheet(
            onDismiss = { showFilters = false },
            onReset = { viewModel.onFilterChanged(FilterSettings()) },
            resetEnabled = hasActiveFilters,
        ) {
            FilterBar(
                filterSettings = state.filterSettings,
                onTravelTimeChanged = viewModel::onTravelTimeChanged,
                onTransportModeChanged = viewModel::onTransportModeChanged,
                onPriceLevelChanged = viewModel::onPriceLevelChanged,
                onOpenNowToggled = viewModel::onOpenNowToggled,
                onSortChanged = viewModel::onSortChanged,
            )
        }
    }

    if (!state.locationPermissionGranted && state.userLatitude == null) {
        PermissionRequiredScreen(onRequestPermission = requestLocationPermission)
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(state.userLatitude ?: 0.0, state.userLongitude ?: 0.0),
            DEFAULT_ZOOM,
        )
    }

    LaunchedEffect(state.userLatitude, state.userLongitude) {
        val lat = state.userLatitude ?: return@LaunchedEffect
        val lng = state.userLongitude ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), DEFAULT_ZOOM),
        )
    }

    LaunchedEffect(state.highlightedPlaceId) {
        val highlighted = state.rankedPlaces.find { it.place.placeId == state.highlightedPlaceId }
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

    // Overlay sizes feed the map padding so the camera, Google logo and my-location
    // button stay in the visible area between the header and the results tray.
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
                myLocationButtonEnabled = true,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = { viewModel.onPlaceHighlighted(null) },
        ) {
            state.rankedPlaces.forEachIndexed { index, rankedPlace ->
                val isHighlighted = rankedPlace.place.placeId == state.highlightedPlaceId
                Marker(
                    state = MarkerState(
                        position = LatLng(rankedPlace.place.latitude, rankedPlace.place.longitude),
                    ),
                    title = rankedPlace.place.name,
                    icon = if (isHighlighted) {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    },
                    zIndex = if (isHighlighted) 1f else 0f,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onPlaceHighlighted(rankedPlace.place.placeId)
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
                modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNavigateToRoute) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = stringResource(R.string.action_plan_route),
                    )
                }
                IconButton(onClick = onNavigateToSaved) {
                    Icon(
                        imageVector = Icons.Filled.Bookmarks,
                        contentDescription = stringResource(R.string.action_saved_places),
                    )
                }
                FiltersIconButton(
                    hasActiveFilters = hasActiveFilters,
                    onClick = { showFilters = true },
                )
            }
            Spacer(Modifier.height(4.dp))
            CategoryChipRow(
                selectedCategories = state.selectedCategories,
                onCategoryToggled = { category ->
                    viewModel.onCategoryToggled(category)
                    viewModel.onPlaceHighlighted(null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MapResultsTray(
            title = stringResource(R.string.home_results_title),
            subtitle = stringResource(
                R.string.home_results_summary,
                state.filterSettings.travelTimeMinutes,
                state.filterSettings.transportMode.label,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { trayHeightPx = it.height },
        ) {
            AnimatedContent(
                targetState = ResultState.from(state),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "resultsTransition",
            ) { resultState ->
                when (resultState) {
                    ResultState.Loading -> StatusMessage(
                        title = stringResource(R.string.home_loading),
                        isLoading = true,
                    )

                    ResultState.Empty -> StatusMessage(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        icon = Icons.Filled.SearchOff,
                        actionLabel = stringResource(R.string.action_adjust_filters),
                        onAction = { showFilters = true },
                    )

                    is ResultState.Results -> PlaceCarousel(
                        items = resultState.places,
                        itemKey = { it.place.placeId },
                        state = carouselState,
                    ) { index, rankedPlace, itemModifier ->
                        PlaceCard(
                            place = rankedPlace.place,
                            rank = index + 1,
                            travelMinutes = rankedPlace.travelInfo.durationMinutes.roundToInt(),
                            isHighlighted = rankedPlace.place.placeId == state.highlightedPlaceId,
                            onClick = {
                                viewModel.onPlaceHighlighted(rankedPlace.place.placeId)
                                viewModel.onPlaceSelected(rankedPlace)
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

@Composable
private fun FiltersIconButton(hasActiveFilters: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
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
}

private sealed interface ResultState {
    data object Loading : ResultState
    data object Empty : ResultState
    data class Results(val places: List<RankedPlace>) : ResultState

    companion object {
        fun from(state: HomeUiState): ResultState = when {
            state.isLoading -> Loading
            state.rankedPlaces.isNotEmpty() -> Results(state.rankedPlaces)
            else -> Empty
        }
    }
}

@Composable
private fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.permission_action))
            }
        }
    }
}
