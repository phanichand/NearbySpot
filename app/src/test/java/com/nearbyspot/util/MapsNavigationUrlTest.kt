package com.nearbyspot.util

import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MapsNavigationUrlTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun `builds a URL with the destination and the stop as a waypoint`() {
        val url = MapsNavigationUrl.build(
            destination = LatLng(15.8497, 74.4977),
            waypoint = LatLng(14.4426, 75.9215),
        )

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=15.8497,74.4977" +
                "&waypoints=14.4426,75.9215&travelmode=driving",
            url,
        )
    }

    @Test
    fun `omits waypoints when there is none`() {
        val url = MapsNavigationUrl.build(destination = LatLng(15.8497, 74.4977), waypoint = null)

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=15.8497,74.4977&travelmode=driving",
            url,
        )
    }

    @Test
    fun `uses a dot decimal separator regardless of the default locale`() {
        Locale.setDefault(Locale.GERMANY)

        val url = MapsNavigationUrl.build(LatLng(12.5, 77.25), LatLng(13.5, 76.75))

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=12.5,77.25" +
                "&waypoints=13.5,76.75&travelmode=driving",
            url,
        )
    }

    @Test
    fun `rounds coordinates to 6 decimal places`() {
        val url = MapsNavigationUrl.build(LatLng(12.123456789, 77.0), waypoint = null)

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=12.123457,77&travelmode=driving",
            url,
        )
    }
}
