# Route Search Baseline

This document records the repeatable baseline for the default restaurant search from Bengaluru to the `Goa, India` autocomplete result.

## Scope

- Git revision and APK SHA-256 are recorded for every session.
- `cold` and `warm` refer only to NearbySpot's 30-minute Room place cache.
- Every run starts from a freshly launched process.
- Google SDK, DNS, TLS, and operating-system caches are not controlled.
- Default route settings are used: Restaurants, Drive, 15-minute maximum detour, and Open Now off.
- Raw Logcat must not be saved or shared. The current debug build can log coordinates, request bodies, and the API key. Only aggregate diagnostics produced by the app may be recorded.

## Run sequence

Use paired runs so each warm run immediately follows its matching cold run:

```text
C1 -> W1 -> C2 -> W2 -> C3 -> W3
```

Before every run:

1. Keep the device stationary, plugged in, on the same network, with battery saver disabled.
2. Force-stop NearbySpot and relaunch it.
3. Allow ten seconds for location acquisition.
4. Open Plan a route, enter `Goa`, and select the exact `Goa, India` prediction.
5. Start timing when the prediction is selected.
6. Stop at the first terminal results, empty, or error state. Record a timeout at five minutes.

Do not use the Refresh action for warm runs because it does not repeat the complete destination-selection flow.

## Cache handling

A cold run requires only `cached_places` to be empty. Favorites must remain untouched.

Before a selective reset:

1. Force-stop the app.
2. Confirm debug `run-as` access and SQLite tooling.
3. Back up the database files to a recoverable location.
4. Record the favorites count.
5. Delete rows only from `cached_places` in a transaction.
6. Run `PRAGMA integrity_check` and confirm the favorites count is unchanged.

Never use `pm clear`, uninstall the app, delete the database files, or remove WAL files to create a cold run. If a verified selective reset is unavailable, wait longer than the 30-minute TTL and label the run according to observed request behavior.

A run is `cold-confirmed` only when aggregate diagnostics show all planned waypoint searches reached the remote Places source. A run is `warm-confirmed` only when the same route, location, category, and filters produce fewer remote Places searches within the TTL. The current cache schema can return partial hits, so cache status must be observed rather than assumed.

## Metrics

| Pair | Run | Cache status | Total (s) | Anchor search (s) | Reach matrices (s) | Detour matrices (s) | Anchors attempted/succeeded | Places calls | Matrix calls | Discovered/deduped | Reach/detour verified | Displayed | Outcome |
|---|---|---|---:|---:|---:|---:|---|---:|---:|---|---|---:|---|
| 1 | C1 | Pending | | | | | | | | | | | |
| 1 | W1 | Pending | | | | | | | | | | | |
| 2 | C2 | Pending | | | | | | | | | | | |
| 2 | W2 | Pending | | | | | | | | | | | |
| 3 | C3 | Pending | | | | | | | | | | | |
| 3 | W3 | Pending | | | | | | | | | | | |

Report the median and range for the three confirmed cold runs and three confirmed warm runs. With only three samples, do not report percentiles.

## Pre-instrumentation observation

An exploratory Bengaluru-to-Goa run on the Pixel 8 Pro produced a 568 km route with a displayed duration of 638 minutes. Restaurant results appeared after approximately two minutes. This was not a controlled benchmark and is retained only as a qualitative reference.

