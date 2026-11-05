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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.LinearProgressIndicator
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
import kotlin.math.roundToInt

@Composable
internal fun RouteSummaryCard(
    distanceMeters: Int,
    durationSeconds: Int,
    resultCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshDescription = stringResource(
        if (isLoading) R.string.route_refreshing_stops else R.string.route_refresh_stops,
    )
    val summary = buildList {
        add(DistanceFormatter.format(distanceMeters))
        add(DurationFormatter.route(durationSeconds))
        if (resultCount > 0) {
            add(pluralStringResource(R.plurals.route_verified_stops, resultCount, resultCount))
        }
    }.joinToString(separator = "  \u00B7  ")
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    rank: Int,
    routeFraction: Float,
    routeZoneLabel: String,
    distanceAlongRouteMeters: Int,
    isHighlighted: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigate: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = arPlace.place
    val typeLabels = placeTypeLabelResIds(place.types).map { stringResource(it) }
    val alongRouteLabel = stringResource(
        R.string.route_along_route,
        DistanceFormatter.format(distanceAlongRouteMeters),
    )
    val displayAddress = stripPlusCode(place.address)
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
    val positionLabel = stringResource(
        R.string.route_position_accessibility,
        routeZoneLabel,
        (routeFraction.coerceIn(0f, 1f) * 100).roundToInt(),
    )
    val announcement = listOfNotNull(
        stringResource(R.string.route_rank_accessibility, rank),
        routeZoneLabel,
        place.name,
        displayAddress.takeIf(String::isNotBlank),
        ratingLabel,
        priceLabel,
        openLabel,
        typeLabels.joinToString().takeIf(String::isNotBlank),
        aheadLabel,
        detourLabel,
        alongRouteLabel,
    ).joinToString()

    Card(
        modifier = modifier
            .width(300.dp)
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
            defaultElevation = if (isHighlighted) 2.dp else 0.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.route_rank, rank),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = routeZoneLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (isFavorite) R.string.route_unsave_stop else R.string.route_save_stop,
                                place.name,
                            ),
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onNavigate) {
                        Icon(
                            imageVector = Icons.Filled.Directions,
                            contentDescription = stringResource(R.string.route_navigate_to, place.name),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (typeLabels.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = typeLabels.joinToString(separator = " \u00B7 "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (displayAddress.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayAddress,
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
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = alongRouteLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { routeFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .semantics {
                        contentDescription = positionLabel
                    },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

internal enum class RouteStopZone {
    EARLY,
    MIDDLE,
    DESTINATION,
}

internal fun routeStopFraction(nearestRoutePointIndex: Int, lastRoutePointIndex: Int): Float =
    if (lastRoutePointIndex <= 0) {
        0f
    } else {
        (nearestRoutePointIndex.toFloat() / lastRoutePointIndex).coerceIn(0f, 1f)
    }

internal fun routeStopZone(routeFraction: Float): RouteStopZone = when {
    routeFraction < 0.34f -> RouteStopZone.EARLY
    routeFraction < 0.75f -> RouteStopZone.MIDDLE
    else -> RouteStopZone.DESTINATION
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

private val PlaceTypeLabels = mapOf(
    "indian_restaurant" to R.string.place_type_indian,
    "south_indian_restaurant" to R.string.place_type_south_indian,
    "seafood_restaurant" to R.string.place_type_seafood,
    "vegetarian_restaurant" to R.string.place_type_vegetarian,
    "vegan_restaurant" to R.string.place_type_vegan,
    "barbecue_restaurant" to R.string.place_type_barbecue,
    "chinese_restaurant" to R.string.place_type_chinese,
    "italian_restaurant" to R.string.place_type_italian,
    "middle_eastern_restaurant" to R.string.place_type_middle_eastern,
    "fast_food_restaurant" to R.string.place_type_fast_food,
    "breakfast_restaurant" to R.string.place_type_breakfast,
    "buffet_restaurant" to R.string.place_type_buffet,
    "bar" to R.string.place_type_bar,
    "cafe" to R.string.place_type_cafe,
    "coffee_shop" to R.string.place_type_cafe,
    "bakery" to R.string.place_type_bakery,
    "ice_cream_shop" to R.string.place_type_ice_cream,
)

private const val MAX_PLACE_TYPE_LABELS = 2

/** Short descriptors such as "Seafood" or "Bar", in the order the API ranked the place types. */
internal fun placeTypeLabelResIds(types: List<String>): List<Int> =
    types.mapNotNull(PlaceTypeLabels::get).distinct().take(MAX_PLACE_TYPE_LABELS)

internal fun activeRouteFilterCount(filters: RouteFilterSettings): Int = listOf(
    filters.transportMode != TransportMode.DRIVE,
    filters.maxDetourMinutes != RouteFilterSettings().maxDetourMinutes,
    filters.maxPriceLevel != null,
    filters.openNowOnly,
).count { it }

private val PlusCodePrefix = Regex("^[23456789CFGHJMPQRVWX]{2,8}\\+[23456789CFGHJMPQRVWX]{2,3},?\\s*")

internal fun stripPlusCode(address: String): String =
    address.replace(PlusCodePrefix, "").trim()

private fun formatRouteReviewCount(count: Int): String = when {
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}k"
    else -> count.toString()
}
