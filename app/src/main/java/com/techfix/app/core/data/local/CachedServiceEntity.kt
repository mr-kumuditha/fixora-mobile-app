package com.techfix.app.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * One row per service in the offline catalog cache. This is a read cache of
 * the Firestore `services` collection and nothing else — it is never the
 * source of truth, and nothing in the app writes a service.
 *
 * `cachedAt` is not used for expiry (a stale catalog beats a blank screen
 * with no connection); it is kept so the age of the cache is inspectable.
 */
@Entity(tableName = "cached_services")
data class CachedServiceEntity(
    @PrimaryKey val id: String,
    val category: String,
    val name: String,
    val description: String,
    val basePrice: Double,
    val cachedAt: Long,
)

@Dao
interface ServiceCacheDao {

    @Query("SELECT * FROM cached_services ORDER BY category ASC, name ASC")
    suspend fun getAll(): List<CachedServiceEntity>

    @Query("SELECT * FROM cached_services WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<CachedServiceEntity>

    @Query("SELECT * FROM cached_services WHERE id = :serviceId LIMIT 1")
    suspend fun getById(serviceId: String): CachedServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(services: List<CachedServiceEntity>)

    @Query("DELETE FROM cached_services")
    suspend fun deleteAll()

    /**
     * Mirrors a successful catalog fetch exactly: services removed upstream
     * disappear from the cache too, rather than lingering as bookable
     * entries that no longer exist in Firestore.
     */
    @Transaction
    suspend fun replaceAll(services: List<CachedServiceEntity>) {
        deleteAll()
        upsertAll(services)
    }
}
