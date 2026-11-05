package com.nearbyspot.domain.usecase

import com.nearbyspot.domain.model.PickKind
import com.nearbyspot.domain.model.RouteSection
import com.nearbyspot.domain.model.SectionPick

/** Keeps the picks a traveller is reading from reshuffling on every refresh. */
object SectionStability {
    const val REPLACE_MARGIN = 0.15

    /**
     * Returns [fresh] with each pick kind resolved against [shown]: a shown pick stays unless the
     * fresh one beats it by more than [REPLACE_MARGIN] or the shown one is beyond the reach limit.
     */
    fun merge(shown: RouteSection, fresh: RouteSection): RouteSection {
        if (shown.picks.isEmpty()) return fresh
        val resolved = PickKind.entries.mapNotNull { kind ->
            resolve(
                kind = kind,
                shownPick = shown.picks.firstOrNull { kind in it.kinds },
                freshPick = fresh.picks.firstOrNull { kind in it.kinds },
            )
        }
        val picks = LinkedHashMap<String, SectionPick>()
        for ((kind, pick) in resolved) {
            val id = pick.candidate.place.placeId
            picks[id] = picks[id]?.let { it.copy(kinds = it.kinds + kind) } ?: pick.copy(kinds = setOf(kind))
        }
        return fresh.copy(picks = picks.values.toList())
    }

    private fun resolve(kind: PickKind, shownPick: SectionPick?, freshPick: SectionPick?): Pair<PickKind, SectionPick>? {
        val chosen = when {
            shownPick == null -> freshPick
            freshPick == null -> shownPick.takeIf { it.isReachable() }
            freshPick.candidate.place.placeId == shownPick.candidate.place.placeId -> freshPick
            !shownPick.isReachable() -> freshPick
            SectionRanker.adjustedScore(freshPick.candidate) >
                SectionRanker.adjustedScore(shownPick.candidate) + REPLACE_MARGIN -> freshPick
            else -> shownPick
        }
        return chosen?.let { kind to it }
    }

    private fun SectionPick.isReachable() =
        candidate.reachableMinutes <= SectionRanker.MAX_REACH_MINUTES
}
