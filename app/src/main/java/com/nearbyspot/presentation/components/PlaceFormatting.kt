package com.nearbyspot.presentation.components

import java.util.Locale

/** Compact review count, e.g. 950 -> "950", 12_400 -> "12.4k". */
fun formatReviewCount(count: Int): String = when {
    count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}k"
    else -> count.toString()
}

fun formatPriceLevel(priceLevel: Int): String = "$".repeat(priceLevel)

fun formatRating(rating: Double): String = String.format(Locale.getDefault(), "%.1f", rating)

fun formatDistance(distanceMeters: Int): String = if (distanceMeters >= METERS_PER_KM) {
    String.format(Locale.getDefault(), "%.1f km", distanceMeters / METERS_PER_KM.toDouble())
} else {
    "$distanceMeters m"
}

private const val METERS_PER_KM = 1000
