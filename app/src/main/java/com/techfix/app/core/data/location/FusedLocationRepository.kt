package com.techfix.app.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.location.LocationRepository
import com.techfix.app.domain.location.LocationResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Device location via Play Services' fused provider.
 *
 * Asks for a fresh fix first, then falls back to the last known one: a cold
 * fix indoors can take longer than a customer will wait on a booking screen,
 * and a slightly stale position is still good enough to rank two branches
 * ~120 km apart. Everything that can fail is mapped to a [LocationResult]
 * case rather than thrown — the branch picker degrades to availability-only
 * matching instead of showing an error.
 */
class FusedLocationRepository(
    private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasLocationPermission(): Boolean =
        PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() above
    override suspend fun currentLocation(): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied

        val cancellationTokenSource = CancellationTokenSource()
        val location = try {
            withTimeout(FRESH_FIX_TIMEOUT_MS) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token,
                ).await()
            }
        } catch (timeout: TimeoutCancellationException) {
            cancellationTokenSource.cancel()
            runCatching { client.lastLocation.await() }.getOrNull()
        } catch (error: Exception) {
            // SecurityException if permission was revoked mid-call, or any
            // Play Services failure — both mean "no position", not a crash.
            runCatching { client.lastLocation.await() }.getOrNull()
        }

        return location
            ?.let { LocationResult.Available(Coordinates(it.latitude, it.longitude)) }
            ?: LocationResult.Unavailable
    }

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        private const val FRESH_FIX_TIMEOUT_MS = 8_000L
    }
}
