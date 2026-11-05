package com.nearbyspot.util

import com.google.android.gms.maps.model.LatLng
import java.math.BigDecimal
import java.math.RoundingMode

object MapsNavigationUrl {
    private const val BASE = "https://www.google.com/maps/dir/?api=1"
    private const val COORDINATE_DECIMALS = 6

    /** Directions to [destination], optionally passing through [waypoint], for Google Maps. */
    fun build(destination: LatLng, waypoint: LatLng?): String = buildString {
        append(BASE)
        append("&destination=").append(format(destination))
        if (waypoint != null) append("&waypoints=").append(format(waypoint))
        append("&travelmode=driving")
    }

    private fun format(point: LatLng) = "${coordinate(point.latitude)},${coordinate(point.longitude)}"

    private fun coordinate(value: Double): String =
        BigDecimal(value).setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
