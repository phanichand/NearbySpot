package com.nearbyspot.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val permissions = locationPermissions()
        return LocationPermissionPolicy.hasPermission(
            hasFinePermission = permissions.hasFine,
            hasCoarsePermission = permissions.hasCoarse,
        )
    }

    @Suppress("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (cont.isActive) cont.resume(location)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    @Suppress("MissingPermission")
    suspend fun getCurrentLocation(): Location {
        val priority = locationPriority()
        if (priority == null) {
            throw SecurityException("Location permission not granted")
        }
        return suspendCancellableCoroutine { cont ->
            val cancellationTokenSource = CancellationTokenSource()
            cont.invokeOnCancellation { cancellationTokenSource.cancel() }
            fusedClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (!cont.isActive) return@addOnSuccessListener
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        cont.resumeWithException(Exception("Unable to get current location"))
                    }
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }
    }

    @Suppress("MissingPermission")
    fun observeLocation(intervalMs: Long = 10_000L): Flow<Location> = callbackFlow {
        val priority = locationPriority()
        if (priority == null) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private fun locationPriority(): Int? {
        val permissions = locationPermissions()
        return when (
            LocationPermissionPolicy.accuracy(
                hasFinePermission = permissions.hasFine,
                hasCoarsePermission = permissions.hasCoarse,
            )
        ) {
            LocationAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            null -> null
        }
    }

    private fun locationPermissions() = LocationPermissions(
        hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED,
        hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED,
    )

    private data class LocationPermissions(
        val hasFine: Boolean,
        val hasCoarse: Boolean,
    )
}
