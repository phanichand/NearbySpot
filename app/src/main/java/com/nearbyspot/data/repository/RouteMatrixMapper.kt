package com.nearbyspot.data.repository

import com.nearbyspot.data.remote.dto.RouteMatrixElement
import com.nearbyspot.domain.model.RouteMatrixEntry
import java.math.RoundingMode

internal fun mapRouteMatrixElements(
    elements: List<RouteMatrixElement>,
    originCount: Int,
    destinationCount: Int,
): List<RouteMatrixEntry> = elements.mapNotNull { element ->
    val originIndex = element.originIndex
        ?.takeIf { it in 0 until originCount }
        ?: return@mapNotNull null
    val destinationIndex = element.destinationIndex
        ?.takeIf { it in 0 until destinationCount }
        ?: return@mapNotNull null
    val statusCode = element.status?.code ?: STATUS_OK
    if (statusCode != STATUS_OK || element.condition != ROUTE_EXISTS) {
        return@mapNotNull null
    }
    val durationSeconds = parseDurationSeconds(element.duration) ?: return@mapNotNull null
    val distanceMeters = element.distanceMeters?.takeIf { it >= 0 } ?: return@mapNotNull null

    RouteMatrixEntry(
        originIndex = originIndex,
        destinationIndex = destinationIndex,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
    )
}

internal fun parseDurationSeconds(value: String?): Int? {
    val seconds = value
        ?.takeIf { it.endsWith(DURATION_SUFFIX) }
        ?.dropLast(DURATION_SUFFIX.length)
        ?.toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 }
        ?: return null

    return runCatching {
        seconds.setScale(0, RoundingMode.CEILING).intValueExact()
    }.getOrNull()
}

private const val STATUS_OK = 0
private const val ROUTE_EXISTS = "ROUTE_EXISTS"
private const val DURATION_SUFFIX = "s"
