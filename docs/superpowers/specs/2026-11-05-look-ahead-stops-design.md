# Look-ahead stops: design

Date: 2026-11-05. Status: draft for approval.

## Intent and why

On a long drive (for example Bangalore to Goa), the traveller opens NearbySpot, enters a destination, and wants to know where to stop next and where to stop later. Today the route screen returns a flat list of up to 10 places ranked by score. On a device run on 2026-11-05:

- Bangalore to Mysuru (154 km, 2 h 50 min): 4 of 6 stops were near Mysuru, 2 were mid-route at 1 h 13 min and 1 h 37 min, and nothing was offered in the first hour.
- Bangalore to Panaji (596 km, 11 h 24 min): 10 stops, with several bunched near Goa.
- Cards were ordered by score, not by position along the route.
- Navigate opened Google Maps with only the chosen stop, so the destination was lost.

What the traveller said they want, in their own words:

- When I enter a destination, tell me the good place in about 15 minutes. I'm fine travelling longer for a better place, for example a better place in 30 minutes beats a worse one in 5.
- More than 45 minutes is too far.
- I want to plan ahead as an option, for example choose lunch while I'm at breakfast. Show me where I'll be in the next 4 to 5 hours, then show better places en route.
- "Good" means recent, highly rated reviews, or a popular place with the most reviews overall. Show both.

## Goals

1. The route screen opens on the traveller's next stop: the best place about 15 minutes ahead, with the hard limit of 45 minutes.
2. A timeline of the drive shows the sections ahead (for example "~2h in", "~4h in", "~5h in"), each with its best picks. Tapping a section shows all its picks.
3. Each section offers two kinds of pick, labelled "Popular" and "Trending". If one place wins both, show it once with both labels.
4. Each pick says what the extra time buys, for example "+22 min, +0.4 stars", and why it was picked, for example the rating and review count.
5. Sections follow the traveller's real position and speed, and update as they drive.

## Non-goals

- Offline use, accounts, a backend, or itinerary export.
- Changing Home (nearby) mode.
- Fuel, restroom and EV-charging categories. The traveller did not answer the question about other categories, so this is not decided here. It is a candidate follow-up.

## Anti-goals (failure even if it works)

- A list that looks spread out but leaves the first hour empty (the Mysuru result).
- Hitting the monthly Places budget on one long trip and showing only an error.
- Cards the traveller must swipe back and forth along the route to read.

## Decisions made with the traveller

- Default view: next stop, 15-minute target, 45-minute hard limit.
- Ahead-of-you view: timeline strip with sections by time ahead (mockup B was chosen over a time slider).
- Two pick types per section, "Popular" and "Trending", both shown.

- **Per-trip budget:** about 200 Places calls per trip is the ceiling.
- **Starting point:** build on top of the current uncommitted work (route screen, `PlacesUsageBudget`, `PlacesBudgetExhaustedException`, the along-route use case) and improve it wherever the builder finds something worth fixing.

**Monthly cap stays at 900 (the traveller decided not to raise it).** 200 calls per trip is therefore a ceiling for a heavy, fully planned trip, not the typical cost. Lazy loading keeps ordinary trips well under it, and the traveller should know that about 4 fully planned trips would use the month's budget.

## My calls (not yet confirmed by the traveller)

