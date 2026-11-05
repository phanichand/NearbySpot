package com.nearbyspot.presentation.route

import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionPick
import kotlin.math.roundToInt

internal sealed interface SectionTitle {
    data object NextStop : SectionTitle
    data class HoursIn(val hours: Int) : SectionTitle
}

internal fun sectionTitle(aheadSeconds: Int): SectionTitle =
    if (aheadSeconds <= 0) {
        SectionTitle.NextStop
    } else {
        SectionTitle.HoursIn((aheadSeconds / SECONDS_PER_HOUR).roundToInt().coerceAtLeast(1))
    }

/** Why a pick was chosen; the extras are null when the pick costs no more than the baseline. */
internal data class PickReason(
    val rating: Double,
    val reviewCount: Int,
    val extraMinutes: Int?,
    val extraStars: Double?,
    val recentPositive: Int?,
    val recentTotal: Int?,
)

internal fun pickReason(pick: SectionPick): PickReason {
    val place = pick.candidate.place
    val hasExtra = pick.extraMinutesVsBaseline > 0
    return PickReason(
        rating = place.rating,
        reviewCount = place.userRatingCount,
        extraMinutes = pick.extraMinutesVsBaseline.takeIf { hasExtra },
        extraStars = pick.extraStarsVsBaseline.takeIf { hasExtra },
        recentPositive = pick.recencySignal?.recentPositiveCount,
        recentTotal = pick.recencySignal?.recentCount,
    )
}

/** The first section is always open; any other opens when it is the expanded one. */
internal fun isSectionOpen(section: RouteSection, expandedIndex: Int?): Boolean =
    section.index == 0 || section.index == expandedIndex

/** Position of a place's card in the timeline list, or null when its section is collapsed. */
internal fun timelineItemIndex(
    sections: List<RouteSection>,
    expandedIndex: Int?,
    placeId: String,
): Int? {
    var position = 0
    for (section in sections) {
        position++
        if (!isSectionOpen(section, expandedIndex)) continue
        for (pick in section.picks) {
            if (pick.candidate.place.placeId == placeId) return position
            position++
        }
    }
    return null
}

private const val SECONDS_PER_HOUR = 3_600.0
