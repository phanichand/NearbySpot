package com.nearbyspot.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.google.android.libraries.places.api.net.PlacesClient
import com.nearbyspot.presentation.navigation.NavGraph
import com.nearbyspot.presentation.theme.NearbySpotTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var placesClient: PlacesClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearbySpotTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    placesClient = placesClient,
                )
            }
        }
    }
}
