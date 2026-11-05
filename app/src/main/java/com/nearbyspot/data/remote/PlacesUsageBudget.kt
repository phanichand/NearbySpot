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
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        store = SharedPreferencesUsageStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
        currentMonth = { LocalDate.now(BILLING_ZONE).toString().take(MONTH_KEY_LENGTH) },
        monthlyLimit = MONTHLY_CALL_LIMIT,
    )

    /** Records one billable request, or throws when the monthly budget is exhausted. */
    @Synchronized
    fun acquire() {
        val month = currentMonth()
        val stored = store.read()
        val used = if (stored.month == month) stored.calls else 0
        if (used >= monthlyLimit) throw PlacesBudgetExhaustedException()
        store.write(UsageStore.Usage(month, used + 1))
    }

    companion object {
        /** Deliberately below Google's 1,000 free Enterprise requests to leave a safety margin. */
        const val MONTHLY_CALL_LIMIT = 900
        private const val PREFERENCES_NAME = "places_usage_budget"
        private const val MONTH_KEY_LENGTH = 7
        private val BILLING_ZONE = ZoneId.of("America/Los_Angeles")
    }
}
