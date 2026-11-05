package com.nearbyspot.domain.model

/** Thrown instead of calling Places once the on-device monthly free-tier budget is used up. */
class PlacesBudgetExhaustedException : Exception("Monthly Places search budget reached")
