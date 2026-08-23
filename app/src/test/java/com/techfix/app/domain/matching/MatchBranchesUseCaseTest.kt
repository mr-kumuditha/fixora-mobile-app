package com.techfix.app.domain.matching

import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.sparepart.SparePart
import com.techfix.app.domain.sparepart.SparePartAvailability
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.sparepart.SparePartStock
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.technician.TechnicianRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM tests for the branch-matching rule. The fakes below mirror the
 * real Block 3 seed data in `docs/supabase/schema.sql` and
 * `FirestoreSeedData` row for row, including the deliberate gaps — Galle has
 * no DESKTOP technician, one technician per branch is unavailable, and part
 * stock is uneven — so these assert the behaviour the app will actually show
 * against the live backends, not a convenient invented dataset.
 *
 * No Android or network dependency: the use case takes repository interfaces
 * only, which is the point of keeping it in the domain layer.
 */
class MatchBranchesUseCaseTest {

    private val colombo = Branch(
        id = "colombo",
        name = "Fixora Colombo",
        latitude = 6.9271,
        longitude = 79.8612,
        address = "No. 42, Union Place, Colombo 02",
    )
    private val galle = Branch(
        id = "galle",
        name = "Fixora Galle",
        latitude = 6.0535,
        longitude = 80.2210,
        address = "No. 18, Wackwella Road, Galle",
    )

    private val nearGalle = Coordinates(6.0540, 80.2200)
    private val nearColombo = Coordinates(6.9280, 79.8600)

    private fun useCase(
        technicians: List<Technician> = SEED_TECHNICIANS,
    ) = MatchBranchesUseCase(
        branchRepository = FakeBranchRepository(listOf(colombo, galle)),
        technicianRepository = FakeTechnicianRepository(technicians),
        sparePartRepository = FakeSparePartRepository(SEED_PARTS, SEED_STOCK),
    )

    @Test
    fun `desktop repair beside the Galle branch is still sent to Colombo`() = runBlocking {
        // Galle has no DESKTOP technician at all, so being 0 km away must not
        // win. This is the case the whole "not distance alone" rule exists for.
        val result = useCase()(DeviceCategory.DESKTOP, nearGalle).getOrThrow()

        assertEquals("colombo", result.recommended?.branch?.id)
        val galleMatch = result.matches.first { it.branch.id == "galle" }
        assertFalse(galleMatch.hasTechnician)
        assertTrue(galleMatch.distanceKm!! < 1.0)
        assertFalse(galleMatch.canHandleNow)
        assertTrue(result.matches.first { it.branch.id == "colombo" }.canHandleNow)
    }

    @Test
    fun `mobile repair beside the Galle branch stays at Galle because both can handle it`() =
        runBlocking {
            // Both branches have a free MOBILE technician and stock, so the
            // tie-break falls to distance — proving availability is not being
            // applied as a blunt override of location.
            val result = useCase()(DeviceCategory.MOBILE, nearGalle).getOrThrow()

            assertEquals("galle", result.recommended?.branch?.id)
            assertTrue(result.matches.all { it.canHandleNow })
        }

    @Test
    fun `the same mobile repair from Colombo flips the recommendation`() = runBlocking {
        val result = useCase()(DeviceCategory.MOBILE, nearColombo).getOrThrow()

        assertEquals("colombo", result.recommended?.branch?.id)
    }

    @Test
    fun `unavailable technicians do not count towards a branch`() = runBlocking {
        // Ishara Silva at Colombo holds LAPTOP but is marked unavailable, and
        // Sanduni Rathnayake at Galle holds MOBILE but is unavailable.
        val result = useCase()(DeviceCategory.LAPTOP, nearColombo).getOrThrow()

        val colomboMatch = result.matches.first { it.branch.id == "colombo" }
        assertEquals(listOf("Dilshan Fernando"), colomboMatch.availableTechnicians.map { it.name })
    }

