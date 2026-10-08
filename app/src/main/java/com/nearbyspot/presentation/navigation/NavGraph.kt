package com.nearbyspot.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.presentation.home.HomeScreen
import com.nearbyspot.presentation.route.RouteScreen
import com.nearbyspot.presentation.saved.SavedScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    placesClient: PlacesClient,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRoute = {
                    navController.navigate(Screen.Route.route)
                },
                onNavigateToSaved = {
                    navController.navigate(Screen.Saved.route)
                },
            )
        }
        composable(Screen.Route.route) {
            RouteScreen(
                placesClient = placesClient,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Saved.route) {
            SavedScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
