package com.nearbyspot.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Route : Screen("route")
    data object Saved : Screen("saved")
}
