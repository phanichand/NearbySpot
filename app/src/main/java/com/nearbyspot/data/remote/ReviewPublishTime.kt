package com.nearbyspot.data.remote

import java.time.Instant
import java.time.format.DateTimeParseException

/** Converts a Places review `publishTime` (RFC 3339) to epoch millis, or null when unusable. */
internal fun parseReviewPublishTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
