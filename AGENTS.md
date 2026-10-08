# AGENTS.md

## Project overview

NearbySpot is a single-module Android application that recommends nearby places and stops along a route. It is written in Kotlin with Jetpack Compose and follows an MVVM/Clean-style split across presentation, domain, and data packages.

The app uses Google Maps/Places/Routes, Hilt, Retrofit/OkHttp, Room, Coil, coroutines, and `StateFlow`. It targets Android API 35, supports API 26+, and builds with Java 17 and the Gradle wrapper.

## Source of truth

- Treat the implementation and tests as authoritative.
- Use `README.md` for setup and product context.
- Treat `FEATURE_ROADMAP.md` as aspirational and potentially stale. Check the source before assuming a listed feature is unimplemented; favorites, multi-category search, sorting, open-now filtering, photos, dark-map styling, and along-route search already have code.
- Never edit generated content under `app/build/`, `.gradle/`, or `.kotlin/`.

## Repository map

- `app/src/main/java/com/nearbyspot/domain/model/`: app-facing models and filter settings.
- `app/src/main/java/com/nearbyspot/domain/repository/`: repository contracts.
- `app/src/main/java/com/nearbyspot/domain/usecase/`: ranking, nearby search, route search, and favorites orchestration.
- `app/src/main/java/com/nearbyspot/data/remote/`: Google Places integration and Routes API service.
- `app/src/main/java/com/nearbyspot/data/local/`: Room entities, DAO, and database.
- `app/src/main/java/com/nearbyspot/data/repository/`: repository implementation, caching, DTO/domain mapping, and API coordination.
- `app/src/main/java/com/nearbyspot/di/`: Hilt bindings and providers.
- `app/src/main/java/com/nearbyspot/presentation/`: Compose screens, reusable UI, navigation, UI state, and ViewModels.
- `app/src/main/java/com/nearbyspot/util/`: location, geometry, radius, and photo helpers.
- `app/src/test/`: local JVM unit tests.
- `app/src/main/res/raw/map_style_dark.json`: Google Map dark-theme styling.

## Local setup and secrets

Create `secrets.properties` in the repository root with:

```properties
MAPS_API_KEY=your_api_key_here
```

The key is consumed by the Secrets Gradle Plugin as `BuildConfig.MAPS_API_KEY` and as the manifest `MAPS_API_KEY` placeholder. Do not print, commit, copy into source, or include the real value in tests or documentation. `secrets.properties` and `local.properties` are intentionally ignored.

The Google Cloud project must enable Maps SDK for Android, Places API (New), and Routes API. Use a key restricted to the Android application where possible.

## Build and verification

Run commands from the repository root and use the wrapper, not a system Gradle installation.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

For a focused unit-test iteration:

```bash
./gradlew testDebugUnitTest --tests 'com.nearbyspot.domain.usecase.RankPlacesUseCaseTest'
```

Run connected tests only when an emulator or device is available:

```bash
./gradlew connectedDebugAndroidTest
```

Before handing off a normal code change, run the unit tests and the most relevant additional check. For changes to manifests, resources, DI, Room, Retrofit, or Compose wiring, also run `lintDebug` and `assembleDebug`. State clearly when an Android SDK, network access, API credentials, or a device prevented a check.

## Architecture and coding conventions

- Keep dependency direction toward the domain layer: presentation calls use cases/contracts; data implements domain contracts.
- Put Google SDK, Retrofit, Room, and persistence details in `data` or narrowly scoped utilities. Do not leak DTOs or entities into UI state.
- Add cross-layer behavior through a repository interface and a focused use case instead of calling a data source directly from a ViewModel.
- ViewModels expose immutable `StateFlow` and update private `MutableStateFlow` values with `update`/`copy`.
- Launch asynchronous UI work in `viewModelScope`. Preserve `CancellationException` when catching broad exceptions, especially for cancellable searches.
- Keep Composables declarative: pass state down and callbacks up. Hoist durable screen state into a ViewModel; use `remember` only for UI-local state.
- Collect flows in Compose with lifecycle-aware APIs such as `collectAsStateWithLifecycle`.
- Use constructor injection and existing Hilt modules. Scope long-lived services and repositories deliberately.
- Follow official Kotlin style: 4-space indentation, trailing commas in multiline declarations/calls, expression bodies where clearer, and imports instead of repeated fully qualified names.
- Prefer small domain models and named constants over magic values. Keep units explicit in names (`durationSeconds`, `radiusMeters`, `travelTimeMinutes`).
- Put user-facing reusable text in `res/values/strings.xml`; previews, logs, and strictly internal labels may remain local when appropriate.

## Domain-specific invariants

- Nearby search estimates a radius, fetches candidates, obtains route-matrix travel times, then ranks and filters them. Preserve that separation when changing search behavior.
- Google route-matrix and Places result limits are intentional cost/size controls. Review API quotas and batching constraints before increasing them.
- Ranking currently drops places with fewer than 50 reviews, requires matching travel information, enforces time/open/price filters, sorts according to `SortOption`, and returns at most five results. Update `RankPlacesUseCaseTest` whenever these rules change.
- Category selection must never become empty through the existing toggle behavior.
- Route-search refreshes are throttled by both movement distance and elapsed time. Avoid introducing overlapping location collectors or uncancelled search jobs.
- Nearby results are cached by rounded location, radius, and sorted place types. If cache-key semantics or serialized `Place` changes, consider compatibility and stale data behavior.
- Room uses database version 3 and currently opts into destructive fallback. Schema changes must increment the version and should add migration coverage if user data must be retained.
- Photo metadata is held in memory by `PlacesDataSource`; persisted `PhotoRef` values are descriptive and are not sufficient by themselves to fetch a photo after process restart.

## Testing guidance

- Add or update JVM tests for pure calculations, ranking/filter rules, geometry, state-independent use cases, and edge cases.
- Use coroutine test utilities and MockK already declared in `app/build.gradle.kts` when collaborators must be controlled.
- Add instrumentation or Compose UI tests for permission flows, navigation, map/UI interaction, Room integration, and behavior requiring Android framework classes.
- Keep tests deterministic: do not depend on live Google APIs, physical location, wall-clock timing, or real API keys.
- Cover empty results, null optional API fields, cancellation, API failures, boundary travel times, and cache behavior when relevant.

## Change discipline

- Keep changes scoped; do not combine feature work with broad formatting or dependency upgrades.
- Preserve existing public behavior unless the task explicitly changes it.
- Do not commit local configuration, credentials, APKs, reports, generated KSP/Hilt sources, or other `app/build/` outputs.
- When adding a dependency, justify why existing AndroidX/Kotlin facilities are insufficient and keep versions compatible with the Compose BOM, Kotlin 2.1.0, AGP 8.7.3, and Java 17.
- Update `README.md` when setup, permissions, supported SDKs, APIs, or user-visible capabilities change. Update the roadmap only when the task includes planning/status maintenance.