    @Test
    fun `out of stock parts are reported separately from parts not carried`() = runBlocking {
        val result = useCase()(DeviceCategory.LAPTOP, nearColombo).getOrThrow()

        val colomboMatch = result.matches.first { it.branch.id == "colombo" }
        // Four parts fit LAPTOP; the keyboard module sits at 0 in Colombo.
        assertEquals(4, colomboMatch.totalPartsTracked)
        assertEquals(
            listOf("Laptop Keyboard Module"),
            colomboMatch.partsOutOfStock.map { it.part.name },
        )
        assertTrue(colomboMatch.hasParts)
    }

    @Test
    fun `with no location the ranking falls back to availability alone`() = runBlocking {
        val result = useCase()(DeviceCategory.DESKTOP, customerLocation = null).getOrThrow()

        assertFalse(result.locationKnown)
        assertTrue(result.matches.all { it.distanceKm == null })
        assertTrue(
            "distance must be neutral, not zero, when location is unknown",
            result.matches.all { it.distanceScore == MatchBranchesUseCase.NEUTRAL_DISTANCE_SCORE },
        )
        assertEquals("colombo", result.recommended?.branch?.id)
    }

    @Test
    fun `when no branch can start the job it still offers the closest match`() = runBlocking {
        // Every technician off shift. Per the architecture doc, this is a wait,
        // not a dead end — the customer must still be given a branch.
        val allBusy = SEED_TECHNICIANS.map { it.copy(available = false) }
        val result = useCase(technicians = allBusy)(DeviceCategory.MOBILE, nearGalle).getOrThrow()

        assertTrue(result.allBranchesBlocked)
        assertNotNull(result.recommended)
        assertEquals("galle", result.recommended?.branch?.id)
    }

    @Test
    fun `distance between the two seeded branches is realistic`() = runBlocking {
        val result = useCase()(DeviceCategory.MOBILE, nearColombo).getOrThrow()

        val toGalle = result.matches.first { it.branch.id == "galle" }.distanceKm!!
        // Colombo to Galle is roughly 100 km as the crow flies.
        assertTrue("expected ~100 km, got $toGalle", toGalle in 90.0..115.0)
        assertNull(result.matches.firstOrNull { it.branch.id == "nowhere" })
    }

    // --------------------------------------------------------------- fakes

