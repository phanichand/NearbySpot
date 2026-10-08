package com.nearbyspot.presentation.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.R
import com.nearbyspot.domain.model.RouteFilterSettings
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.presentation.theme.StarYellow
import com.nearbyspot.presentation.util.DistanceFormatter
import com.nearbyspot.presentation.util.DurationFormatter

@Composable
internal fun RouteSummaryCard(
    destinationName: String,
    distanceMeters: Int,
    durationSeconds: Int,
    resultCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinationLabel = destinationName.ifBlank {
        stringResource(R.string.route_destination_fallback)
    }
    val refreshDescription = stringResource(
        if (isLoading) R.string.route_refreshing_stops else R.string.route_refresh_stops,
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.route_to_destination,
                        destinationLabel,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${DistanceFormatter.format(distanceMeters)} · " +
                        DurationFormatter.route(durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (resultCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.route_verified_stops,
                            resultCount,
                            resultCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = refreshDescription
                    },
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RouteActionDock(
    filtersVisible: Boolean,
    activeFilterCount: Int,
    highlightedPlaceName: String?,
    onToggleFilters: () -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterActionLabel = stringResource(
        if (filtersVisible) R.string.route_hide_filters else R.string.route_show_filters,
    )
    val activeFiltersLabel = stringResource(R.string.route_active_filters, activeFilterCount)
    val detailsLabel = highlightedPlaceName?.let {
        stringResource(R.string.route_view_details_for, it)
    }
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onToggleFilters,
                modifier = Modifier
                    .then(
                        if (highlightedPlaceName == null) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 48.dp)
                    .semantics {
                        selected = filtersVisible
                        contentDescription = buildString {
                            append(filterActionLabel)
                            if (activeFilterCount > 0) append(", $activeFiltersLabel")
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = if (activeFilterCount > 0 || filtersVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activeFilterCount > 0) {
                        stringResource(R.string.route_filters_with_count, activeFilterCount)
                    } else {
                        stringResource(R.string.route_filters)
                    },
                )
            }
            if (highlightedPlaceName != null) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = detailsLabel.orEmpty()
                        },
                ) {
                    Text(
                        text = stringResource(R.string.route_view_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RouteStopCard(
    arPlace: AlongRoutePlace,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = arPlace.place
    val aheadLabel = DurationFormatter.ahead(arPlace.reachableSeconds)
    val detourLabel = DurationFormatter.detour(arPlace.detourSeconds)
    val haptic = LocalHapticFeedback.current
    val ratingLabel = stringResource(
        R.string.route_rating_and_reviews,
        place.rating,
        place.userRatingCount,
    )
    val priceLabel = place.priceLevel?.let {
        stringResource(R.string.route_price_level, "$".repeat(it))
    }
    val openLabel = place.isOpen?.let {
        stringResource(if (it) R.string.route_currently_open else R.string.route_currently_closed)
    }
    val announcement = listOfNotNull(
        place.name,
        place.address.takeIf(String::isNotBlank),
        ratingLabel,
        priceLabel,
        openLabel,
        aheadLabel,
        detourLabel,
    ).joinToString()

    Card(
        modifier = modifier
            .width(280.dp)
            .semantics(mergeDescendants = true) {
                selected = isHighlighted
                role = Role.Button
                contentDescription = announcement
            }
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 8.dp else 2.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (place.address.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = StarYellow,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${place.rating} (${formatRouteReviewCount(place.userRatingCount)})",
                    style = MaterialTheme.typography.labelLarge,
                )
                place.priceLevel?.let { priceLevel ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$".repeat(priceLevel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = aheadLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detourLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

internal fun activeRouteFilterCount(filters: RouteFilterSettings): Int = listOf(
    filters.transportMode != TransportMode.DRIVE,
    filters.maxDetourMinutes != RouteFilterSettings().maxDetourMinutes,
    filters.maxPriceLevel != null,
    filters.openNowOnly,
).count { it }

private fun formatRouteReviewCount(count: Int): String = when {
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}k"
    else -> count.toString()
}
