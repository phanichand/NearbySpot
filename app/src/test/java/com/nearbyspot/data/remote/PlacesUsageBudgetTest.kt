package com.nearbyspot.data.remote

import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlacesUsageBudgetTest {

    private class FakeStore : UsageStore {
        var usage = UsageStore.Usage(month = "", calls = 0)
        override fun read() = usage
        override fun write(usage: UsageStore.Usage) {
            this.usage = usage
        }
    }

    private val store = FakeStore()
    private var month = "2026-10"
    private val budget = PlacesUsageBudget(store, currentMonth = { month }, monthlyLimit = 3)

    @Test
    fun `calls are allowed up to the limit then refused`() {
        repeat(3) { budget.acquire() }

        assertEquals(3, store.usage.calls)
        assertThrows(PlacesBudgetExhaustedException::class.java) { budget.acquire() }
        assertEquals(3, store.usage.calls)
    }

    @Test
    fun `a new billing month resets the count`() {
        repeat(3) { budget.acquire() }
        month = "2026-11"

        budget.acquire()

        assertEquals(UsageStore.Usage("2026-11", 1), store.usage)
    }

    @Test
    fun `usage stored for an earlier month does not count`() {
        store.usage = UsageStore.Usage("2026-09", 900)

        budget.acquire()

        assertEquals(UsageStore.Usage("2026-10", 1), store.usage)
    }
}
