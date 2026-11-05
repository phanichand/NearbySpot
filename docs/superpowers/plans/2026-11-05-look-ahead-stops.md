# Look-ahead Stops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat along-route list with a drive timeline whose first section is the next stop (15 min target, 45 min limit) and whose later sections, loaded lazily, each offer a "Popular" and a "Trending" pick.

**Architecture:** Pure domain logic (section planning, time-versus-quality ranking, review recency, pick stability) is added beside the existing along-route use case and tested on the JVM. A new `GetRouteSectionsUseCase` loads one section at a time by running the existing `GetAlongRoutePlacesUseCase` on a slice of the route, then re-ranks. `RouteViewModel` holds a list of sections and loads them on demand. The Compose route screen renders the timeline. Trip and monthly Places budgets are enforced where calls are made, in `PlacesUsageBudget`.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM in `app/build.gradle.kts`), Hilt, coroutines/`StateFlow`, MockK, JUnit4, Google Places SDK. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-05-look-ahead-stops-design.md`

## Global Constraints

- Default view target 15 minutes, hard limit 45 minutes (`SectionRanker.TARGET_REACH_MINUTES = 15`, `MAX_REACH_MINUTES = 45`).
- Per-trip Places call ceiling is 200 (`PlacesUsageBudget.TRIP_CALL_LIMIT = 200`). The monthly cap stays at 900 (`MONTHLY_CALL_LIMIT`); do not raise it.
- Later sections load lazily; the next-stop section loads first.
- Popular uses rating and review count; Trending uses recent review ratings and dates (about 6 months, 180 days) for at most 3 finalists per section.
- If review data or budget is unavailable, show the Popular pick only.
- A budget error must not discard results already fetched.
- Navigate opens `https://www.google.com/maps/dir/?api=1&destination=...&waypoints=...`, keeping the original destination.
- Keep monthly cap, Places result limits and route-matrix limits unchanged. Preserve the pipeline: estimate radius, fetch candidates, get travel times, then rank and filter.
- Follow `AGENTS.md`: MVVM with immutable `StateFlow`, `CancellationException` rethrown, user-facing text in `res/values/strings.xml`, no DTOs or entities in UI state, 4-space indent and trailing commas. Update `README.md` for the user-visible change.
- Build on the existing uncommitted work (budget, exception, along-route use case, route screen). Do not rewrite `GetAlongRoutePlacesUseCase`.

## Review Focus

- A budget error partway through a section search: the user still sees the stops already found, plus a short note (Task 2 and Task 6).
- A rural stretch with no qualifying place: the section merges into its neighbour instead of showing an empty row (Task 5 and Task 6).
- A short trip under 45 minutes, or a traveller already near the destination: one section, no crash, no negative or zero-length section (Task 5).
- The review fetch fails or returns no usable dates: the section shows the Popular pick only and does not error (Task 3 and Task 6).
- The traveller changes the destination or transport mode while a section is loading: the old load is cancelled and the trip budget restarts (Task 7).

---

### Task 1: Per-trip budget, refund on failure, count review calls

**Files:**
- Modify: `app/src/main/java/com/nearbyspot/data/remote/PlacesUsageBudget.kt`
- Modify: `app/src/main/java/com/nearbyspot/domain/model/PlacesBudgetExhaustedException.kt`
- Modify: `app/src/main/java/com/nearbyspot/data/remote/PlacesDataSource.kt` (`searchNearby` line 35, `searchAlongRoute` line 74, `fetchReviews` line 104)
- Modify: `app/src/main/java/com/nearbyspot/domain/repository/PlaceRepository.kt` and `data/repository/PlaceRepositoryImpl.kt`
- Test: `app/src/test/java/com/nearbyspot/data/remote/PlacesUsageBudgetTest.kt`

