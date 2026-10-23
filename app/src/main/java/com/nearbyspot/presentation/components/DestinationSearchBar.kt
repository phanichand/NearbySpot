package com.nearbyspot.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DestinationResult(
    val placeId: String,
    val name: String,
    val latLng: LatLng,
)

@Composable
fun DestinationSearchBar(
    placesClient: PlacesClient,
    onDestinationSelected: (DestinationResult) -> Unit,
    selectedName: String,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(selectedName) }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    LaunchedEffect(selectedName) {
        if (selectedName.isNotBlank()) {
            query = selectedName
            showSuggestions = false
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                showSuggestions = newQuery.length >= 3
                debounceJob?.cancel()
                if (newQuery.length >= 3) {
                    debounceJob = scope.launch {
                        delay(500)
                        isLoading = true
                        try {
                            val request = FindAutocompletePredictionsRequest.builder()
                                .setQuery(newQuery)
                                .setSessionToken(sessionToken)
                                .build()
                            val response = placesClient
                                .findAutocompletePredictions(request)
                                .await()
                            predictions = response.autocompletePredictions
                        } catch (_: Exception) {
                            predictions = emptyList()
                        }
                        isLoading = false
                    }
                } else {
                    predictions = emptyList()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.route_search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search destination",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        predictions = emptyList()
                        showSuggestions = false
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        AnimatedVisibility(
            visible = showSuggestions && predictions.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    items(
                        items = predictions,
                        key = { it.placeId },
                    ) { prediction ->
                        PredictionItem(
                            prediction = prediction,
                            onClick = {
                                query = prediction.getFullText(null).toString()
                                showSuggestions = false
                                predictions = emptyList()

                                scope.launch {
                                    try {
                                        val fields = listOf(Place.Field.LAT_LNG, Place.Field.DISPLAY_NAME)
                                        val fetchRequest = FetchPlaceRequest.builder(
                                            prediction.placeId,
                                            fields,
                                        ).setSessionToken(sessionToken).build()
                                        val fetchResponse = placesClient.fetchPlace(fetchRequest).await()
                                        sessionToken = AutocompleteSessionToken.newInstance()
                                        val place = fetchResponse.place
                                        @Suppress("DEPRECATION")
                                        val latLng = place.latLng
                                        if (latLng != null) {
                                            onDestinationSelected(
                                                DestinationResult(
                                                    placeId = prediction.placeId,
                                                    name = prediction.getFullText(null).toString(),
                                                    latLng = latLng,
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {
                                        // Fetch failed silently
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionItem(
    prediction: AutocompletePrediction,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prediction.getPrimaryText(null).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = prediction.getSecondaryText(null).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
