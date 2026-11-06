package com.nearbyspot.presentation.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "DestinationSearch"

/** Exception type and, for Places errors, the status code; never the query or any key. */
private fun describe(error: Exception): String =
    "${error.javaClass.simpleName} status=${(error as? ApiException)?.statusCode}"

data class DestinationResult(
    val placeId: String,
    val name: String,
    val latLng: LatLng,
)

internal data class DestinationSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
)

private enum class RetryTarget {
    QUERY,
    SELECTION,
}

@Composable
fun DestinationSearchBar(
    placesClient: PlacesClient,
    onDestinationSelected: (DestinationResult) -> Unit,
    selectedName: String,
    modifier: Modifier = Modifier,
    @StringRes hintResId: Int = R.string.destination_search_hint,
) {
    var query by remember { mutableStateOf(selectedName) }
    var suggestions by remember { mutableStateOf<List<DestinationSuggestion>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTarget by remember { mutableStateOf<RetryTarget?>(null) }
    var pendingSelection by remember { mutableStateOf<DestinationSuggestion?>(null) }
    var autocompleteJob by remember { mutableStateOf<Job?>(null) }
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }
    val requestGate = remember { DestinationRequestGate() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val autocompleteError = stringResource(R.string.destination_search_error)
    val selectionError = stringResource(R.string.destination_selection_error)

    fun invalidateRequests() {
        requestGate.invalidate()
        autocompleteJob?.cancel()
        selectionJob?.cancel()
        autocompleteJob = null
        selectionJob = null
        isLoading = false
    }

    fun dismissSearch(clearFocus: Boolean) {
        invalidateRequests()
        showSuggestions = false
        errorMessage = null
        retryTarget = null
        if (clearFocus) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun requestSuggestions(requestQuery: String) {
        requestGate.invalidate()
        autocompleteJob?.cancel()
        val requestId = requestGate.next()
        autocompleteJob = scope.launch {
            delay(AUTOCOMPLETE_DEBOUNCE_MS)
            if (!requestGate.isCurrent(requestId)) return@launch
            isLoading = true
            errorMessage = null
            retryTarget = null
            try {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(requestQuery)
                    .setSessionToken(sessionToken)
                    .build()
                val response = withTransientRetry { placesClient.findAutocompletePredictions(request).await() }
                if (!requestGate.isCurrent(requestId) || query != requestQuery || !showSuggestions) {
                    return@launch
                }
                suggestions = response.autocompletePredictions.map { prediction ->
                    DestinationSuggestion(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString(),
                        fullText = prediction.getFullText(null).toString(),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Autocomplete failed: ${describe(error)}")
                if (requestGate.isCurrent(requestId)) {
                    suggestions = emptyList()
                    errorMessage = autocompleteError
                    retryTarget = RetryTarget.QUERY
                }
            } finally {
                if (requestGate.isCurrent(requestId)) isLoading = false
            }
        }
    }

    fun fetchSelection(suggestion: DestinationSuggestion) {
        requestGate.invalidate()
        autocompleteJob?.cancel()
        selectionJob?.cancel()
        val requestId = requestGate.next()
        pendingSelection = suggestion
        errorMessage = null
        retryTarget = null
        selectionJob = scope.launch {
            isLoading = true
            try {
                val fields = listOf(Place.Field.LAT_LNG, Place.Field.DISPLAY_NAME)
                val request = FetchPlaceRequest.builder(suggestion.placeId, fields)
                    .setSessionToken(sessionToken)
                    .build()
                val response = withTransientRetry { placesClient.fetchPlace(request).await() }
                if (!requestGate.isCurrent(requestId)) return@launch
                @Suppress("DEPRECATION")
                val latLng = response.place.latLng
                if (latLng == null) {
                    errorMessage = selectionError
                    retryTarget = RetryTarget.SELECTION
                    showSuggestions = true
                    return@launch
                }
                sessionToken = AutocompleteSessionToken.newInstance()
                pendingSelection = null
                showSuggestions = false
                onDestinationSelected(
                    DestinationResult(
                        placeId = suggestion.placeId,
                        name = suggestion.fullText,
                        latLng = latLng,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Place selection failed: ${describe(error)}")
                if (requestGate.isCurrent(requestId)) {
                    errorMessage = selectionError
                    retryTarget = RetryTarget.SELECTION
                    showSuggestions = true
                }
            } finally {
                if (requestGate.isCurrent(requestId)) isLoading = false
            }
        }
    }

    LaunchedEffect(selectedName) {
        if (selectedName != query) {
            dismissSearch(clearFocus = true)
            query = selectedName
            suggestions = emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            requestGate.invalidate()
            autocompleteJob?.cancel()
            selectionJob?.cancel()
        }
    }

    DestinationSearchField(
        hintResId = hintResId,
        query = query,
        suggestions = suggestions,
        expanded = showSuggestions,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onQueryChanged = { newQuery ->
            invalidateRequests()
            query = newQuery
            suggestions = emptyList()
            errorMessage = null
            retryTarget = null
            pendingSelection = null
            showSuggestions = newQuery.length >= MIN_AUTOCOMPLETE_CHARACTERS
            if (showSuggestions) requestSuggestions(newQuery)
        },
        onFocusGained = {
            if (query.length >= MIN_AUTOCOMPLETE_CHARACTERS) {
                showSuggestions = true
                if (suggestions.isEmpty()) requestSuggestions(query)
            }
        },
        onClear = {
            dismissSearch(clearFocus = true)
            query = ""
            suggestions = emptyList()
            pendingSelection = null
        },
        onSuggestionsDismissed = { dismissSearch(clearFocus = false) },
        onEditingFinished = { dismissSearch(clearFocus = true) },
        onRetry = {
            when (retryTarget) {
                RetryTarget.QUERY -> requestSuggestions(query)
                RetryTarget.SELECTION -> pendingSelection?.let(::fetchSelection)
                null -> Unit
            }
        },
        onSuggestionSelected = { suggestion ->
            query = suggestion.fullText
            suggestions = emptyList()
            showSuggestions = false
            errorMessage = null
            retryTarget = null
            requestGate.invalidate()
            autocompleteJob?.cancel()
            focusManager.clearFocus()
            keyboardController?.hide()
            fetchSelection(suggestion)
        },
        modifier = modifier,
    )
}

@Composable
internal fun DestinationSearchField(
    query: String,
    suggestions: List<DestinationSuggestion>,
    expanded: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onQueryChanged: (String) -> Unit,
    onFocusGained: () -> Unit,
    onClear: () -> Unit,
    onSuggestionsDismissed: () -> Unit,
    onEditingFinished: () -> Unit,
    onRetry: () -> Unit,
    onSuggestionSelected: (DestinationSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes hintResId: Int = R.string.destination_search_hint,
) {
    val popupVisible = expanded && (isLoading || errorMessage != null || suggestions.isNotEmpty())
    val searchDescription = stringResource(R.string.destination_search_description)

    BackHandler(enabled = expanded, onBack = onEditingFinished)

    BoxWithConstraints(modifier = modifier) {
        val anchorWidth = maxWidth
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onFocusChanged { if (it.isFocused) onFocusGained() }
                .semantics { contentDescription = searchDescription }
                .testTag(DESTINATION_SEARCH_FIELD_TAG),
            placeholder = {
                Text(
                    stringResource(hintResId),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.destination_search_clear),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEditingFinished() }),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        DropdownMenu(
            expanded = popupVisible,
            onDismissRequest = onSuggestionsDismissed,
            modifier = Modifier
                .width(anchorWidth)
                .heightIn(max = 240.dp)
                .testTag(DESTINATION_SUGGESTIONS_TAG),
            offset = DpOffset(x = 0.dp, y = 4.dp),
            properties = PopupProperties(focusable = false),
        ) {
            when {
                isLoading -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.destination_search_loading)) },
                        onClick = {},
                        enabled = false,
                        leadingIcon = {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag(DESTINATION_LOADING_TAG),
                                strokeWidth = 2.dp,
                            )
                        },
                    )
                }

                errorMessage != null -> {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = errorMessage,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        },
                        onClick = {},
                    )
                }

                else -> suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { SuggestionText(suggestion) },
                        onClick = { onSuggestionSelected(suggestion) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionText(suggestion: DestinationSuggestion) {
    Column {
        Text(
            text = suggestion.primaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (suggestion.secondaryText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = suggestion.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal class DestinationRequestGate {
    private var generation: Long = 0L

    fun next(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun isCurrent(requestId: Long): Boolean = requestId == generation
}

internal const val DESTINATION_SEARCH_FIELD_TAG = "destination_search_field"
internal const val DESTINATION_SUGGESTIONS_TAG = "destination_suggestions"
internal const val DESTINATION_LOADING_TAG = "destination_loading"

private const val MIN_AUTOCOMPLETE_CHARACTERS = 3
private const val AUTOCOMPLETE_DEBOUNCE_MS = 500L
