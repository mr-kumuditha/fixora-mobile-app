package com.techfix.app.domain.sparepart

import com.techfix.app.domain.catalog.DeviceCategory

/**
 * A spare part from the Supabase `spare_parts` table. `category` is the part
 * type (SCREEN, BATTERY, …); `compatibleCategories` is which device
 * categories it fits.
 */
data class SparePart(
    val id: String,
    val name: String,
    val category: String,
    val compatibleCategories: List<DeviceCategory>,
)

/** Quantity of one part held at one branch (`spare_part_stock`). */
data class SparePartStock(
    val partId: String,
    val branchId: String,
    val quantity: Int,
)

/** A part joined to its quantity at a single branch. */
data class SparePartAvailability(
    val part: SparePart,
    val branchId: String,
    val quantity: Int,
) {
    val inStock: Boolean get() = quantity > 0
}