- **Section length:** roughly 1 hour of driving each, up to the 4 to 5 hour horizon the traveller named, with a way to see further. The builder picks exact values, but a section is a stretch of the route, not a point.
- **45-minute limit in later sections:** applies as a detour or reach limit around the section's centre, not from the traveller's current position. The existing `maxDetourMinutes` filter is the base for this.
- **"Trending" cost control:** recent-review checks run only for finalists per section, because each review fetch is a separate paid Places call and the app has a 900-calls-a-month client budget.
- **Order:** sections in drive order. Within a section, the Popular pick comes first, then Trending.
- **Navigate:** when the traveller taps Navigate on a pick, open Google Maps with the original destination kept and the pick as a waypoint. Use the Google Maps URL scheme (`https://www.google.com/maps/dir/?api=1&destination=...&waypoints=...`), because the current `google.navigation:` scheme cannot carry a waypoint. One waypoint is well inside its limit.
- **Time versus quality:** each candidate gets a score where quality counts most and a penalty grows with reach time, centred on 15 minutes. Anything beyond 45 minutes is excluded. The traveller's example (a better place at 30 min beats a worse one at 5) must hold in tests. The builder tunes the weights.
- **Review minimum and result caps:** keep the existing minimum review count for Popular. Trending needs at least a few recent reviews to qualify. The caps on results per section stay small, and the builder sets them.
- **Baseline for "+22 min, +0.4 stars":** the comparison is against the nearest acceptable pick in the same section (the 15-minute pick for the next-stop section).
- **Loading order:** the next-stop section loads first. Later sections load lazily, as the traveller scrolls to or taps them, so a trip the traveller never plans ahead for costs nothing extra. Candidates for a section come from a fetch around that section, not one route-wide fetch.
- **Trending data:** use the review dates and ratings the Places response already gives, for a small number of finalists per section (about 3). Google returns only a handful of reviews per place, so this is a weak signal, and the card must not claim more than the data shows. If the review call fails or the budget is out, show the Popular pick only and say nothing alarming.
- **Recency window:** reviews from roughly the last 6 months count as recent. The builder may adjust.
- **Stability:** sections do not reshuffle while the traveller reads them. A new refresh replaces a shown pick only when the new one is clearly better. Speed comes from recent location updates already collected, with a fallback to the route's own travel estimate.
- **Empty sections:** a section with no qualifying place merges into its neighbour. If no place qualifies within 45 minutes for the next stop, say so plainly and show the nearest qualifying pick beyond it, labelled with how far it is.
- **Beyond the 4 to 5 hour horizon:** the strip simply extends, loaded lazily like the other sections.
- **Budget errors:** a budget error must not discard results already fetched. Show what was found, plus a short note.
- **Starting point:** build on top of the existing along-route use case rather than rewriting it, and fix the two budget bugs (discarded partial results, charge with no refund on failure) as part of this change, because the new design depends on them. The other two review findings (too-close-to-start and slot mismatch) are replaced by the new section logic.

## Constraints

- Android app: Kotlin, Compose, Hilt, MVVM/Clean as described in `AGENTS.md`. Dependencies point toward the domain layer; no DTOs or entities in UI state.
- Preserve the nearby-search pipeline separation: estimate radius, fetch candidates, get route-matrix times, then rank and filter.
- Google Places and route-matrix result limits are intentional cost controls. Review quotas before raising any.
- Route-search refresh stays throttled by distance moved and time elapsed. Avoid overlapping collectors or uncancelled search jobs.
- Keep user-facing text in `strings.xml`. Update `README.md` for user-visible capability changes. Update `RankPlacesUseCaseTest` or the route use case tests whenever ranking rules change.
- Existing bugs the new design must not repeat, from the 2026-11-05 code review:
  - A budget error thrown mid-search must not discard results already fetched.
  - Budget is charged before a request and not refunded when it fails.
  - The "too close to start" filter uses remaining trip time on every refresh.
  - Gap-fill and final selection slot the route differently.

## Left to the builder

- Exact section boundaries, number of sections and picks per section.
- The scoring formula for "Popular" and "Trending", provided Popular uses rating and review count and Trending uses recent review ratings and dates.
- Exact copy, layout details and animation.
- How refresh works while driving, within the throttling constraint.
- Test design and how to split the work into steps.

## Open questions for later

- Other categories (fuel, restrooms, rest stops) and whether they belong in this change.
- Whether tracking must continue when Google Maps is in the foreground. Today the app requests only foreground location permission and has no foreground service.
- Whether the 900-call monthly budget is the right model for a trip-planning feature.
