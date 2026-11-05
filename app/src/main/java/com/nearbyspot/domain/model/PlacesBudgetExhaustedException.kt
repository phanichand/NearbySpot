package com.nearbyspot.domain.model

/** Thrown instead of calling Places once the monthly or per-trip budget is used up. */
class PlacesBudgetExhaustedException(
    val reason: Reason = Reason.MONTHLY,
) : Exception(
    when (reason) {
        Reason.MONTHLY -> "Monthly Places search budget reached"
        Reason.TRIP -> "Trip Places search budget reached"
    },
) {
    enum class Reason { MONTHLY, TRIP }
}
