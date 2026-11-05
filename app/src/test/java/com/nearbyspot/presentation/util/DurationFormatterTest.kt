package com.nearbyspot.presentation.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun `route duration handles zero and positive sub-minute values`() {
        assertEquals("0 minutes", DurationFormatter.route(0))
        assertEquals("1 minute", DurationFormatter.route(1))
        assertEquals("1 minute", DurationFormatter.route(59))
    }

    @Test
    fun `route duration handles exact hours without trailing zero minutes`() {
        assertEquals("1 hour", DurationFormatter.route(3_600))
        assertEquals("2 hours", DurationFormatter.route(7_200))
    }

    @Test
    fun `route duration pluralizes mixed hours and minutes`() {
        assertEquals("1 hour 1 minute", DurationFormatter.route(3_660))
        assertEquals("2 hours 2 minutes", DurationFormatter.route(7_320))
        assertEquals("10 hours 38 minutes", DurationFormatter.route(38_280))
    }

    @Test
    fun `ahead duration appends journey context`() {
        assertEquals("3 hours 26 minutes ahead", DurationFormatter.ahead(12_360))
        assertEquals("1 minute ahead", DurationFormatter.ahead(60))
    }

    @Test
    fun `detour duration includes plus sign and singular or plural units`() {
        assertEquals("+0 minutes detour", DurationFormatter.detour(0))
        assertEquals("+1 minute detour", DurationFormatter.detour(60))
        assertEquals("+2 minutes detour", DurationFormatter.detour(120))
        assertEquals("+1 hour detour", DurationFormatter.detour(3_600))
    }

    @Test
    fun `duration rejects negative input`() {
        assertThrows(IllegalArgumentException::class.java) {
            DurationFormatter.route(-1)
        }
    }

    @Test
    fun `distance uses meters below one kilometer`() {
        assertEquals("0 m", DistanceFormatter.format(0))
        assertEquals("999 m", DistanceFormatter.format(999))
    }

    @Test
    fun `kilometer output is stable when default locale uses decimal commas`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.0 km", DistanceFormatter.format(1_000))
            assertEquals("1.5 km", DistanceFormatter.format(1_500))
            assertEquals("99.9 km", DistanceFormatter.format(99_900))
            assertEquals("568 km", DistanceFormatter.format(568_000))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `distance rejects negative input`() {
        assertThrows(IllegalArgumentException::class.java) {
            DistanceFormatter.format(-1)
        }
    }
}
