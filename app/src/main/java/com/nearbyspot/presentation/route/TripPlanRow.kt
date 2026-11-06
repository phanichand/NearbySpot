package com.nearbyspot.presentation.route

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.R
import com.nearbyspot.presentation.components.DestinationSearchBar
import java.util.Date

/** Where the trip starts and when the traveller leaves; both default to "here" and "now". */
@Composable
internal fun TripPlanRow(
    placesClient: PlacesClient,
    originName: String,
    departureTimeMillis: Long?,
    onOriginSelected: (name: String, lat: Double, lng: Double) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDepartureChanged: (Long?) -> Unit,
) {
    var showOriginDialog by remember { mutableStateOf(false) }
    var showDepartureDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { showOriginDialog = true },
            label = {
                Text(
                    stringResource(
                        R.string.route_origin_chip,
                        originName.ifBlank { stringResource(R.string.route_origin_current) },
                    ),
                    maxLines = 1,
                )
            },
        )
        AssistChip(
            onClick = { showDepartureDialog = true },
            label = {
                Text(
                    if (departureTimeMillis == null) {
                        stringResource(R.string.route_departure_now)
                    } else {
                        stringResource(
                            R.string.route_departure_at,
                            DateFormat.getTimeFormat(context).format(Date(departureTimeMillis)),
                        )
                    },
                )
            },
        )
    }

    if (showOriginDialog) {
        AlertDialog(
            onDismissRequest = { showOriginDialog = false },
            title = { Text(stringResource(R.string.route_origin_dialog_title)) },
            text = {
                DestinationSearchBar(
                    placesClient = placesClient,
                    selectedName = originName,
                    hintResId = R.string.route_origin_hint,
                    onDestinationSelected = { result ->
                        showOriginDialog = false
                        onOriginSelected(result.name, result.latLng.latitude, result.latLng.longitude)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOriginDialog = false
                        onUseCurrentLocation()
                    },
                ) { Text(stringResource(R.string.route_origin_use_current)) }
            },
            dismissButton = {
                TextButton(onClick = { showOriginDialog = false }) {
                    Text(stringResource(R.string.route_departure_cancel))
                }
            },
        )
    }

    if (showDepartureDialog) {
        DepartureDialog(
            onDismiss = { showDepartureDialog = false },
            onLeaveNow = {
                showDepartureDialog = false
                onDepartureChanged(null)
            },
            onSet = { hour, minute ->
                showDepartureDialog = false
                onDepartureChanged(nextDepartureAt(hour, minute, System.currentTimeMillis()))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartureDialog(
    onDismiss: () -> Unit,
    onLeaveNow: () -> Unit,
    onSet: (hour: Int, minute: Int) -> Unit,
) {
    val timeState = rememberTimePickerState(
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_departure_dialog_title)) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onSet(timeState.hour, timeState.minute) }) {
                Text(stringResource(R.string.route_departure_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onLeaveNow) { Text(stringResource(R.string.route_departure_now)) }
        },
    )
}
