package com.techfix.app.core.data.catalog

import com.techfix.app.core.data.local.CachedServiceEntity
import com.techfix.app.core.data.local.ServiceCacheDao
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The offline catalog rule, tested without a device: network first, the last
 * successful fetch second, and an error only when there is nothing saved.
 *
 * Note what these do *not* prove — that the Room implementation behind the
 * DAO reads and writes correctly on a real device with the radio off. That
 * needs an airplane-mode pass. What they do pin down is the decision logic
 * that sits above the DAO, which is where a fallback usually goes wrong.
 */
class CachingServiceRepositoryTest {

    private val screen = RepairService(
        id = "mobile-screen-replacement",
        category = DeviceCategory.MOBILE,
        name = "Screen replacement",
        description = "Cracked or unresponsive display.",
        basePrice = 12000.0,
    )
    private val battery = RepairService(
        id = "laptop-battery",
        category = DeviceCategory.LAPTOP,
        name = "Battery replacement",
        description = "Swollen or dead battery.",
        basePrice = 9000.0,
    )

    /** An in-memory stand-in for Room, so these run on the JVM. */
    private class FakeDao : ServiceCacheDao {
        val rows = mutableMapOf<String, CachedServiceEntity>()

        override suspend fun getAll() = rows.values.sortedWith(compareBy({ it.category }, { it.name }))
        override suspend fun getByCategory(category: String) =
            rows.values.filter { it.category == category }.sortedBy { it.name }

        override suspend fun getById(serviceId: String) = rows[serviceId]
        override suspend fun upsertAll(services: List<CachedServiceEntity>) {
            services.forEach { rows[it.id] = it }
        }

        override suspend fun deleteAll() = rows.clear()
        override suspend fun replaceAll(services: List<CachedServiceEntity>) {
            deleteAll()
            upsertAll(services)
        }
    }

    private class FakeRemote(
        var services: List<RepairService> = emptyList(),
        var failure: Throwable? = null,
    ) : ServiceRepository {
        override suspend fun getServices(): Result<List<RepairService>> =
            failure?.let { Result.failure(it) } ?: Result.success(services)

        override suspend fun getServicesByCategory(category: DeviceCategory) =
            failure?.let { Result.failure<List<RepairService>>(it) }
                ?: Result.success(services.filter { it.category == category })

        override suspend fun getService(serviceId: String): Result<RepairService> =
            failure?.let { Result.failure(it) }
                ?: services.firstOrNull { it.id == serviceId }
                    ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("not found"))
    }

    @Test
    fun `a successful fetch is served live and written to the cache`() = runBlocking {
        val dao = FakeDao()
        val repository = CachingServiceRepository(FakeRemote(listOf(screen, battery)), dao)

        val read = repository.getServicesWithSource().getOrThrow()

        assertFalse("a live read must not be labelled offline", read.fromCache)
        assertEquals(listOf(screen, battery).map { it.id }.sorted(), read.value.map { it.id }.sorted())
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `the catalog still loads from the cache when the network read fails`() = runBlocking {
        val dao = FakeDao()
        val remote = FakeRemote(listOf(screen, battery))
        val repository = CachingServiceRepository(remote, dao)
        repository.getServices().getOrThrow()

        remote.failure = IOException("no connection")
        val read = repository.getServicesWithSource().getOrThrow()

        assertTrue("a cache read must be labelled offline", read.fromCache)
        assertEquals(listOf("laptop-battery", "mobile-screen-replacement"), read.value.map { it.id }.sorted())
    }

    @Test
    fun `a single service also falls back to the cache`() = runBlocking {
        val dao = FakeDao()
        val remote = FakeRemote(listOf(screen))
        val repository = CachingServiceRepository(remote, dao)
        repository.getService(screen.id).getOrThrow()

        remote.failure = IOException("no connection")
        val read = repository.getServiceWithSource(screen.id).getOrThrow()

        assertTrue(read.fromCache)
        assertEquals(screen, read.value)
    }

    @Test
    fun `opening a service detail warms the cache for that one service`() = runBlocking {
        val dao = FakeDao()
        val repository = CachingServiceRepository(FakeRemote(listOf(screen)), dao)

        repository.getService(screen.id).getOrThrow()

        assertEquals(setOf(screen.id), dao.rows.keys)
    }

    @Test
    fun `a failed read with an empty cache is an error, not an empty catalog`() = runBlocking {
        val repository = CachingServiceRepository(
            FakeRemote(failure = IOException("no connection")),
            FakeDao(),
        )

        val result = repository.getServicesWithSource()

        assertTrue(result.isFailure)
        assertEquals("no connection", result.exceptionOrNull()?.message)
    }

    @Test
    fun `a successful fetch removes services that no longer exist upstream`() = runBlocking {
        val dao = FakeDao()
        val remote = FakeRemote(listOf(screen, battery))
        val repository = CachingServiceRepository(remote, dao)
        repository.getServices().getOrThrow()

        remote.services = listOf(screen)
        repository.getServices().getOrThrow()

        assertEquals(setOf(screen.id), dao.rows.keys)
        remote.failure = IOException("no connection")
        assertEquals(listOf(screen.id), repository.getServices().getOrThrow().map { it.id })
    }

    @Test
    fun `category filtering falls back to the cache too`() = runBlocking {
        val dao = FakeDao()
        val remote = FakeRemote(listOf(screen, battery))
        val repository = CachingServiceRepository(remote, dao)
        repository.getServices().getOrThrow()

        remote.failure = IOException("no connection")
        val laptops = repository.getServicesByCategory(DeviceCategory.LAPTOP).getOrThrow()

        assertEquals(listOf(battery.id), laptops.map { it.id })
    }

    @Test
    fun `a cached row with an unknown category is dropped rather than guessed at`() = runBlocking {
        val dao = FakeDao()
        dao.rows["ghost"] = CachedServiceEntity(
            id = "ghost",
            category = "SMARTWATCH",
            name = "Strap replacement",
            description = "",
            basePrice = 1000.0,
            cachedAt = 0L,
        )
        val repository = CachingServiceRepository(
            FakeRemote(failure = IOException("no connection")),
            dao,
        )

        val result = repository.getServicesWithSource()

        // Only unreadable rows were cached, so this is the empty-cache case.
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
