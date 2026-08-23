package com.techfix.app.domain.catalog

/**
 * Hides whether the service catalog comes from Firestore or the Room read
 * cache, so ViewModels never talk to a backend directly.
 *
 * The `…WithSource` variants exist for the two screens that show an offline
 * notice. They default to reporting a live read, so an implementation with no
 * cache behind it — [com.techfix.app.core.data.catalog.FirestoreServiceRepository]
 * — needs no extra code, and the callers that don't care about provenance
 * keep using the plain reads.
 */
interface ServiceRepository {
    suspend fun getServices(): Result<List<RepairService>>
    suspend fun getServicesByCategory(category: DeviceCategory): Result<List<RepairService>>
    suspend fun getService(serviceId: String): Result<RepairService>

    suspend fun getServicesWithSource(): Result<CachedRead<List<RepairService>>> =
        getServices().map { CachedRead(it, fromCache = false) }

    suspend fun getServiceWithSource(serviceId: String): Result<CachedRead<RepairService>> =
        getService(serviceId).map { CachedRead(it, fromCache = false) }
}
