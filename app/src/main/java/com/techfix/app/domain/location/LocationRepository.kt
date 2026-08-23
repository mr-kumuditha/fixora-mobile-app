package com.techfix.app.domain.location

/**
 * Outcome of asking for the device's current position. Deliberately three
 * distinct cases rather than a nullable Coordinates: the branch picker shows
 * a different message for "you said no" than for "GPS couldn't get a fix",
 * and neither is an error — matching still runs on availability alone.
 */
sealed interface LocationResult {
    data class Available(val coordinates: Coordinates) : LocationResult

    /** ACCESS_FINE/COARSE_LOCATION not granted. */
    data object PermissionDenied : LocationResult

    /** Permission granted, but no fix (location off, indoors, timed out). */
    data object Unavailable : LocationResult
}

interface LocationRepository {
    /** True when either location permission is currently held. */
    fun hasLocationPermission(): Boolean

    /**
     * Best available current position. Never throws — a failure comes back
     * as [LocationResult.Unavailable] so the caller can degrade instead of
     * showing an error screen.
     */
    suspend fun currentLocation(): LocationResult
}
