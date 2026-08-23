package com.techfix.app.domain.matching

import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.sparepart.SparePart
import com.techfix.app.domain.sparepart.SparePartAvailability
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.sparepart.SparePartStock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchBranchesUseCaseTest {
    private val colombo = Branch("colombo", "Fixora Colombo", 6.9271, 79.8612, "Colombo")
    private val galle = Branch("galle", "Fixora Galle", 6.0535, 80.2210, "Galle")

    private fun useCase() = MatchBranchesUseCase(
        branchRepository = FakeBranchRepository(listOf(colombo, galle)),
        sparePartRepository = FakeSparePartRepository(seedParts, seedStock),
    )

    @Test
    fun `parts availability can outweigh distance`() = runBlocking {
        val result = useCase()(DeviceCategory.DESKTOP, Coordinates(6.0540, 80.2200)).getOrThrow()
        assertEquals("colombo", result.recommended?.branch?.id)
        assertTrue(result.matches.first { it.branch.id == "colombo" }.canHandleNow)
        assertFalse(result.matches.first { it.branch.id == "galle" }.canHandleNow)
    }

    @Test
    fun `distance ranks branches with comparable stock`() = runBlocking {
        val result = useCase()(DeviceCategory.MOBILE, Coordinates(6.0540, 80.2200)).getOrThrow()
        assertEquals("galle", result.recommended?.branch?.id)
    }

    @Test
    fun `unknown location uses a neutral distance score`() = runBlocking {
        val result = useCase()(DeviceCategory.DESKTOP, null).getOrThrow()
        assertFalse(result.locationKnown)
        assertTrue(result.matches.all { it.distanceKm == null })
        assertTrue(result.matches.all { it.distanceScore == MatchBranchesUseCase.NEUTRAL_DISTANCE_SCORE })
        assertEquals("colombo", result.recommended?.branch?.id)
    }

    @Test
    fun `out of stock parts remain visible in the explanation`() = runBlocking {
        val result = useCase()(DeviceCategory.LAPTOP, Coordinates(6.9280, 79.8600)).getOrThrow()
        val match = result.matches.first { it.branch.id == "colombo" }
        assertEquals(2, match.totalPartsTracked)
        assertEquals(listOf("Laptop Keyboard"), match.partsOutOfStock.map { it.part.name })
    }

    private class FakeBranchRepository(private val branches: List<Branch>) : BranchRepository {
        override suspend fun getBranches() = Result.success(branches)
        override suspend fun getBranch(branchId: String) = branches.firstOrNull { it.id == branchId }
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("No branch $branchId"))
    }

    private class FakeSparePartRepository(
        private val parts: List<SparePart>,
        private val stock: List<SparePartStock>,
    ) : SparePartRepository {
        override suspend fun getSpareParts() = Result.success(parts)
        override suspend fun getStockForBranch(branchId: String) =
            Result.success(stock.filter { it.branchId == branchId })
        override suspend fun getAvailabilityForCategory(branchId: String, category: DeviceCategory) =
            Result.success(parts.filter { category in it.compatibleCategories }.map { part ->
                SparePartAvailability(
                    part,
                    branchId,
                    stock.firstOrNull { it.partId == part.id && it.branchId == branchId }?.quantity ?: 0,
                )
            })
    }

    private companion object {
        val seedParts = listOf(
            SparePart("mobile-screen", "Mobile Screen", "SCREEN", listOf(DeviceCategory.MOBILE)),
            SparePart("mobile-port", "Charging Port", "PORT", listOf(DeviceCategory.MOBILE)),
            SparePart("laptop-screen", "Laptop Screen", "SCREEN", listOf(DeviceCategory.LAPTOP)),
            SparePart("laptop-keyboard", "Laptop Keyboard", "KEYBOARD", listOf(DeviceCategory.LAPTOP)),
            SparePart("desktop-psu", "Desktop Power Supply", "POWER", listOf(DeviceCategory.DESKTOP)),
        )
        val seedStock = listOf(
            SparePartStock("mobile-screen", "colombo", 8),
            SparePartStock("mobile-screen", "galle", 3),
            SparePartStock("mobile-port", "colombo", 5),
            SparePartStock("mobile-port", "galle", 6),
            SparePartStock("laptop-screen", "colombo", 4),
            SparePartStock("laptop-screen", "galle", 0),
            SparePartStock("laptop-keyboard", "colombo", 0),
            SparePartStock("laptop-keyboard", "galle", 7),
            SparePartStock("desktop-psu", "colombo", 6),
            SparePartStock("desktop-psu", "galle", 0),
        )
    }
}
