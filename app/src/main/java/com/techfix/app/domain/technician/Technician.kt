package com.techfix.app.domain.technician

import com.techfix.app.domain.catalog.DeviceCategory

/**
 * A technician from the Firestore `technicians` collection. Migrated records
 * retain their original stable UUID document ids.
 */
data class Technician(
    val id: String,
    val name: String,
    val branchId: String,
    val categorySkills: List<DeviceCategory>,
    val available: Boolean,
    /** False keeps historical repair references intact while removing this record from active work. */
    val active: Boolean = true,
    /** Reverse link to `users/{uid}`. Required before this technician can receive assignments. */
    val linkedUserId: String? = null,
    /** Server timestamp in epoch millis when the roster record was retired. */
    val archivedAt: Long? = null,
)
