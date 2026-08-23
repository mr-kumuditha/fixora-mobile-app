package com.techfix.app.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The whole of the app's local storage, and deliberately no more than that:
 * a read cache of the service catalog and a single in-progress booking draft
 * (CLAUDE.md — "Room caches only the service catalog and one draft repair
 * request. No sync queue."). Repair requests, payments, technicians and
 * spare-part stock are never cached locally; they stay authoritative in
 * Firestore and Supabase.
 *
 * Destructive migration is intentional. Both tables are disposable — the
 * catalog is refetched on the next successful connection and a lost draft
 * costs the customer a re-entry, so carrying migrations for them would be
 * ceremony over a cache.
 */
@Database(
    entities = [CachedServiceEntity::class, DraftRepairRequestEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FixoraDatabase : RoomDatabase() {

    abstract fun serviceCacheDao(): ServiceCacheDao

    abstract fun draftRepairRequestDao(): DraftRepairRequestDao

    companion object {
        private const val NAME = "fixora.db"

        @Volatile
        private var instance: FixoraDatabase? = null

        fun get(context: Context): FixoraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, FixoraDatabase::class.java, NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
