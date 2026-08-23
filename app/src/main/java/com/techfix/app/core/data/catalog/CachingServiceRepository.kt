package com.techfix.app.core.data.catalog

import com.techfix.app.core.data.local.CachedServiceEntity
import com.techfix.app.core.data.local.ServiceCacheDao
import com.techfix.app.domain.catalog.CachedRead
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository

/**
 * The offline read cache for the service catalog: network first, Room as the
 * fallback.
 *
 * Every successful Firestore read overwrites the cache, so the fallback is
 * always the last catalog the device actually saw. A failed read is only
 * surfaced as an error when the cache is empty too — otherwise the customer
 * gets the saved catalog with an offline notice, which is the whole point of
 * the block. Deliberately network-first rather than cache-first: prices and
 * descriptions are what the customer is deciding on, so a stale catalog is a
 * fallback, never the default.
 *
 * Cache writes are best-effort. A disk failure must not turn a perfectly good
 * live read into an error.
 */
class CachingServiceRepository(
    private val remote: ServiceRepository,
    private val dao: ServiceCacheDao,
) : ServiceRepository {

    override suspend fun getServices(): Result<List<RepairService>> =
        getServicesWithSource().map { it.value }

    override suspend fun getServicesByCategory(
        category: DeviceCategory,
    ): Result<List<RepairService>> {
        val live = remote.getServicesByCategory(category)
        live.onSuccess { return Result.success(it) }
        val cached = readCache { dao.getByCategory(category.name) }
        return if (cached.isEmpty()) live else Result.success(cached)
    }

    override suspend fun getService(serviceId: String): Result<RepairService> =
        getServiceWithSource(serviceId).map { it.value }

    override suspend fun getServicesWithSource(): Result<CachedRead<List<RepairService>>> {
        val live = remote.getServices()
        live.onSuccess { services ->
            runCatching { dao.replaceAll(services.map { it.toEntity() }) }
            return Result.success(CachedRead(services, fromCache = false))
        }
        val cached = readCache { dao.getAll() }
        return if (cached.isEmpty()) {
            Result.failure(live.exceptionOrNull() ?: IllegalStateException("Catalog unavailable"))
        } else {
            Result.success(CachedRead(cached, fromCache = true))
        }
    }

    override suspend fun getServiceWithSource(
        serviceId: String,
    ): Result<CachedRead<RepairService>> {
        val live = remote.getService(serviceId)
        live.onSuccess { service ->
            runCatching { dao.upsertAll(listOf(service.toEntity())) }
            return Result.success(CachedRead(service, fromCache = false))
        }
        val cached = runCatching { dao.getById(serviceId) }.getOrNull()?.toRepairService()
        return if (cached == null) {
            Result.failure(live.exceptionOrNull() ?: IllegalStateException("Service unavailable"))
        } else {
            Result.success(CachedRead(cached, fromCache = true))
        }
    }

    private suspend fun readCache(
        read: suspend () -> List<CachedServiceEntity>,
    ): List<RepairService> =
        runCatching { read() }.getOrNull().orEmpty().mapNotNull { it.toRepairService() }

    private fun RepairService.toEntity() = CachedServiceEntity(
        id = id,
        category = category.name,
        name = name,
        description = description,
        basePrice = basePrice,
        cachedAt = System.currentTimeMillis(),
    )

    /**
     * A cached row whose category no longer maps onto the enum is dropped
     * rather than guessed at — the same rule the Firestore mapper follows.
     */
    private fun CachedServiceEntity.toRepairService(): RepairService? {
        val parsed = DeviceCategory.fromRaw(category) ?: return null
        return RepairService(
            id = id,
            category = parsed,
            name = name,
            description = description,
            basePrice = basePrice,
        )
    }
}
