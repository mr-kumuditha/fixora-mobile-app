package com.techfix.app.domain.catalog

/**
 * One bookable repair service from the Firestore `services` collection.
 * Named RepairService rather than Service to avoid colliding with
 * android.app.Service at call sites.
 */
data class RepairService(
    val id: String,
    val category: DeviceCategory,
    val name: String,
    val description: String,
    val basePrice: Double,
)
