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

    private fun budgetWithTripLimit(tripLimit: Int, monthlyLimit: Int = 1_000) =
        PlacesUsageBudget(store, currentMonth = { month }, monthlyLimit = monthlyLimit, tripLimit = tripLimit)

    @Test
    fun `trip ceiling refuses the next call with reason TRIP`() {
        val tripBudget = budgetWithTripLimit(tripLimit = 2)
        tripBudget.beginTrip()
        repeat(2) { tripBudget.acquire() }

        val error = assertThrows(PlacesBudgetExhaustedException::class.java) { tripBudget.acquire() }

        assertEquals(PlacesBudgetExhaustedException.Reason.TRIP, error.reason)
        assertEquals(2, store.usage.calls)
    }

    @Test
    fun `beginTrip resets the trip count but not the monthly count`() {
        val tripBudget = budgetWithTripLimit(tripLimit = 2)
        tripBudget.beginTrip()
        repeat(2) { tripBudget.acquire() }

        tripBudget.beginTrip()
        tripBudget.acquire()

        assertEquals(3, store.usage.calls)
    }

    @Test
    fun `release gives back one call and never goes below zero`() {
        budget.acquire()
        budget.acquire()

        budget.release()
        assertEquals(1, store.usage.calls)

        budget.release()
        budget.release()
        assertEquals(0, store.usage.calls)
    }

    @Test
    fun `release after a trip reset does not free trip headroom beyond zero`() {
        val tripBudget = budgetWithTripLimit(tripLimit = 1)
        tripBudget.beginTrip()
        tripBudget.acquire()
        tripBudget.beginTrip()

        tripBudget.release()
        tripBudget.acquire()

        assertThrows(PlacesBudgetExhaustedException::class.java) { tripBudget.acquire() }
    }

    @Test
    fun `monthly exhaustion carries reason MONTHLY`() {
        repeat(3) { budget.acquire() }

        val error = assertThrows(PlacesBudgetExhaustedException::class.java) { budget.acquire() }

        assertEquals(PlacesBudgetExhaustedException.Reason.MONTHLY, error.reason)
    }

    @Test
    fun `the trip ceiling only applies while a trip is active`() {
        val tripBudget = budgetWithTripLimit(tripLimit = 1)

        repeat(3) { tripBudget.acquire() }
        assertEquals(3, store.usage.calls)

        tripBudget.beginTrip()
        tripBudget.acquire()
        assertThrows(PlacesBudgetExhaustedException::class.java) { tripBudget.acquire() }

        tripBudget.endTrip()
        tripBudget.acquire()
        assertEquals(5, store.usage.calls)
    }
}