**Interfaces:**
- Produces: `PlacesBudgetExhaustedException(val reason: Reason = Reason.MONTHLY)` with `enum class Reason { MONTHLY, TRIP }`.
- Produces: `PlacesUsageBudget.beginTrip()`, `PlacesUsageBudget.acquire()` (now also counts toward the trip and throws with `Reason.TRIP` once `TRIP_CALL_LIMIT` is reached), `PlacesUsageBudget.release()` (undo the most recent `acquire`, never below zero).
- Produces: `PlaceRepository.beginTrip()` (non-suspending), delegating to the budget.
- Produces: `PlacesUsageBudget.TRIP_CALL_LIMIT = 200`.

- [ ] **Step 1: Write failing tests** in `PlacesUsageBudgetTest`, using the file's existing fake store and month helper:
  - `trip ceiling refuses the 201st call with reason TRIP` (limit set high enough that monthly never triggers).
  - `beginTrip resets the trip count but not the monthly count`.
  - `release gives back one call and never goes below zero`.
  - `monthly exhaustion carries reason MONTHLY`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.data.remote.PlacesUsageBudgetTest'`. Expected: FAIL (members not defined).
- [ ] **Step 3: Implement** the interfaces above. Make the constructor take `tripLimit: Int` with the default `TRIP_CALL_LIMIT` so tests can inject it. Keep `@Synchronized`.
- [ ] **Step 4: In `PlacesDataSource`**, call `acquire()` in `fetchReviews` as well, and call `release()` when a request fails with an exception other than budget exhaustion (including cancellation).
- [ ] **Step 5: Run** the same test command. Expected: PASS. Run `./gradlew testDebugUnitTest` and confirm no other tests broke (fake repositories in tests need a `beginTrip()` stub).
- [ ] **Step 6: Commit** `feat: per-trip Places budget, refund failed calls, count review fetches`.

### Task 2: Partial results survive a budget error

**Files:**
- Modify: `app/src/main/java/com/nearbyspot/domain/usecase/GetAlongRoutePlacesUseCase.kt` (the `PlacesBudgetExhaustedException` handlers around lines 63, 107, 130, 526)
- Test: `app/src/test/java/com/nearbyspot/domain/usecase/GetAlongRoutePlacesUseCaseTest.kt`

