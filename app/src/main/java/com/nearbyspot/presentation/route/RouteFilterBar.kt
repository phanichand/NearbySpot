package com.nearbyspot.presentation.route

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nearbyspot.R
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.presentation.filter.FilterSection
import com.nearbyspot.presentation.filter.MinutesSelector
import com.nearbyspot.presentation.filter.OpenNowRow
import com.nearbyspot.presentation.filter.PriceSelector
import com.nearbyspot.presentation.filter.TransportModeSelector

private val DETOUR_OPTIONS_MINUTES = listOf(5, 15, 30)

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
        FilterSection(label = stringResource(R.string.filter_mode)) {
            TransportModeSelector(
                selected = filterSettings.transportMode,
                onSelected = onTransportModeChanged,
            )
        }
        FilterSection(label = stringResource(R.string.filter_max_detour)) {
            MinutesSelector(
                options = DETOUR_OPTIONS_MINUTES,
                selected = filterSettings.maxDetourMinutes,
                onSelected = onMaxDetourChanged,
            )
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
