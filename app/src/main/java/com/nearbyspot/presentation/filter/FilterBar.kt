package com.nearbyspot.presentation.filter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nearbyspot.R
import com.nearbyspot.domain.model.FilterSettings
import com.nearbyspot.domain.model.SortOption
import com.nearbyspot.domain.model.TransportMode

private val TRAVEL_TIME_OPTIONS_MINUTES = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
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
        FilterSection(label = stringResource(R.string.filter_mode)) {
            TransportModeSelector(
                selected = filterSettings.transportMode,
                onSelected = onTransportModeChanged,
            )
        }
        FilterSection(label = stringResource(R.string.filter_within)) {
            MinutesSelector(
                options = TRAVEL_TIME_OPTIONS_MINUTES,
                selected = filterSettings.travelTimeMinutes,
                onSelected = onTravelTimeChanged,
            )
        }
        FilterSection(label = stringResource(R.string.filter_sort)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SortOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = filterSettings.sortBy == option,
                        onClick = { onSortChanged(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SortOption.entries.size,
                        ),
                    ) {
                        Text(option.label)
                    }
                }
            }
        }
        FilterSection(label = stringResource(R.string.filter_price)) {
            PriceSelector(
                maxPriceLevel = filterSettings.maxPriceLevel,
                onPriceLevelChanged = onPriceLevelChanged,
            )
        }
        OpenNowRow(
            openNowOnly = filterSettings.openNowOnly,
            onOpenNowToggled = onOpenNowToggled,
        )
    }
}
