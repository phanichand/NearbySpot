package com.nearbyspot.presentation.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.util.Date
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbyspot.R
import com.nearbyspot.domain.model.AlongRoutePlace
import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionNote
import com.nearbyspot.domain.model.SectionPick
import com.nearbyspot.domain.model.SectionStatus
import com.nearbyspot.presentation.util.DurationFormatter

/** The drive as a list of sections: "Next stop" first, then later stretches the traveller can open. */
@Composable
internal fun RouteTimeline(
    sections: List<RouteSection>,
    departureTimeMillis: Long?,
    expandedIndex: Int?,
    highlightedPlaceId: String?,
    favoriteIds: Set<String>,
    listState: LazyListState,
    onSectionClick: (Int) -> Unit,
    onPickClick: (AlongRoutePlace) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    hasMore: Boolean,
    onShowFurther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.heightIn(max = TIMELINE_MAX_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            item(key = "section-${section.index}") {
                SectionHeader(
                    section = section,
                    clockTime = rememberClockTime(departureTimeMillis, section.aheadSeconds),
                    isOpen = isSectionOpen(section, expandedIndex),
                    onClick = { onSectionClick(section.index) },
                )
            }
            if (isSectionOpen(section, expandedIndex)) {
                items(
                    items = section.picks,
                    key = { pick -> "pick-${section.index}-${pick.candidate.place.placeId}" },
                ) { pick ->
                    val place = pick.candidate.place
                    PickCard(
                        pick = pick,
                        isHighlighted = place.placeId == highlightedPlaceId,
                        isFavorite = place.placeId in favoriteIds,
                        onClick = { onPickClick(pick.candidate) },
                        onToggleFavorite = { onToggleFavorite(place) },
                        onNavigate = { onNavigate(place) },
                    )
                }
            }
        }
        if (hasMore) {
            item(key = "show-further") {
                Text(
                    text = stringResource(R.string.route_timeline_show_further),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowFurther)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The wall-clock time the traveller reaches a section, from the chosen departure or from now. */
@Composable
private fun rememberClockTime(departureTimeMillis: Long?, aheadSeconds: Int): String {
    val context = LocalContext.current
    return remember(departureTimeMillis, aheadSeconds) {
        DateFormat.getTimeFormat(context).format(
            Date(arrivalClockMillis(departureTimeMillis, System.currentTimeMillis(), aheadSeconds)),
        )
    }
}

@Composable
private fun SectionHeader(section: RouteSection, clockTime: String, isOpen: Boolean, onClick: () -> Unit) {
    val title = when (val title = sectionTitle(section.aheadSeconds)) {
        SectionTitle.NextStop -> stringResource(R.string.route_timeline_next_stop)
        is SectionTitle.Ahead -> when {
            title.minutes < 60 -> stringResource(R.string.route_timeline_minutes_in, title.minutes)
            title.minutes % 60 == 0 -> stringResource(R.string.route_timeline_hours_in, title.minutes / 60)
            else -> stringResource(
                R.string.route_timeline_hours_minutes_in,
                title.minutes / 60,
                title.minutes % 60,
            )
        }
    }
    val status = when (section.status) {
        SectionStatus.NOT_LOADED -> stringResource(R.string.route_timeline_tap_to_load)
        SectionStatus.LOADING -> stringResource(R.string.route_timeline_loading)
        SectionStatus.NO_QUALIFYING_PLACE -> stringResource(R.string.route_timeline_empty)
        SectionStatus.FAILED -> stringResource(R.string.route_timeline_failed)
        SectionStatus.LOADED -> null
    }
    val note = when (section.note) {
        SectionNote.BUDGET_LIMITED -> stringResource(R.string.route_note_budget_limited)
        SectionNote.POPULAR_ONLY -> stringResource(R.string.route_note_popular_only)
        null -> null
    }
    val isNextStop = section.aheadSeconds <= 0
    val canExpand = !isNextStop && section.status != SectionStatus.LOADING
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isNextStop) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clockTime,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canExpand) {
                Icon(
                    imageVector = if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PickCard(
    pick: SectionPick,
    isHighlighted: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNavigate: () -> Unit,
) {
    val place = pick.candidate.place
    val reason = pickReason(pick)
    val kindLabels = pick.kinds.sortedBy { it.ordinal }.map { kind ->
        stringResource(
            if (kind == PickKind.POPULAR) R.string.route_pick_popular else R.string.route_pick_trending,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isHighlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kindLabels.joinToString(separator = " · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
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
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.route_pick_meta,
                    reason.rating,
                    NumberFormat.getIntegerInstance().format(reason.reviewCount),
                    DurationFormatter.ahead(
                        pick.timeAheadSeconds.takeIf { it > 0 } ?: pick.candidate.reachableSeconds,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reason.extraMinutes?.let { minutes ->
                    val stars = reason.extraStars
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            text = if (stars != null && stars > 0.0) {
                                stringResource(R.string.route_pick_extra_with_stars, minutes, "%.1f".format(stars))
                            } else {
                                stringResource(R.string.route_pick_extra_time, minutes)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (reason.recentPositive != null && reason.recentTotal != null) {
                    Text(
                        text = stringResource(R.string.route_pick_recent, reason.recentPositive, reason.recentTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val TIMELINE_MAX_HEIGHT = 320.dp
