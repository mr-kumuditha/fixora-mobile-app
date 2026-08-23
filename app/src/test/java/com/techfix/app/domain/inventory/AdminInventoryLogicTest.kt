package com.techfix.app.domain.inventory

import com.techfix.app.domain.catalog.DeviceCategory
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminInventoryLogicTest {
    private fun item(
        id: String = "part-1",
        name: String = "Display panel",
        category: String = "SCREEN",
        minimum: Int = 3,
        colombo: Int = 5,
        galle: Int = 5,
        cost: BigDecimal? = null,
        available: Boolean = true,
        updatedAt: String = "2026-08-23T12:00:00Z",
    ) = AdminInventoryItem(
        id, name, category, null, "SKU-1", listOf(DeviceCategory.MOBILE), minimum,
        cost, null, null, null, available, "2026-08-20T12:00:00Z", updatedAt, null,
        listOf(
            InventoryBranchStock("colombo", colombo, null),
            InventoryBranchStock("galle", galle, null),
        ),
    )

    @Test
    fun `stock status is calculated from quantity and configured threshold`() {
        assertEquals(InventoryStockStatus.IN_STOCK, item(colombo = 5).statusFor("colombo"))
        assertEquals(InventoryStockStatus.LOW_STOCK, item(colombo = 3).statusFor("colombo"))
        assertEquals(InventoryStockStatus.OUT_OF_STOCK, item(colombo = 0).statusFor("colombo"))
        assertEquals(InventoryStockStatus.UNAVAILABLE, item(available = false).statusFor("colombo"))
    }

    @Test
    fun `all branch status flags a branch gap without calling the item globally out`() {
        assertEquals(InventoryStockStatus.LOW_STOCK, item(colombo = 8, galle = 0).statusFor(null))
        assertEquals(InventoryStockStatus.OUT_OF_STOCK, item(colombo = 0, galle = 0).statusFor(null))
    }

    @Test
    fun `inventory value is hidden until every active item has a reliable unit cost`() {
        assertNull(InventoryDashboardMetrics.from(listOf(item(cost = BigDecimal("10.00")), item(id = "part-2"))).inventoryValue)
        val metrics = InventoryDashboardMetrics.from(
            listOf(item(cost = BigDecimal("10.00"), colombo = 2, galle = 3), item(id = "part-2", cost = BigDecimal("4.50"), colombo = 1, galle = 1)),
        )
        assertEquals(0, BigDecimal("59.00").compareTo(metrics.inventoryValue))
    }

    @Test
    fun `search category stock filter and quantity sort compose predictably`() {
        val display = item(name = "Mobile Display", category = "SCREEN", colombo = 1)
        val battery = item(id = "part-2", name = "Battery Pack", category = "BATTERY", colombo = 8)
        val result = filterAndSortInventory(
            listOf(battery, display), "mobile", "SCREEN", InventoryStockFilter.LOW_STOCK,
            InventorySort.QUANTITY_LOW, "colombo",
        )
        assertEquals(listOf(display), result)
    }

    @Test
    fun `item validation rejects missing required data and negative prices`() {
        val invalid = validateInventoryItem(
            InventoryItemDraft(" ", "", "", "", emptySet(), -1, BigDecimal("-1"), null, "", ""),
        )
        assertFalse(invalid.isValid)
        assertEquals("Item name is required", invalid.nameError)
        assertEquals("Minimum stock cannot be negative", invalid.minimumStockError)
        assertEquals("Unit cost cannot be negative", invalid.unitCostError)
    }

    @Test
    fun `valid item keeps optional commercial fields optional`() {
        val valid = validateInventoryItem(
            InventoryItemDraft("Display", "Screen", "", "", setOf(DeviceCategory.MOBILE), 2, null, null, "", ""),
        )
        assertTrue(valid.isValid)
    }

    @Test
    fun `add remove and correction report the resulting quantity`() {
        fun validate(type: StockAdjustmentType, quantity: Int) = validateStockAdjustment(
            10, StockAdjustmentDraft("part", "colombo", type, quantity, "Stock count correction"),
        )
        assertEquals(14, validate(StockAdjustmentType.ADD, 4).resultingQuantity)
        assertEquals(6, validate(StockAdjustmentType.REMOVE, 4).resultingQuantity)
        assertEquals(4, validate(StockAdjustmentType.CORRECT, 4).resultingQuantity)
    }

    @Test
    fun `negative results invalid values and missing reasons are rejected`() {
        val excessiveRemoval = validateStockAdjustment(
            3, StockAdjustmentDraft("part", "colombo", StockAdjustmentType.REMOVE, 4, "Damaged"),
        )
        assertFalse(excessiveRemoval.isValid)
        assertNull(excessiveRemoval.resultingQuantity)
        val missingReason = validateStockAdjustment(
            3, StockAdjustmentDraft("part", "colombo", StockAdjustmentType.ADD, 1, ""),
        )
        assertFalse(missingReason.isValid)
    }

    @Test
    fun `stock adjustment rejects a resulting quantity beyond the database limit`() {
        val validation = validateStockAdjustment(
            MAX_QUANTITY,
            StockAdjustmentDraft("part", "colombo", StockAdjustmentType.ADD, 1, "Supplier delivery"),
        )
        assertFalse(validation.isValid)
        assertEquals("Resulting quantity is too large", validation.quantityError)
    }

    @Test
    fun `submission gate rejects a duplicate until the first request completes`() {
        val gate = InventorySubmissionGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        assertTrue(gate.isBusy)
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
