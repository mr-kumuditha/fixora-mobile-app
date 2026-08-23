package com.techfix.app.domain.repair

import com.techfix.app.domain.catalog.DeviceCategory

/**
 * Details of the device being repaired, entered in the booking flow.
 * Stored as a nested map on the Firestore `repairRequests` document.
 */
data class DeviceDetails(
    val category: DeviceCategory,
    val brand: String,
    val model: String,
    val serialNumber: String? = null,
)

/**
 * A booked repair, from the Firestore `repairRequests` collection.
 *
 * `imageUrls` point at Supabase Storage, not Firebase Storage (decision of
 * 2026-08-21). `technicianId` references a Firestore `technicians` document
 * id and is null until a Branch Manager assigns one.
 */
data class RepairRequest(
    val id: String,
    val customerId: String,
    val serviceId: String,
    val deviceDetails: DeviceDetails,
    val issueDescription: String,
    val imageUrls: List<String> = emptyList(),
    val branchId: String,
    val technicianId: String? = null,
    val status: RepairStatus = RepairStatus.SUBMITTED,
    val createdAt: Long? = null,
    val scheduledAt: Long? = null,
    /**
     * Set when the repair reaches COMPLETED, which only happens once a
     * (simulated) payment succeeds — see Block 7. Null for everything still
     * in flight, so History Detail can tell "finished on this date" from
     * "no completion recorded".
     */
    val completedAt: Long? = null,
)
