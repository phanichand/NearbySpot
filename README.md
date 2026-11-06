# NearbySpot

A smart Android app that recommends nearby popular places based on Google Maps reviews, travel time, and transport mode. Built for tourists and locals who want to quickly find the best restaurants, cafes, attractions, and more around them.

## Features

- **Category-based search**: Restaurants, Cafes, Bakeries, Culture, Attractions, Nature, Shopping, Nightlife, Wellness
- **Travel-time filtering**: Find places within 5, 10, or 15 minutes
- **Transport mode**: Walk, Drive, or Bicycle
- **Smart ranking**: Popularity, rating, and proximity combine to favor well-established places that are still convenient
- **Price filtering**: Filter by price level ($, $$, $$$, $$$$)
- **Place details**: Ratings, reviews, open status, address, and one-tap navigation to Google Maps
- **Along-route discovery**: Plan a route and find verified stops without exceeding the selected detour
- **Quality threshold**: Route stops require at least 100 Google reviews
- **Quality-first route results**: Search the full route plus the destination area, rank verified stops by a review-smoothed rating, skip stops right at the start of long trips, and spread results across the route
- **Look-ahead stops**: The route screen opens on a "Next stop" section (about 15 minutes ahead, never more than 45) followed by 45-minute sections you can open to plan later stops, such as lunch while you are still at breakfast. Each section offers a "Popular" pick (rating and review count) and a "Trending" pick (recent reviews), and each pick shows what its extra drive buys
- **Plan from anywhere**: Under the search bar, choose a start point other than your current location and a departure time ("Leave now" or a clock time). Each section shows the clock time you reach it. A planned trip is not tracked live, and a later driving departure uses traffic-aware route timing.
- **Budgeted Places usage**: A trip may use about 200 Places calls and the app caps itself at 900 a month; later sections load only when opened, and Navigate keeps your destination with the stop as a waypoint
- **Saved places**: Keep favorites available from a dedicated saved screen
- **Adaptive design**: Responsive Material 3 surfaces with light and dark themes

## Tech Stack

- Kotlin + Jetpack Compose
- MVVM + Clean Architecture
- Hilt (DI), Retrofit + OkHttp, Room
- Google Maps SDK, Places SDK (New), Routes API

## Setup

1. Create a [Google Cloud project](https://console.cloud.google.com/) and enable:
   - Maps SDK for Android
   - Places API (New)
   - Routes API
2. Create an API key restricted to your Android app's SHA-1 fingerprint
3. Create `secrets.properties` in the project root:
   ```
   MAPS_API_KEY=your_api_key_here
   ROUTES_API_KEY=your_routes_api_key_here
   ```
   `ROUTES_API_KEY` may be omitted for local development, in which case the app reuses `MAPS_API_KEY`. Production releases should use the backend or dedicated-key strategy described below.
4. Open the project in Android Studio and sync Gradle
5. Run on an Android device or emulator (min SDK 26)

`local.defaults.properties` contains a non-functional placeholder so clean checkouts and CI can compile without a real credential. Never replace that tracked value with a real key.

## Verification and release

Run the standard local checks with:

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease compileDebugAndroidTestKotlin
```

Release builds use R8 and resource shrinking. Distribution signing is intentionally not stored in the repository; see the [release checklist](docs/RELEASE_CHECKLIST.md) for API-key restrictions, device smoke tests, and Play App Signing guidance.

## Privacy and data handling

NearbySpot uses foreground location to search Google Places and calculate Google Routes. Search results are cached on-device for up to 24 hours, including an approximately rounded search location; saved places remain in the local Room database until the app data is cleared or the app is uninstalled. Android cloud backup and device-transfer backup are disabled so this location-derived data is not copied automatically.

Location and route inputs are sent to Google Maps Platform services under Google's applicable terms. A production release should publish a matching privacy policy and Play data-safety declaration. Because a mobile APK cannot keep a REST credential secret, high-volume production deployments should proxy Routes API calls through a controlled backend; otherwise use the separate `ROUTES_API_KEY` with least-privilege API restrictions, tight quotas, and billing alerts.

## Project Structure

```
app/src/main/java/com/nearbyspot/
├── di/                  # Hilt dependency injection modules
├── data/
│   ├── remote/          # PlacesDataSource, RoutesApiService
│   ├── local/           # Room database, DAO, entities
│   └── repository/      # PlaceRepositoryImpl
├── domain/
│   ├── model/           # Place, Category, TravelInfo, FilterSettings
│   ├── repository/      # PlaceRepository interface
│   └── usecase/         # GetNearbyPlaces, GetTravelTimes, RankPlaces
├── presentation/
│   ├── home/            # HomeScreen, HomeViewModel
│   ├── route/           # Route planning and along-route discovery
│   ├── saved/           # Favorite places
│   ├── detail/          # PlaceDetailSheet
│   ├── filter/          # FilterBar
│   ├── components/      # CategoryChips, PlaceCard, RatingBar
│   ├── navigation/      # NavGraph, Screen
│   └── theme/           # Material 3 theme
└── util/                # LocationHelper, RadiusEstimator
```
# NearbySpot
