package com.techfix.app.domain.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A latitude/longitude pair, defined in the domain layer rather than reusing
 * the Maps SDK's LatLng so the branch-matching use case stays free of any
 * Android or Google Play dependency. The UI converts to LatLng at the map.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

private const val EARTH_RADIUS_KM = 6371.0088

/**
 * Great-circle distance in kilometres (haversine). Accurate enough at the
 * ~120 km Colombo-to-Galle scale this app works at, and pure Kotlin, so the
 * scoring in [com.techfix.app.domain.matching.MatchBranchesUseCase] is
 * testable off-device.
 */
fun distanceKmBetween(from: Coordinates, to: Coordinates): Double {
    val latDelta = Math.toRadians(to.latitude - from.latitude)
    val lngDelta = Math.toRadians(to.longitude - from.longitude)
    val fromLat = Math.toRadians(from.latitude)
    val toLat = Math.toRadians(to.latitude)

    val a = sin(latDelta / 2) * sin(latDelta / 2) +
        sin(lngDelta / 2) * sin(lngDelta / 2) * cos(fromLat) * cos(toLat)
    // asin form rather than atan2 — clamped because floating-point drift can
    // push `a` a hair above 1 for antipodal-ish inputs and NaN the sqrt.
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
}
