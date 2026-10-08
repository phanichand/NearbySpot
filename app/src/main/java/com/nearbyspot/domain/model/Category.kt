package com.nearbyspot.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(
    val label: String,
    val icon: ImageVector,
    val placeTypes: List<String>,
) {
    RESTAURANT(
        label = "Restaurants",
        icon = Icons.Filled.Restaurant,
        placeTypes = listOf("restaurant"),
    ),
    CAFE(
        label = "Cafes",
        icon = Icons.Filled.Coffee,
        placeTypes = listOf("cafe", "coffee_shop"),
    ),
    BAKERY(
        label = "Bakery",
        icon = Icons.Filled.Cake,
        placeTypes = listOf("bakery", "ice_cream_shop"),
    ),
    CULTURE(
        label = "Culture",
        icon = Icons.Filled.Museum,
        placeTypes = listOf("museum", "art_gallery", "cultural_landmark", "historical_landmark"),
    ),
    ATTRACTIONS(
        label = "Attractions",
        icon = Icons.Filled.Attractions,
        placeTypes = listOf("tourist_attraction", "amusement_park"),
    ),
    NATURE(
        label = "Nature",
        icon = Icons.Filled.Forest,
        placeTypes = listOf("park", "national_park", "botanical_garden", "zoo", "aquarium"),
    ),
    SHOPPING(
        label = "Shopping",
        icon = Icons.Filled.ShoppingBag,
        placeTypes = listOf("shopping_mall", "market"),
    ),
    NIGHTLIFE(
        label = "Nightlife",
        icon = Icons.Filled.Nightlife,
        placeTypes = listOf("bar", "night_club"),
    ),
    WELLNESS(
        label = "Wellness",
        icon = Icons.Filled.Spa,
        placeTypes = listOf("spa"),
    ),
}
