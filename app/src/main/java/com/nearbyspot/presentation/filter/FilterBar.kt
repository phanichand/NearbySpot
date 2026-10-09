package com.nearbyspot.presentation.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.nearbyspot.R
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.SortOption
import com.nearbyspot.domain.model.TransportMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    filterSettings: FilterSettings,
    onTravelTimeChanged: (Int) -> Unit,
    onTransportModeChanged: (TransportMode) -> Unit,
    onPriceLevelChanged: (Int?) -> Unit,
    onOpenNowToggled: (Boolean) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterSection(label = stringResource(R.string.home_filter_travel_mode)) {
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

        FilterSection(label = stringResource(R.string.home_filter_travel_time)) {
            listOf(5, 10, 15).forEach { minutes ->
                FilterChip(
                    selected = filterSettings.travelTimeMinutes == minutes,
                    onClick = { onTravelTimeChanged(minutes) },
                    label = { Text("$minutes min") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        FilterSection(label = stringResource(R.string.home_filter_maximum_price)) {
            FilterChip(
                selected = filterSettings.maxPriceLevel == null,
                onClick = { onPriceLevelChanged(null) },
                label = { Text(stringResource(R.string.home_filter_any_price)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            listOf(1, 2, 3, 4).forEach { level ->
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

        FilterSection(label = stringResource(R.string.home_filter_availability)) {
            FilterChip(
                selected = filterSettings.openNowOnly,
                onClick = { onOpenNowToggled(!filterSettings.openNowOnly) },
                label = { Text(stringResource(R.string.home_filter_open_now)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }

        FilterSection(label = stringResource(R.string.home_filter_sort_by)) {
            SortOption.entries.forEach { option ->
                FilterChip(
                    selected = filterSettings.sortBy == option,
                    onClick = { onSortChanged(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

private fun TransportMode.toIcon(): ImageVector = when (this) {
    TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransportMode.DRIVE -> Icons.Filled.DirectionsCar
    TransportMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
}
