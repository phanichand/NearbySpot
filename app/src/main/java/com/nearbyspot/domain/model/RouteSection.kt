package com.nearbyspot.domain.model

import com.nearbyspot.domain.usecase.RecencySignal

enum class PickKind { POPULAR, TRENDING }

/** A recommended stop in a section, with why it was chosen and what its extra drive buys. */
data class SectionPick(
    val candidate: AlongRoutePlace,
    val kinds: Set<PickKind>,
    val extraMinutesVsBaseline: Int,
    val extraStarsVsBaseline: Double,
    val recencySignal: RecencySignal?,
    /** Time from the traveller to the stop: the section's own lead time plus the reach inside it. */
    val timeAheadSeconds: Int = 0,
)

enum class SectionStatus { NOT_LOADED, LOADING, LOADED, NO_QUALIFYING_PLACE, FAILED }

enum class SectionNote { BUDGET_LIMITED, POPULAR_ONLY }

/** A stretch of the drive ahead; [aheadSeconds] is how long until the traveller reaches its start. */
data class RouteSection(
    val index: Int,
    val startRouteIndex: Int,
    val endRouteIndex: Int,
    val aheadSeconds: Int,
    val status: SectionStatus,
    val picks: List<SectionPick>,
    val note: SectionNote? = null,
    val failure: RouteSearchFailure? = null,
)
