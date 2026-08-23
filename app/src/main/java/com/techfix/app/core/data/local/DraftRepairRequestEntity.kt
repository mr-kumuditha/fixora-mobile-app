package com.techfix.app.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The one in-progress Book Repair draft the architecture doc allows. The
 * primary key is a constant, so there is exactly one row: starting a booking
 * for a different service replaces the previous draft rather than keeping a
 * list of them. That is the locked scope — a cache of one draft, no sync
 * queue and no draft history.
 *
 * `imagesJson` holds only photos that finished uploading to Supabase, so a
 * restored draft never points at a local `content://` URI that the process
 * no longer has read permission for. See [com.techfix.app.domain.draft.DraftImage].
 */
@Entity(tableName = "draft_repair_request")
data class DraftRepairRequestEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val customerId: String,
    val serviceId: String,
    val step: Int,
    val category: String?,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val issueDescription: String,
    val imagesJson: String,
    val unsavedImageCount: Int,
    val selectedBranchId: String?,
    val scheduledAt: Long?,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

@Dao
interface DraftRepairRequestDao {

    @Query("SELECT * FROM draft_repair_request WHERE id = :id LIMIT 1")
    suspend fun get(id: Int = DraftRepairRequestEntity.SINGLE_ROW_ID): DraftRepairRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftRepairRequestEntity)

    @Query("DELETE FROM draft_repair_request")
    suspend fun clear()
}
