# NearbySpot

A smart Android app that recommends nearby popular places based on Google Maps reviews, travel time, and transport mode. Built for tourists and locals who want to quickly find the best restaurants, cafes, attractions, and more around them.

## Features

- **Category-based search**: Restaurants, Cafes, Bakeries, Culture, Attractions, Nature, Shopping, Nightlife, Wellness
- **Travel-time filtering**: Find places within 5, 10, or 15 minutes
- **Transport mode**: Walk, Drive, or Bicycle
- **Smart ranking**: Places ranked by `rating * ln(1 + reviewCount) * timePenalty` -- popular places closer to you rank higher
- **Price filtering**: Filter by price level ($, $$, $$$, $$$$)
- **Place details**: Ratings, reviews, open status, address, and one-tap navigation to Google Maps

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
3. Copy `secrets.properties.example` or create `secrets.properties` in the project root:
   ```
   MAPS_API_KEY=your_api_key_here
   ```
4. Open the project in Android Studio and sync Gradle
5. Run on an Android device or emulator (min SDK 26)

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
│   ├── detail/          # PlaceDetailSheet
│   ├── filter/          # FilterBar
│   ├── components/      # CategoryChips, PlaceCard, RatingBar
│   ├── navigation/      # NavGraph, Screen
│   └── theme/           # Material 3 theme
└── util/                # LocationHelper, RadiusEstimator
```
# NearbySpot
