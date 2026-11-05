package com.nearbyspot.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.nearbyspot.domain.model.PlacesBudgetExhaustedException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface UsageStore {
    fun read(): Usage
    fun write(usage: Usage)

    data class Usage(val month: String, val calls: Int)
}

class SharedPreferencesUsageStore(private val preferences: SharedPreferences) : UsageStore {
    override fun read() = UsageStore.Usage(
        month = preferences.getString(KEY_MONTH, "").orEmpty(),
        calls = preferences.getInt(KEY_CALLS, 0),
    )

    override fun write(usage: UsageStore.Usage) {
        preferences.edit()
            .putString(KEY_MONTH, usage.month)
            .putInt(KEY_CALLS, usage.calls)
            .apply()
    }

    private companion object {
        const val KEY_MONTH = "month"
        const val KEY_CALLS = "calls"
    }
}

/**
 * Counts billable Places Enterprise requests per billing month and refuses new ones once the
 * limit is reached, so the app stays inside the free tier. Google resets its free caps on the
 * first of the month in Pacific time.
 */
@Singleton
class PlacesUsageBudget(
    private val store: UsageStore,
    private val currentMonth: () -> String,
    private val monthlyLimit: Int,
    private val tripLimit: Int = TRIP_CALL_LIMIT,
) {
    private var tripCalls = 0
    private var tripActive = false

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        store = SharedPreferencesUsageStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        currentMonth = { LocalDate.now(BILLING_ZONE).toString().take(MONTH_KEY_LENGTH) },
        monthlyLimit = MONTHLY_CALL_LIMIT,
    )

    /** Starts a new trip: its call ceiling restarts, the monthly count does not. */
    @Synchronized
    fun beginTrip() {
        tripActive = true
        tripCalls = 0
    }

    /** Ends the trip; calls made outside a trip (such as Home searches) have no trip ceiling. */
    @Synchronized
    fun endTrip() {
        tripActive = false
        tripCalls = 0
    }

    /** Records one billable request, or throws when the monthly or trip budget is exhausted. */
    @Synchronized
    fun acquire() {
        val month = currentMonth()
        val stored = store.read()
        val used = if (stored.month == month) stored.calls else 0
        if (used >= monthlyLimit) {
            throw PlacesBudgetExhaustedException(PlacesBudgetExhaustedException.Reason.MONTHLY)
        }
        if (tripActive && tripCalls >= tripLimit) {
            throw PlacesBudgetExhaustedException(PlacesBudgetExhaustedException.Reason.TRIP)
        }
        store.write(UsageStore.Usage(month, used + 1))
        if (tripActive) tripCalls++
    }

    /** Gives back the most recent [acquire] when its request failed before being billed. */
    @Synchronized
    fun release() {
        val month = currentMonth()
        val stored = store.read()
        if (stored.month == month && stored.calls > 0) {
            store.write(UsageStore.Usage(month, stored.calls - 1))
        }
        if (tripCalls > 0) tripCalls--
    }

    companion object {
        /** Deliberately below Google's 1,000 free Enterprise requests to leave a safety margin. */
        const val MONTHLY_CALL_LIMIT = 900

        /** Ceiling for one planned trip, so a long route cannot spend the whole month. */
        const val TRIP_CALL_LIMIT = 200
        private const val PREFERENCES_NAME = "places_usage_budget"
        private const val MONTH_KEY_LENGTH = 7
        private val BILLING_ZONE = ZoneId.of("America/Los_Angeles")
    }
}