**Interfaces:**
- Consumes: `PlacesBudgetExhaustedException` from Task 1.
- Produces: `GetAlongRoutePlacesUseCase.invoke` returns the places found so far when a budget error occurs after at least one anchor returned places; it throws `RouteSearchException(MONTHLY_LIMIT_REACHED, retryable = false)` only when nothing was found. A new `budgetLimited: Boolean` is not added to the return type; instead expose `RouteSearchFailure.TRIP_LIMIT_REACHED` for the trip reason (add to the enum and to `RouteSearchException`'s message map).

- [ ] **Step 1: Write failing tests** following the file's existing fake-repository style:
  - `budget error after first anchor returns the first anchor's places`.
  - `budget error before any place is found throws MONTHLY_LIMIT_REACHED`.
  - `trip-reason budget error with no places throws TRIP_LIMIT_REACHED`.
  - `along-route search keeps places already fetched when a later place type hits the limit` (covers the loop in `PlacesDataSource.searchAlongRoute` through the repository fake).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.GetAlongRoutePlacesUseCaseTest'`. Expected: FAIL.
- [ ] **Step 3: Change the handlers** so the exception is recorded and the search continues to ranking with what it has. Keep `CancellationException` rethrown first.
- [ ] **Step 4: Run** the same command, then `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `fix: return partial stops when the Places budget runs out`.

### Task 3: Review recency signal

**Files:**
- Modify: `app/src/main/java/com/nearbyspot/domain/model/Place.kt` (`Review` gains `publishedAtEpochMillis: Long?`, default `null`)
- Modify: `app/src/main/java/com/nearbyspot/data/remote/PlacesDataSource.kt` (`fetchReviews` mapping, line 110)
- Create: `app/src/main/java/com/nearbyspot/domain/usecase/RecentReviewScorer.kt`
- Test: `app/src/test/java/com/nearbyspot/domain/usecase/RecentReviewScorerTest.kt`

**Interfaces:**
- Produces: `data class RecencySignal(val recentCount: Int, val recentPositiveCount: Int)`.
- Produces: `object RecentReviewScorer { const val WINDOW_DAYS = 180; const val MIN_RECENT_REVIEWS = 3; fun signal(reviews: List<Review>, nowMillis: Long): RecencySignal?; fun trendingScore(signal: RecencySignal): Double }` — `signal` returns `null` when fewer than `MIN_RECENT_REVIEWS` dated reviews fall inside the window. A review is positive at rating 4.0 or higher. `trendingScore` is the share of recent reviews that are positive, in 0.0..1.0.

- [ ] **Step 1: Write failing tests**: `ignores reviews older than 180 days`, `returns null with fewer than 3 recent reviews`, `reviews without a date are ignored`, `18 of 20 recent positive scores 0.9`, `empty list returns null`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.RecentReviewScorerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the scorer.** In `fetchReviews`, fill `publishedAtEpochMillis` from the SDK's review publish time if the Places SDK version in `app/build.gradle.kts` exposes one. If it does not, parse the English relative text in `relativeTimeDescription` ("a month ago", "3 months ago", "a year ago") into an approximate epoch time, and leave `null` for anything unparseable.
- [ ] **Step 4: Run** the test command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: score recent review sentiment for Trending picks`.

### Task 4: Sections, picks and time-versus-quality ranking

**Files:**
- Create: `app/src/main/java/com/nearbyspot/domain/model/RouteSection.kt`
- Create: `app/src/main/java/com/nearbyspot/domain/usecase/SectionRanker.kt`
- Test: `app/src/test/java/com/nearbyspot/domain/usecase/SectionRankerTest.kt`

**Interfaces:**
- Produces in `RouteSection.kt`:
  - `enum class PickKind { POPULAR, TRENDING }`
  - `data class SectionPick(val candidate: AlongRoutePlace, val kinds: Set<PickKind>, val extraMinutesVsBaseline: Int, val extraStarsVsBaseline: Double, val recencySignal: RecencySignal?)`
  - `enum class SectionStatus { NOT_LOADED, LOADING, LOADED, NO_QUALIFYING_PLACE, FAILED }`
  - `data class RouteSection(val index: Int, val startRouteIndex: Int, val endRouteIndex: Int, val aheadSeconds: Int, val status: SectionStatus, val picks: List<SectionPick>, val note: SectionNote? = null)` with `enum class SectionNote { BUDGET_LIMITED, POPULAR_ONLY }`.
- Produces in `SectionRanker`: `TARGET_REACH_MINUTES = 15`, `MAX_REACH_MINUTES = 45`, `fun timeAdjustedScore(quality: Double, reachMinutes: Double): Double`, and `fun rank(candidates: List<AlongRoutePlace>, recency: Map<String, RecencySignal?>): List<SectionPick>`.

Behaviour of `rank`: drop candidates with `reachableMinutes > 45`; quality is `GetAlongRoutePlacesUseCase.smoothedRating(rating, userRatingCount)`; the time penalty grows with distance from 15 minutes (the builder tunes weights); the Popular pick is the top time-adjusted score; the Trending pick is the top by `RecentReviewScorer.trendingScore` among candidates whose recency is non-null; a place that wins both appears once with both kinds, and the Trending slot then goes to the next best Trending candidate if any; the baseline for `extraMinutesVsBaseline` and `extraStarsVsBaseline` is the candidate with the smallest reach time among those with a smoothed rating within 0.5 of the best (its own extras are 0).

- [ ] **Step 1: Write failing tests** with the spec's numbers:
  - `a 4.6 place at 30 minutes beats a 3.9 place at 5 minutes` (equal review counts).
  - `candidate at 46 minutes is excluded and at 45 is kept`.
  - `one place winning both gets both kinds and appears once`.
  - `no recency data returns the Popular pick only`.
  - `empty candidates returns empty list`.
  - `extra minutes and stars are measured against the baseline`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.SectionRankerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the models and `SectionRanker`.
- [ ] **Step 4: Run** the test command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: rank section picks by quality and reach time`.

### Task 5: Section planner and pick stability

**Files:**
- Create: `app/src/main/java/com/nearbyspot/domain/usecase/SectionPlanner.kt`
- Create: `app/src/main/java/com/nearbyspot/domain/usecase/SectionStability.kt`
- Test: `app/src/test/java/com/nearbyspot/domain/usecase/SectionPlannerTest.kt`, `SectionStabilityTest.kt`

**Interfaces:**
- Consumes: `RouteSection`, `SectionPick`, `SectionStatus` from Task 4.
- Produces: `object SectionPlanner { const val HORIZON_SECONDS = 18_000; const val SECTION_SECONDS = 3_600; fun plan(routeInfo: RouteInfo, currentRouteIndex: Int, speedMetersPerSecond: Double?): List<RouteSection> }`. The first section covers now to 45 minutes; later sections are about an hour each up to the horizon or the route end. Speed `null` or non-positive falls back to `routeInfo.distanceMeters / routeInfo.durationSeconds`. Every returned section has `status = NOT_LOADED` and an empty pick list. A route with under 45 minutes left returns exactly one section. `aheadSeconds` is the time until the section starts. Section route indexes come from the cumulative distance along `decodedPoints`.
- Produces: `object SectionStability { const val REPLACE_MARGIN = 0.15; fun merge(shown: RouteSection, fresh: RouteSection): RouteSection }` — a shown pick is replaced by a fresh pick of the same kind only when the fresh time-adjusted score exceeds it by more than `REPLACE_MARGIN`; a pick that is no longer reachable within 45 minutes is always dropped.
- Produces: `SectionPlanner.mergeEmpty(sections: List<RouteSection>): List<RouteSection>` (a member of `SectionPlanner`) — merges every `NO_QUALIFYING_PLACE` section into its previous neighbour, widening that neighbour's `endRouteIndex` (or into the next one when it is the first).

- [ ] **Step 1: Write failing tests** with a synthetic straight-line polyline:
  - `a 3 hour route yields a 45 minute first section then hourly sections`.
  - `route with 30 minutes left yields exactly one section`.
  - `currentRouteIndex at the last point yields one section without crashing`.
  - `null speed falls back to route average`.
  - `empty decodedPoints yields an empty list`.
  - `merge keeps a shown pick unless the fresh one clears the margin`.
  - `merge drops an unreachable shown pick`.
  - `mergeEmpty folds an empty middle section into its neighbour`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.SectionPlannerTest' --tests 'com.nearbyspot.domain.usecase.SectionStabilityTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** both objects, using `PolylineUtils.haversineDistance` for cumulative distance.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: plan route sections and keep picks stable`.

### Task 6: Section loading use case

**Files:**
- Create: `app/src/main/java/com/nearbyspot/domain/usecase/GetRouteSectionsUseCase.kt`
- Test: `app/src/test/java/com/nearbyspot/domain/usecase/GetRouteSectionsUseCaseTest.kt`

**Interfaces:**
- Consumes: `GetAlongRoutePlacesUseCase.invoke(...)`, `PlaceRepository.fetchReviews(placeId: String): List<Review>`, `SectionRanker.rank`, `RecentReviewScorer.signal`, `RouteSection` and friends.
- Produces: `class GetRouteSectionsUseCase @Inject constructor(private val alongRoute: GetAlongRoutePlacesUseCase, private val repository: PlaceRepository, private val clock: () -> Long = System::currentTimeMillis)` with `suspend operator fun invoke(routeInfo: RouteInfo, section: RouteSection, placeTypes: List<String>, filters: RouteFilterSettings): RouteSection` returning the section with status `LOADED`, `NO_QUALIFYING_PLACE`, or `FAILED` and picks filled.

Behaviour: build a `RouteInfo` slice of `routeInfo.decodedPoints` from `startRouteIndex` to `endRouteIndex` with a blank `encodedPolyline` (the existing use case then uses its anchor-based path), call `alongRoute` with `currentLatLng` at the slice start and `currentRouteIndex = 0`; take the 3 best finalists by smoothed rating, fetch their reviews, compute recency, then call `SectionRanker.rank`. Budget errors (either reason) from review fetches mean Popular-only with `note = POPULAR_ONLY`; a budget error from the along-route call with partial results returns them with `note = BUDGET_LIMITED`. Any other review failure also yields Popular-only. `CancellationException` is always rethrown.

- [ ] **Step 1: Write failing tests** using mocked `GetAlongRoutePlacesUseCase` and repository:
  - `loads picks for a section and fetches reviews for at most 3 finalists`.
  - `review fetch failure still returns the Popular pick with POPULAR_ONLY`.
  - `no candidates returns NO_QUALIFYING_PLACE`.
  - `budget error after partial results returns them with BUDGET_LIMITED`.
  - `cancellation propagates`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.GetRouteSectionsUseCaseTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the use case. Verify with a test that a slice with blank `encodedPolyline` does yield candidates through the existing fallback path; if it does not, add the smallest change in `GetAlongRoutePlacesUseCase` that makes the slice work.
- [ ] **Step 4: Run** the test command, then `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: load one route section with Popular and Trending picks`.

### Task 7: ViewModel state and lazy loading

**Files:**
- Modify: `app/src/main/java/com/nearbyspot/presentation/route/RouteUiState.kt`
- Modify: `app/src/main/java/com/nearbyspot/presentation/route/RouteViewModel.kt`
- Test: `app/src/test/java/com/nearbyspot/presentation/route/RouteViewModelTest.kt`

**Interfaces:**
- Consumes: `SectionPlanner.plan`, `SectionPlanner.mergeEmpty`, `SectionStability.merge`, `GetRouteSectionsUseCase`, `PlaceRepository.beginTrip()`.
- Produces: `RouteUiState.sections: List<RouteSection> = emptyList()` and `expandedSectionIndex: Int? = null`. `RouteViewModel.onSectionExpanded(index: Int)` loads that section if its status is `NOT_LOADED` or `FAILED`.
- Keep `alongRoutePlaces`, `selectedPlace` and `highlightedPlaceId` working for the map markers; fill `alongRoutePlaces` from the loaded picks' candidates so existing marker code keeps working.

Behaviour: on a new destination, transport mode or category/filter change, cancel all section jobs, call `repository.beginTrip()` only for a new destination or transport mode, plan sections, and load section 0. A throttled refresh re-plans from the current position, loads the first section, and applies `SectionStability.merge` to any already-loaded section. Section loads share one cancellable job map keyed by section index; never run two loads for the same index. After each load, apply `mergeEmpty` and update with `update { it.copy(...) }`.

- [ ] **Step 1: Write failing tests** with the existing `MainDispatcherRule` and mocks:
  - `selecting a destination loads only the first section`.
  - `expanding a later section loads it once even if tapped twice`.
  - `changing the destination cancels in-flight section loads and calls beginTrip`.
  - `changing a category reloads without calling beginTrip`.
  - `a refresh keeps a shown pick unless the new one clears the margin`.
  - `an empty loaded section is merged into its neighbour`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.presentation.route.RouteViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the state and ViewModel changes. Inject `GetRouteSectionsUseCase`. Preserve the existing refresh throttling constants and `CancellationException` handling.
- [ ] **Step 4: Run** the test command, then `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: route view model loads sections lazily`.

### Task 8: Navigate keeps the destination

**Files:**
- Create: `app/src/main/java/com/nearbyspot/util/MapsNavigationUrl.kt`
- Modify: `app/src/main/java/com/nearbyspot/presentation/detail/PlaceDetailSheet.kt` (navigate handling around lines 278-290) and the route card navigate action in `presentation/route/RoutePresentationComponents.kt`
- Test: `app/src/test/java/com/nearbyspot/util/MapsNavigationUrlTest.kt`

**Interfaces:**
- Produces: `object MapsNavigationUrl { fun build(destination: LatLng, waypoint: LatLng?): String }`. With a waypoint: `https://www.google.com/maps/dir/?api=1&destination=15.8497,74.4977&waypoints=14.4426,75.9215&travelmode=driving`. Without one: the same without the `waypoints` parameter. Coordinates use `Locale.US` and no more than 6 decimal places.
- The route card and the detail sheet from the route screen pass the trip destination; the Home screen's detail sheet keeps `google.navigation:` with no destination.

- [ ] **Step 1: Write failing tests**: `builds a URL with destination and waypoint` (assert the exact string above), `omits waypoints when null`, `uses a dot decimal separator regardless of default locale`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.util.MapsNavigationUrlTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the builder and wire it into the two route-screen entry points with `Intent.ACTION_VIEW`, keeping the existing `geo:` fallback when no handler resolves.
- [ ] **Step 4: Run** the test command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: keep the destination when navigating to a stop`.

### Task 9: Timeline UI, strings and README

**Files:**
- Modify: `app/src/main/java/com/nearbyspot/presentation/route/RouteScreen.kt`
- Modify: `app/src/main/java/com/nearbyspot/presentation/route/RoutePresentationComponents.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `README.md`
- Test: `app/src/test/java/com/nearbyspot/presentation/route/RoutePresentationLogicTest.kt`; add a Compose test under `app/src/androidTest/java/com/nearbyspot/presentation/route/` only if a device is available

**Interfaces:**
- Consumes: `RouteUiState.sections`, `RouteViewModel.onSectionExpanded(Int)`, `MapsNavigationUrl.build`.
- Produces: pure presentation helpers in `RoutePresentationComponents.kt` (or a new `RouteSectionPresentation.kt`) with these signatures: `fun sectionTitle(aheadSeconds: Int): SectionTitle` returning "Next stop" for the first section and "~Nh in" for later ones, and `fun pickReason(pick: SectionPick): PickReason` returning rating and review count, the "+N min, +X stars" comparison (omitted when the extras are 0), and the recent-reviews line when `recencySignal` is non-null.

Replace the single card carousel with the timeline strip from the mockup: "Next stop" first, then sections in drive order; each shows its Popular and Trending picks with labels, the reach time, the reason line, and a Navigate action. A section tap calls `onSectionExpanded`. Show a plain "Nothing qualifying within 45 minutes" line plus the nearest pick for an empty next stop, and a short note for `BUDGET_LIMITED` and `POPULAR_ONLY`. Put all new text in `strings.xml`, including replacing "Popular across your route" and "Popularity rank" with wording that matches the new ranking. README gets a short "Look-ahead stops" section and a note on the per-trip budget.

- [ ] **Step 1: Write failing tests** for the helpers: `first section title is Next stop`, `later section title rounds hours ahead`, `pick reason omits comparison when extras are zero`, `pick reason includes recent reviews line only with a signal`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.nearbyspot.presentation.route.RoutePresentationLogicTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the helpers, then the Composables, keeping them stateless (state down, callbacks up) and collecting with `collectAsStateWithLifecycle`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest lintDebug assembleDebug`. Expected: all pass, no new lint errors.
- [ ] **Step 5: Commit** `feat: route timeline with Popular and Trending picks`.

### Task 10: Verify on the device

**Files:** none changed unless a defect is found.

- [ ] **Step 1:** `./gradlew installDebug`, unlock the phone, and launch the app.
- [ ] **Step 2:** Search Mysuru. Expected: a "Next stop" section whose first pick is within 45 minutes, later sections in drive order, no card list sorted by score, and an early-route stop (before 1 hour) now present.
- [ ] **Step 3:** Search Panaji. Expected: sections out to about 5 hours ahead; tapping a later section loads it once; the visible trip call count stays under 200 (check the diagnostic log line from `RouteSearchDiagnostics`).
- [ ] **Step 4:** Tap Navigate on a pick. Expected: Google Maps opens with the destination kept and the pick as a waypoint.
- [ ] **Step 5:** Turn on airplane mode and expand a section. Expected: a failed-section state or Popular-only, not a crash.
- [ ] **Step 6:** Fix any defect with a test first, then commit.
