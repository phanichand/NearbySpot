package com.nearbyspot.presentation.route

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The next moment the clock reads [hour]:[minute]: today if still ahead, otherwise tomorrow. */
internal fun nextDepartureAt(hour: Int, minute: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    var candidate = LocalDateTime.of(LocalDate.from(now), LocalTime.of(hour, minute)).atZone(zone)
    if (!candidate.toInstant().isAfter(now.toInstant())) candidate = candidate.plusDays(1)
    return candidate.toInstant().toEpochMilli()
}

/** Clock time at which the traveller reaches a point [aheadSeconds] into the drive. */
internal fun arrivalClockMillis(departureTimeMillis: Long?, nowMillis: Long, aheadSeconds: Int): Long =
    (departureTimeMillis ?: nowMillis) + aheadSeconds * MILLIS_PER_SECOND

private const val MILLIS_PER_SECOND = 1_000L
