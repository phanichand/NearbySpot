package com.nearbyspot.domain.model

sealed interface RouteSearchProgress {
    data object Planning : RouteSearchProgress

    data class SearchingStops(
        val completed: Int,
        val total: Int,
    ) : RouteSearchProgress

    data object CalculatingTravelTimes : RouteSearchProgress

    data object Complete : RouteSearchProgress
}

enum class RouteSearchFailure {
    ALL_ANCHORS_FAILED,
    DESTINATION_TRAVEL_TIME_UNAVAILABLE,
    TIMED_OUT,
    SERVICE_UNAVAILABLE,
    MONTHLY_LIMIT_REACHED,
}

class RouteSearchException(
    val failure: RouteSearchFailure,
    val retryable: Boolean = true,
) : Exception(
    when (failure) {
        RouteSearchFailure.ALL_ANCHORS_FAILED -> "Unable to search route stops"
        RouteSearchFailure.DESTINATION_TRAVEL_TIME_UNAVAILABLE ->
            "Unable to verify travel time to the destination"
        RouteSearchFailure.TIMED_OUT -> "Route stop search timed out"
        RouteSearchFailure.SERVICE_UNAVAILABLE ->
            "Route stop search is temporarily unavailable"
        RouteSearchFailure.MONTHLY_LIMIT_REACHED -> "Monthly search budget reached"
    },
)
