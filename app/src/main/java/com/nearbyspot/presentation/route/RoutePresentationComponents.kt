package com.nearbyspot.presentation.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalLayoutApi::class)
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
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Route,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.route_to_destination,
                        destinationLabel,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RouteMetricPill(
                        icon = Icons.Filled.Straighten,
                        text = DistanceFormatter.format(distanceMeters),
                    )
                    RouteMetricPill(
                        icon = Icons.Filled.Schedule,
                        text = DurationFormatter.route(durationSeconds),
                    )
                }
                if (resultCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    RouteMetricPill(
                        icon = Icons.Filled.Verified,
                        text = pluralStringResource(
                            R.plurals.route_verified_stops, resultCount, resultCount,
                        ),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
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
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
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

@OptIn(ExperimentalLayoutApi::class)
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
            .heightIn(min = 164.dp)
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
            defaultElevation = if (isHighlighted) 8.dp else 3.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
            place.isOpen?.let { isOpen ->
                Spacer(Modifier.height(6.dp))
                RouteMetricPill(
                    text = stringResource(
                        if (isOpen) R.string.route_open_now else R.string.route_closed_now,
                    ),
                    containerColor = if (isOpen) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (isOpen) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RouteMetricPill(
                    icon = Icons.Filled.Schedule,
                    text = aheadLabel,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                RouteMetricPill(
                    icon = Icons.Filled.Route,
                    text = detourLabel,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RouteMetricPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
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
