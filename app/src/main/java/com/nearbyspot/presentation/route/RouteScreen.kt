package com.nearbyspot.presentation.route

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.nearbyspot.presentation.components.CategoryChipRow
import com.nearbyspot.presentation.components.DestinationSearchBar
import com.nearbyspot.presentation.detail.PlaceDetailSheet
import com.nearbyspot.presentation.theme.StarYellow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    if (showDetail && state.selectedPlace != null) {
        val arPlace = state.selectedPlace!!
        PlaceDetailSheet(
            place = arPlace.place,
            travelMinutes = arPlace.reachableMinutes.roundToInt(),
            travelLabel = "${arPlace.reachableMinutes.roundToInt()} min away",
            distanceMeters = 0,
            detourLabel = "+${arPlace.detourMinutes.roundToInt()} min detour",
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

    LaunchedEffect(state.routeInfo) {
        val routeInfo = state.routeInfo ?: return@LaunchedEffect
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
            onMapClick = { viewModel.onPlaceHighlighted(null) },
        ) {
            state.routeInfo?.let { routeInfo ->
                val routeIndex = state.currentRoutePointIndex

                if (routeIndex > 0 && routeIndex < routeInfo.decodedPoints.size) {
                    val behindPoints = routeInfo.decodedPoints.subList(0, routeIndex + 1)
                    Polyline(
                        points = behindPoints,
                        color = Color(0xFFB0BEC5),
                        width = 10f,
                    )

                    val aheadPoints = routeInfo.decodedPoints.subList(routeIndex, routeInfo.decodedPoints.size)
                    Polyline(
                        points = aheadPoints,
                        color = Color(0xFF1B6B4A),
                        width = 14f,
                    )
                } else {
                    Polyline(
                        points = routeInfo.decodedPoints,
                        color = Color(0xFF1B6B4A),
                        width = 14f,
                    )
                }

                if (routeInfo.decodedPoints.isNotEmpty()) {
                    Marker(
                        state = MarkerState(position = routeInfo.decodedPoints.first()),
                        title = "Start",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        zIndex = 2f,
                    )
                    Marker(
                        state = MarkerState(position = routeInfo.decodedPoints.last()),
                        title = state.destinationName.ifBlank { "Destination" },
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                        zIndex = 2f,
                    )
                }

                state.currentPositionOnRoute?.let { pos ->
                    Marker(
                        state = MarkerState(position = pos),
                        title = "You",
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
                    snippet = "${arPlace.reachableMinutes.roundToInt()} min  \u00B7  +${arPlace.detourMinutes.roundToInt()} min detour",
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
                .align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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

            CategoryChipRow(
                selectedCategories = state.selectedCategories,
                onCategoryToggled = { viewModel.onCategoryToggled(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 4.dp),
            )

            AnimatedVisibility(
                visible = showFilters,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                RouteFilterBar(
                    filterSettings = state.filterSettings,
                    onMaxDetourChanged = viewModel::onMaxDetourChanged,
                    onTransportModeChanged = viewModel::onTransportModeChanged,
                    onPriceLevelChanged = viewModel::onPriceLevelChanged,
                    onOpenNowToggled = viewModel::onOpenNowToggled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

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
                                text = "Enter a destination to find popular stops along your route",
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
                                    text = if (state.isLoadingRoute) "Computing route..." else "Finding stops...",
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
                                    text = "No popular stops found",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Try a different category or increase the reachable time",
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

                    is RouteResultState.Results -> {
                        LazyRow(
                            state = bubbleListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                items = resultState.places,
                                key = { _, item -> item.place.placeId },
                            ) { _, arPlace ->
                                RouteStopBubble(
                                    arPlace = arPlace,
                                    isHighlighted = arPlace.place.placeId == state.highlightedPlaceId,
                                    onClick = {
                                        viewModel.onPlaceHighlighted(arPlace.place.placeId)
                                    },
                                    onInfoClick = {
                                        viewModel.onPlaceSelected(arPlace)
                                        showDetail = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            state.routeInfo?.let { routeInfo ->
                val durationMin = routeInfo.durationSeconds / 60
                val distanceKm = "%.1f".format(routeInfo.distanceMeters / 1000.0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$distanceKm km  \u00B7  $durationMin min",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onRefreshPlaces()
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh places",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (state.routeInfo != null) {
            if (hasHighlight) {
                SmallFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showFilters = !showFilters
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Toggle filters",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showFilters = !showFilters
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Toggle filters",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = hasHighlight,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val highlighted = state.alongRoutePlaces.find {
                it.place.placeId == state.highlightedPlaceId
            }
            if (highlighted != null) {
                Button(onClick = {
                    viewModel.onPlaceSelected(highlighted)
                    showDetail = true
                }) {
                    Text("View Details")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
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

@Composable
private fun RouteStopBubble(
    arPlace: AlongRoutePlace,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val place = arPlace.place
    val reachMin = arPlace.reachableMinutes.roundToInt()
    val detourMin = arPlace.detourMinutes.roundToInt()
    val haptic = LocalHapticFeedback.current
    val priceText = if (place.priceLevel != null) "$".repeat(place.priceLevel) else "\u2014"

    val elevation by animateDpAsState(
        targetValue = if (isHighlighted) 12.dp else 2.dp,
        animationSpec = tween(durationMillis = 300),
        label = "routeBubbleElevation",
    )

    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onInfoClick()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "View details",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = StarYellow,
                )
                Spacer(Modifier.width(3.dp))
                Text("${place.rating}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(${formatReviewCount(place.userRatingCount)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "$reachMin min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "\u00B7",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "+$detourMin min detour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (priceText != "\u2014") {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatReviewCount(count: Int): String = when {
    count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}k"
    else -> count.toString()
}
