package com.nearbyspot.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbyspot.R
import com.nearbyspot.domain.model.Place

/**
 * Shared list card for nearby results, route stops and saved places.
 *
 * @param rank 1-based position shown as a leading badge; omitted when null.
 * @param travelMinutes travel time pill; omitted when null.
 * @param detourMinutes extra route detour pill; omitted when null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    travelMinutes: Int? = null,
    detourMinutes: Int? = null,
    isHighlighted: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (isHighlighted) colors.secondaryContainer else colors.surfaceContainerLow,
        label = "placeCardContainer",
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isHighlighted) {
            BorderStroke(1.5.dp, colors.primary)
        } else {
            BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f))
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isHighlighted) colors.primary else colors.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.place_rank, rank),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isHighlighted) colors.onPrimary else colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingSummary(
                        rating = place.rating,
                        reviewCount = place.userRatingCount,
                        starSize = 14.dp,
                    )
                    if (place.priceLevel != null) {
                        Text(
                            text = " · ${formatPriceLevel(place.priceLevel)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (travelMinutes != null || detourMinutes != null || place.isOpen != null) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (travelMinutes != null) {
                            MetaPill(
                                text = stringResource(R.string.place_minutes, travelMinutes),
                                icon = Icons.Filled.AccessTime,
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer,
                            )
                        }
                        if (detourMinutes != null) {
                            MetaPill(
                                text = stringResource(R.string.place_detour, detourMinutes),
                                icon = Icons.Filled.Route,
                                containerColor = colors.tertiaryContainer,
                                contentColor = colors.onTertiaryContainer,
                            )
                        }
                        if (place.isOpen != null) {
                            OpenStatusPill(isOpen = place.isOpen)
                        }
                    }
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else {
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}
