package com.nearbyspot.presentation.route

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DeparturePlanningTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun millis(hour: Int, minute: Int, day: Int = 6) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a time later today stays today`() {
        val now = millis(8, 0)

        assertEquals(millis(13, 0), nextDepartureAt(hour = 13, minute = 0, nowMillis = now, zone = zone))
    }

    @Test
    fun `a time that has passed today means tomorrow`() {
        val now = millis(14, 0)

        assertEquals(millis(7, 30, day = 7), nextDepartureAt(hour = 7, minute = 30, nowMillis = now, zone = zone))
    }

    @Test
    fun `arrival clock adds the time ahead to the departure`() {
        val departure = millis(9, 0)

        assertEquals(
            millis(11, 15),
            arrivalClockMillis(departureTimeMillis = departure, nowMillis = millis(8, 0), aheadSeconds = 8_100),
        )
    }

    @Test
    fun `arrival clock starts from now when no departure is chosen`() {
        assertEquals(
            millis(8, 45),
            arrivalClockMillis(departureTimeMillis = null, nowMillis = millis(8, 0), aheadSeconds = 2_700),
        )
    }
}
