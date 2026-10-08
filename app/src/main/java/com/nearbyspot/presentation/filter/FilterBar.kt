package com.nearbyspot.presentation.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.SortOption
import com.nearbyspot.domain.model.TransportMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                TransportMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = filterSettings.transportMode == mode,
                        onClick = { onTransportModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransportMode.entries.size,
                        ),
                        modifier = Modifier.heightIn(min = 40.dp),
                        icon = {
                            Icon(
                                imageVector = mode.toIcon(),
                                contentDescription = mode.label,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Within",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val timeOptions = listOf(5, 10, 15)
                timeOptions.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = filterSettings.travelTimeMinutes == minutes,
                        onClick = { onTravelTimeChanged(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = timeOptions.size,
                        ),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text("$minutes min", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Price",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = filterSettings.maxPriceLevel == null,
                onClick = { onPriceLevelChanged(null) },
                label = { Text("Any") },
                modifier = Modifier.heightIn(min = 40.dp),
            )
            listOf(1, 2, 3, 4).forEach { level ->
                FilterChip(
                    selected = filterSettings.maxPriceLevel == level,
                    onClick = {
                        onPriceLevelChanged(
                            if (filterSettings.maxPriceLevel == level) null else level
                        )
                    },
                    label = { Text("$".repeat(level)) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filterSettings.openNowOnly,
                onClick = { onOpenNowToggled(!filterSettings.openNowOnly) },
                label = { Text("Open Now") },
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sort",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SortOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = filterSettings.sortBy == option,
                        onClick = { onSortChanged(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SortOption.entries.size,
                        ),
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun TransportMode.toIcon(): ImageVector = when (this) {
    TransportMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransportMode.DRIVE -> Icons.Filled.DirectionsCar
    TransportMode.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
}
