package com.nearbyspot.presentation.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nearbyspot.R
import com.nearbyspot.presentation.components.PlaceCard
import com.nearbyspot.presentation.components.StatusMessage
import com.nearbyspot.presentation.detail.PlaceDetailSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onNavigateBack: () -> Unit,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail && state.selectedPlace != null) {
        PlaceDetailSheet(
            place = state.selectedPlace!!,
            travelMinutes = 0,
            travelLabel = "",
            distanceMeters = 0,
            onDismiss = {
                showDetail = false
                viewModel.onPlaceSelected(null)
            },
            photoProvider = viewModel.photoProvider,
            isFavorite = true,
            onFetchReviews = viewModel::fetchReviews,
            onToggleFavorite = {
                viewModel.onRemoveFavorite(state.selectedPlace!!)
                showDetail = false
                viewModel.onPlaceSelected(null)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.saved_title))
                        if (state.favorites.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.saved_count,
                                    state.favorites.size,
                                    state.favorites.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.favorites.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusMessage(
                        title = stringResource(R.string.saved_empty_title),
                        body = stringResource(R.string.saved_empty_body),
                        icon = Icons.Filled.BookmarkBorder,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.favorites, key = { it.placeId }) { place ->
                        PlaceCard(
                            place = place,
                            onClick = {
                                viewModel.onPlaceSelected(place)
                                showDetail = true
                            },
                            modifier = Modifier.animateItem(),
                            trailingContent = {
                                IconButton(onClick = { viewModel.onRemoveFavorite(place) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = stringResource(R.string.action_remove_saved),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
