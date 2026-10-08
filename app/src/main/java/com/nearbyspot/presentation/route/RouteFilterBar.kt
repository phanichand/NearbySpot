package com.nearbyspot.presentation.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteFilterBar(
    filterSettings: RouteFilterSettings,
    onMaxDetourChanged: (Int) -> Unit,
    onTransportModeChanged: (TransportMode) -> Unit,
    onPriceLevelChanged: (Int?) -> Unit,
    onOpenNowToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FilterSectionLabel(stringResource(R.string.route_filter_travel_mode))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TransportMode.entries.forEach { mode ->
                FilterChip(
                    selected = filterSettings.transportMode == mode,
                    onClick = { onTransportModeChanged(mode) },
                    label = { Text(mode.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = mode.toIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        FilterSectionLabel(stringResource(R.string.route_filter_maximum_detour))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(5, 15, 30).forEach { minutes ->
                FilterChip(
                    selected = filterSettings.maxDetourMinutes == minutes,
                    onClick = { onMaxDetourChanged(minutes) },
                    label = { Text("$minutes min") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        FilterSectionLabel(stringResource(R.string.route_filter_maximum_price))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = filterSettings.maxPriceLevel == null,
                onClick = { onPriceLevelChanged(null) },
                label = { Text(stringResource(R.string.route_filter_any_price)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            (1..4).forEach { level ->
                FilterChip(
                    selected = filterSettings.maxPriceLevel == level,
                    onClick = {
                        onPriceLevelChanged(
                            if (filterSettings.maxPriceLevel == level) null else level,
                        )
                    },
                    label = { Text("$".repeat(level)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = filterSettings.openNowOnly,
            onClick = { onOpenNowToggled(!filterSettings.openNowOnly) },
            label = { Text(stringResource(R.string.route_filter_open_now)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
}

private fun TransportMode.toIcon(): ImageVector = when (this) {
    TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransportMode.DRIVE -> Icons.Filled.DirectionsCar
    TransportMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
}
