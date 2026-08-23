package com.techfix.app.core.data.sparepart

import com.techfix.app.core.data.SupabaseClientProvider
import com.techfix.app.core.data.SupabaseTables
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.sparepart.SparePart
import com.techfix.app.domain.sparepart.SparePartAvailability
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.sparepart.SparePartStock
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spare parts and per-branch stock, from Supabase Postgres. The stock
 * numbers are deliberately uneven between Colombo and Galle so the branch
 * matching in Block 5 has a real reason to prefer one over the other.
 */
class SupabaseSparePartRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) : SparePartRepository {

    @Serializable
    private data class SparePartRow(
        val id: String,
        val name: String,
        val category: String,
        @SerialName("compatible_categories") val compatibleCategories: List<String>,
    ) {
        fun toSparePart() = SparePart(
            id = id,
            name = name,
            category = category,
            compatibleCategories = compatibleCategories.mapNotNull(DeviceCategory::fromRaw),
        )
    }

    /**
     * Write shape for [updateStock]. Separate from [StockRow] because the
     * upsert must not send `id` (the row may not exist yet, and Postgres
     * generates it) and must send `updated_at` (nothing else refreshes it).
     */
    @Serializable
    private data class StockUpsert(
        @SerialName("part_id") val partId: String,
        @SerialName("branch_id") val branchId: String,
        val quantity: Int,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class StockRow(
        @SerialName("part_id") val partId: String,
        @SerialName("branch_id") val branchId: String,
        val quantity: Int,
    ) {
        fun toStock() = SparePartStock(partId = partId, branchId = branchId, quantity = quantity)
    }

    override suspend fun getSpareParts(): Result<List<SparePart>> = runCatching {
        client.from(SupabaseTables.SPARE_PARTS)
            .select()
            .decodeList<SparePartRow>()
            .map { it.toSparePart() }
    }

    override suspend fun getStockForBranch(branchId: String): Result<List<SparePartStock>> =
        runCatching {
            client.from(SupabaseTables.SPARE_PART_STOCK)
                .select { filter { eq("branch_id", branchId) } }
                .decodeList<StockRow>()
                .map { it.toStock() }
        }

    override suspend fun getAvailabilityForCategory(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<SparePartAvailability>> = runCatching {
        val parts = client.from(SupabaseTables.SPARE_PARTS)
            .select { filter { contains("compatible_categories", listOf(category.name)) } }
            .decodeList<SparePartRow>()
            .map { it.toSparePart() }

        if (parts.isEmpty()) return@runCatching emptyList()

        val quantityByPartId = client.from(SupabaseTables.SPARE_PART_STOCK)
            .select {
                filter {
                    eq("branch_id", branchId)
                    isIn("part_id", parts.map { it.id })
                }
            }
            .decodeList<StockRow>()
            .associate { it.partId to it.quantity }

        // A part with no stock row for this branch is "we carry it, none here",
        // which the matching logic needs to tell apart from "not a part we carry".
        parts.map { part ->
            SparePartAvailability(
                part = part,
                branchId = branchId,
                quantity = quantityByPartId[part.id] ?: 0,
            )
        }
    }

    override suspend fun updateStock(
        partId: String,
        branchId: String,
        quantity: Int,
    ): Result<Unit> = runCatching {
        require(quantity >= 0) { "Stock quantity can't be negative" }
        client.from(SupabaseTables.SPARE_PART_STOCK)
            .upsert(
                StockUpsert(
                    partId = partId,
                    branchId = branchId,
                    quantity = quantity,
                    updatedAt = Instant.now().toString(),
                )
            ) {
                // Matches the spare_part_stock_unique_part_branch constraint,
                // so a branch that has never carried this part gets a new row
                // and one that has gets its quantity corrected.
                onConflict = "part_id,branch_id"
            }
        Unit
    }
}
