package com.nearbyspot.presentation.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.nearbyspot.presentation.components.PlaceBubble
import com.nearbyspot.presentation.detail.PlaceDetailSheet
import com.nearbyspot.presentation.filter.FilterBar
import kotlinx.coroutines.launch

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

    if (!state.locationPermissionGranted && state.userLatitude == null) {
        PermissionRequiredScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        )
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(state.userLatitude ?: 0.0, state.userLongitude ?: 0.0),
            14f,
        )
    }

    LaunchedEffect(state.userLatitude, state.userLongitude) {
        val lat = state.userLatitude ?: return@LaunchedEffect
        val lng = state.userLongitude ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14f)
        )
    }

    LaunchedEffect(state.highlightedPlaceId) {
        val highlighted = state.rankedPlaces.find { it.place.placeId == state.highlightedPlaceId }
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
    val hasActiveFilters = state.filterSettings != FilterSettings()
    val hasHighlight = state.highlightedPlaceId != null

    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val darkMapStyle = remember(isDarkTheme) {
        if (isDarkTheme) {
            runCatching {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            }.getOrNull()
        } else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = state.locationPermissionGranted,
                mapStyleOptions = darkMapStyle,
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
            ),
            onMapClick = {
                viewModel.onPlaceHighlighted(null)
            },
        ) {
            state.rankedPlaces.forEach { rankedPlace ->
                val isHighlighted = rankedPlace.place.placeId == state.highlightedPlaceId
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            rankedPlace.place.latitude,
                            rankedPlace.place.longitude,
                        )
                    ),
                    title = rankedPlace.place.name,
                    snippet = "${rankedPlace.place.rating} - ${
                        rankedPlace.travelInfo.durationMinutes.toInt()
                    } min${if (rankedPlace.place.priceLevel != null) " - ${"$".repeat(rankedPlace.place.priceLevel)}" else ""}",
                    icon = if (isHighlighted) {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    },
                    zIndex = if (isHighlighted) 1f else 0f,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onPlaceHighlighted(rankedPlace.place.placeId)
                        val idx = state.rankedPlaces.indexOf(rankedPlace)
                        if (idx >= 0) {
                            scope.launch { bubbleListState.animateScrollToItem(idx) }
                        }
                        true
                    },
                )
            }
        }

        // Top overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Explore nearby",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Pick what sounds good",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                val latitude = state.userLatitude
                                val longitude = state.userLongitude
                                if (latitude != null && longitude != null) {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(latitude, longitude),
                                                14f,
                                            ),
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "Center on my location",
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToSaved()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Saved places",
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showFilters = !showFilters
                            },
                        ) {
                            if (hasActiveFilters) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(8.dp),
                                        )
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Toggle filters (active)",
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "Toggle filters",
                                )
                            }
                        }
                    }

                    CategoryChipRow(
                        selectedCategories = state.selectedCategories,
                        onCategoryToggled = { cat ->
                            viewModel.onCategoryToggled(cat)
                            viewModel.onPlaceHighlighted(null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )

                    AnimatedVisibility(
                        visible = showFilters,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                    ) {
                        FilterBar(
                            filterSettings = state.filterSettings,
                            onTravelTimeChanged = viewModel::onTravelTimeChanged,
                            onTransportModeChanged = viewModel::onTransportModeChanged,
                            onPriceLevelChanged = viewModel::onPriceLevelChanged,
                            onOpenNowToggled = viewModel::onOpenNowToggled,
                            onSortChanged = viewModel::onSortChanged,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Animated content switch between loading / empty / results
            AnimatedContent(
                targetState = ResultState.from(state),
                transitionSpec = { fadeIn(initialAlpha = 0.3f) togetherWith fadeOut() },
                label = "resultsTransition",
            ) { resultState ->
                when (resultState) {
                    ResultState.Loading -> {
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
                                    text = "Finding popular places...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    ResultState.Empty -> {
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
                                    text = "No popular places found",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Try a different category or increase the travel time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = { showFilters = true }) {
                                    Text("Adjust Filters")
                                }
                            }
                        }
                    }

                    is ResultState.Results -> {
                        LazyRow(
                            state = bubbleListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                items = resultState.places,
                                key = { _, item -> item.place.placeId },
                            ) { _, rankedPlace ->
                                PlaceBubble(
                                    rankedPlace = rankedPlace,
                                    isHighlighted = rankedPlace.place.placeId == state.highlightedPlaceId,
                                    onClick = {
                                        viewModel.onPlaceHighlighted(rankedPlace.place.placeId)
                                    },
                                    onLongClick = {
                                        viewModel.onPlaceSelected(rankedPlace)
                                        showDetail = true
                                    },
                                )
                            }
                        }
                    }
                    }
                }
            }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToRoute()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Plan a route")
                }

                AnimatedVisibility(
                    visible = hasHighlight,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    val highlighted = state.rankedPlaces.find {
                        it.place.placeId == state.highlightedPlaceId
                    }
                    if (highlighted != null) {
                        Button(
                            onClick = {
                                viewModel.onPlaceSelected(highlighted)
                                showDetail = true
                            },
                        ) {
                            Text("View details")
                        }
                    }
                }
            }
        }

        // Snackbar well above bottom controls
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
        )
    }
}

private sealed interface ResultState {
    data object Loading : ResultState
    data object Empty : ResultState
    data class Results(val places: List<RankedPlace>) : ResultState

    companion object {
        fun from(state: HomeUiState): ResultState = when {
            state.isLoading -> Loading
            state.rankedPlaces.isEmpty() && state.userLatitude != null -> Empty
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
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Discover what's nearby",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "NearbySpot needs your location to find popular restaurants, cafes, and attractions around you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.width(220.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable Location")
            }
        }
    }
}
