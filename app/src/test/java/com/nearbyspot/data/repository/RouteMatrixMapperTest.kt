package com.nearbyspot.data.repository

import com.nearbyspot.data.remote.dto.RouteMatrixElement
import com.nearbyspot.data.remote.dto.RouteMatrixStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMatrixMapperTest {

    @Test
    fun `maps successful sparse out of order elements by their indices`() {
        val elements = listOf(
            element(originIndex = 1, destinationIndex = 2, duration = "12s", distanceMeters = 900),
            element(originIndex = 0, destinationIndex = 1, duration = "3.5s", distanceMeters = 250),
        )

        val result = mapRouteMatrixElements(elements, originCount = 2, destinationCount = 3)

        assertEquals(2, result.size)
        assertEquals(1, result[0].originIndex)
        assertEquals(2, result[0].destinationIndex)
        assertEquals(12, result[0].durationSeconds)
        assertEquals(0, result[1].originIndex)
        assertEquals(1, result[1].destinationIndex)
        assertEquals(4, result[1].durationSeconds)
    }

    @Test
    fun `rejects element level errors and routes that do not exist`() {
        val elements = listOf(
            element(statusCode = 7),
            element(destinationIndex = 1, condition = "ROUTE_NOT_FOUND"),
            element(destinationIndex = 2),
        )

        val result = mapRouteMatrixElements(elements, originCount = 1, destinationCount = 3)

        assertEquals(1, result.size)
        assertEquals(2, result.single().destinationIndex)
    }

    @Test
    fun `rejects missing values invalid indices and negative measurements`() {
        val elements = listOf(
            element(originIndex = null),
            element(destinationIndex = null),
            element(originIndex = -1),
            element(destinationIndex = 2),
            element(duration = null),
            element(duration = "not-a-duration"),
            element(distanceMeters = null),
            element(distanceMeters = -1),
        )

        val result = mapRouteMatrixElements(elements, originCount = 1, destinationCount = 2)

        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `successful status may be omitted by protobuf JSON defaults`() {
        val result = mapRouteMatrixElements(
            elements = listOf(element(statusCode = null)),
            originCount = 1,
            destinationCount = 1,
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `parses and conservatively rounds protobuf durations`() {
        assertEquals(4, parseDurationSeconds("3.000000001s"))
        assertEquals(3, parseDurationSeconds("3.0s"))
        assertEquals(0, parseDurationSeconds("0s"))
        assertNull(parseDurationSeconds("-1s"))
        assertNull(parseDurationSeconds("2147483648s"))
        assertNull(parseDurationSeconds("3"))
        assertNull(parseDurationSeconds(null))
    }

    private fun element(
        originIndex: Int? = 0,
        destinationIndex: Int? = 0,
        duration: String? = "60s",
        distanceMeters: Int? = 1_000,
        statusCode: Int? = 0,
        condition: String? = "ROUTE_EXISTS",
    ) = RouteMatrixElement(
        originIndex = originIndex,
        destinationIndex = destinationIndex,
        duration = duration,
        distanceMeters = distanceMeters,
        status = statusCode?.let { RouteMatrixStatus(code = it, message = null) },
        condition = condition,
    )
}
