package com.nearbyspot.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationRequestGateTest {

    @Test
    fun `new request makes every older response stale`() {
        val gate = DestinationRequestGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun `dismiss invalidates the active response`() {
        val gate = DestinationRequestGate()
        val active = gate.next()

        gate.invalidate()

        assertFalse(gate.isCurrent(active))
    }
}
