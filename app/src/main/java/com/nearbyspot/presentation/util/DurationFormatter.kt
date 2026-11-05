package com.nearbyspot.presentation.util

import java.util.Locale
import kotlin.math.roundToInt

object DurationFormatter {

    fun route(durationSeconds: Int): String = format(durationSeconds)

    fun ahead(durationSeconds: Int): String = "${format(durationSeconds)} ahead"

    fun detour(durationSeconds: Int): String = "+${format(durationSeconds)} detour"

    private fun format(durationSeconds: Int): String {
        require(durationSeconds >= 0) { "durationSeconds must be non-negative" }

        val totalMinutes = when (durationSeconds) {
            0 -> 0
            else -> (durationSeconds / SECONDS_PER_MINUTE.toDouble())
                .roundToInt()
                .coerceAtLeast(1)
        }
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR

        return buildList {
            if (hours > 0) add(quantity(hours, "hour"))
            if (minutes > 0 || hours == 0) add(quantity(minutes, "minute"))
        }.joinToString(separator = " ")
    }

    private fun quantity(value: Int, unit: String): String =
        "$value $unit${if (value == 1) "" else "s"}"

    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
}

object DistanceFormatter {

    fun format(distanceMeters: Int): String {
        require(distanceMeters >= 0) { "distanceMeters must be non-negative" }

        return when {
            distanceMeters < METERS_PER_KILOMETER -> "$distanceMeters m"
            distanceMeters < WHOLE_KILOMETER_THRESHOLD_METERS -> String.format(
                Locale.ROOT,
                "%.1f km",
                distanceMeters / METERS_PER_KILOMETER.toDouble(),
            )
            else -> String.format(
                Locale.ROOT,
                "%d km",
                (distanceMeters / METERS_PER_KILOMETER.toDouble()).roundToInt(),
            )
        }
    }

    private const val METERS_PER_KILOMETER = 1_000
    private const val WHOLE_KILOMETER_THRESHOLD_METERS = 100_000
}
