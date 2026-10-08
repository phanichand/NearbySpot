package com.nearbyspot.domain.usecase

internal class RouteSearchDiagnostics(
    private val clockNanos: () -> Long = System::nanoTime,
    private val emit: (String) -> Unit,
) {
    enum class Stage(val key: String) {
        WAYPOINT_PLANNING("waypoint_planning"),
        SEARCH_DISCOVERY("search_discovery"),
        FILTERING("filtering"),
        REACH_MATRICES("reach_matrices"),
        DETOUR_MATRICES("detour_matrices"),
    }

    enum class Outcome(val key: String) {
        SUCCESS("success"),
        FAILURE("failure"),
        CANCELLATION("cancellation"),
    }

    private val startedAtNanos = clockNanos()
    private val stageNanos = Stage.entries.associateWith { 0L }.toMutableMap()
    private val counters = Counter.entries.associateWith { 0 }.toMutableMap()
    private var emitted = false

    suspend fun <T> measure(stage: Stage, block: suspend () -> T): T {
        val started = clockNanos()
        return try {
            block()
        } finally {
            val elapsed = (clockNanos() - started).coerceAtLeast(0L)
            synchronized(this) {
                stageNanos[stage] = stageNanos.getValue(stage) + elapsed
            }
        }
    }

    fun increment(counter: Counter, amount: Int = 1) {
        synchronized(this) {
            counters[counter] = counters.getValue(counter) + amount
        }
    }

    fun finish(outcome: Outcome) {
        val summary = synchronized(this) {
            if (emitted) return
            emitted = true
            val totalMillis = nanosToMillis((clockNanos() - startedAtNanos).coerceAtLeast(0L))
            val stages = Stage.entries.joinToString(separator = ",") { stage ->
                "${stage.key}:${nanosToMillis(stageNanos.getValue(stage))}"
            }
            val counts = Counter.entries.joinToString(separator = ",") { counter ->
                "${counter.key}:${counters.getValue(counter)}"
            }
            "route_search_summary status=${outcome.key} total_ms=$totalMillis " +
                "stages_ms={$stages} counts={$counts}"
        }
        emit(summary)
    }

    enum class Counter(val key: String) {
        WAYPOINTS_ATTEMPTED("waypoints_attempted"),
        WAYPOINTS_SUCCEEDED("waypoints_succeeded"),
        CANDIDATES_DISCOVERED("candidates_discovered"),
        CANDIDATES_DEDUPED("candidates_deduped"),
        CANDIDATES_FILTERED("candidates_filtered"),
        REACH_VERIFIED("reach_verified"),
        DETOUR_VERIFIED("detour_verified"),
        FINAL_RESULTS("final_results"),
        NEARBY_REPOSITORY_CALLS("nearby_repository_calls"),
        TRAVEL_TIME_REPOSITORY_CALLS("travel_time_repository_calls"),
    }

    private fun nanosToMillis(nanos: Long): Long = nanos / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