    private class FakeBranchRepository(private val branches: List<Branch>) : BranchRepository {
        override suspend fun getBranches() = Result.success(branches)
        override suspend fun getBranch(branchId: String) =
            branches.firstOrNull { it.id == branchId }
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("no branch $branchId"))
    }

    private class FakeTechnicianRepository(
        private val technicians: List<Technician>,
    ) : TechnicianRepository {
        override suspend fun getTechnicians() = Result.success(technicians)

        override suspend fun getTechniciansForBranch(branchId: String) =
            Result.success(technicians.filter { it.branchId == branchId })

        override suspend fun getAvailableTechnicians(branchId: String, category: DeviceCategory) =
            Result.success(
                technicians.filter {
                    it.branchId == branchId && it.available && category in it.categorySkills
                },
            )
    }

    private class FakeSparePartRepository(
        private val parts: List<SparePart>,
        private val stock: List<SparePartStock>,
    ) : SparePartRepository {
        override suspend fun getSpareParts() = Result.success(parts)

        override suspend fun getStockForBranch(branchId: String) =
            Result.success(stock.filter { it.branchId == branchId })

        override suspend fun getAvailabilityForCategory(
            branchId: String,
            category: DeviceCategory,
        ) = Result.success(
            parts.filter { category in it.compatibleCategories }.map { part ->
                SparePartAvailability(
                    part = part,
                    branchId = branchId,
                    quantity = stock.firstOrNull {
                        it.partId == part.id && it.branchId == branchId
                    }?.quantity ?: 0,
                )
            },
        )

        /** Matching never writes stock — this fake exists to satisfy the interface. */
        override suspend fun updateStock(partId: String, branchId: String, quantity: Int) =
            Result.failure<Unit>(UnsupportedOperationException("not used by matching"))
    }

    private companion object {
        val SEED_TECHNICIANS = listOf(
            tech("Nuwan Perera", "colombo", listOf(DeviceCategory.MOBILE, DeviceCategory.TABLET), true),
            tech("Dilshan Fernando", "colombo", listOf(DeviceCategory.LAPTOP, DeviceCategory.DESKTOP), true),
            tech("Ishara Silva", "colombo", listOf(DeviceCategory.MOBILE, DeviceCategory.LAPTOP), false),
            tech("Kasun Jayawardena", "galle", listOf(DeviceCategory.MOBILE, DeviceCategory.TABLET), true),
            tech("Tharindu Bandara", "galle", listOf(DeviceCategory.LAPTOP), true),
            tech("Sanduni Rathnayake", "galle", listOf(DeviceCategory.MOBILE), false),
        )

        val SEED_PARTS = listOf(
            part("Mobile Display Panel", "SCREEN", DeviceCategory.MOBILE),
            part("Mobile Battery Pack", "BATTERY", DeviceCategory.MOBILE),
            part("USB-C Charging Port Flex", "PORT", DeviceCategory.MOBILE, DeviceCategory.TABLET),
            part("Laptop LCD Panel 15.6\"", "SCREEN", DeviceCategory.LAPTOP),
            part("Laptop Keyboard Module", "KEYBOARD", DeviceCategory.LAPTOP),
            part("512GB NVMe SSD", "STORAGE", DeviceCategory.LAPTOP, DeviceCategory.DESKTOP),
            part("ATX Power Supply 600W", "POWER", DeviceCategory.DESKTOP),
            part("Tablet Display Assembly", "SCREEN", DeviceCategory.TABLET),
            part("Thermal Paste Kit", "COOLING", DeviceCategory.LAPTOP, DeviceCategory.DESKTOP),
        )

        val SEED_STOCK = listOf(
            stock("Mobile Display Panel", "colombo", 12),
            stock("Mobile Display Panel", "galle", 3),
            stock("Mobile Battery Pack", "colombo", 8),
            stock("Mobile Battery Pack", "galle", 0),
            stock("USB-C Charging Port Flex", "colombo", 5),
            stock("USB-C Charging Port Flex", "galle", 6),
            stock("Laptop LCD Panel 15.6\"", "colombo", 4),
            stock("Laptop LCD Panel 15.6\"", "galle", 0),
            stock("Laptop Keyboard Module", "colombo", 0),
            stock("Laptop Keyboard Module", "galle", 7),
            stock("512GB NVMe SSD", "colombo", 10),
            stock("512GB NVMe SSD", "galle", 2),
            stock("ATX Power Supply 600W", "colombo", 6),
            stock("ATX Power Supply 600W", "galle", 0),
            stock("Tablet Display Assembly", "colombo", 2),
            stock("Tablet Display Assembly", "galle", 5),
            stock("Thermal Paste Kit", "colombo", 20),
            stock("Thermal Paste Kit", "galle", 15),
        )

        // Ids are the part names here — the real table uses uuids, but the use
        // case only ever joins on equality, so the shape is what matters.
        private fun tech(
            name: String,
            branchId: String,
            skills: List<DeviceCategory>,
            available: Boolean,
        ) = Technician(
            id = name,
            name = name,
            branchId = branchId,
            categorySkills = skills,
            available = available,
        )

        private fun part(name: String, category: String, vararg fits: DeviceCategory) =
            SparePart(
                id = name,
                name = name,
                category = category,
                compatibleCategories = fits.toList(),
            )

        private fun stock(partName: String, branchId: String, quantity: Int) =
            SparePartStock(partId = partName, branchId = branchId, quantity = quantity)
    }
}
