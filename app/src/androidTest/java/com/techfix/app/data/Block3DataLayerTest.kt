package com.techfix.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.core.data.branch.FirestoreBranchRepository
import com.techfix.app.core.data.catalog.FirestoreServiceRepository
import com.techfix.app.core.data.payment.FirestorePaymentRepository
import com.techfix.app.core.data.repair.FirestoreRepairRequestRepository
import com.techfix.app.core.data.sparepart.SupabaseSparePartRepository
import com.techfix.app.core.data.technician.FirestoreTechnicianRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.payment.PaymentMethod
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.repair.DeviceDetails
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Block 3 verification, against the real techfix-mobile-app Firebase project
 * and the real Supabase project — no emulators, no mocks. Every assertion
 * below is a live query, so a passing run means the collection/table exists,
 * is reachable from the device, and holds the seed data.
 *
 * Firestore writes go through the deployed rules, which allow catalog writes
 * to an ADMIN only, so the run signs in as a seed admin account. Its
 * credentials come from local.properties via instrumentation arguments
 * (SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD) and are never compiled into an APK.
 *
 * The Supabase tables are created and seeded by docs/supabase/schema.sql;
 * this test only reads them back.
 */
@RunWith(AndroidJUnit4::class)
class Block3DataLayerTest {

    private val firestore = FirebaseFirestore.getInstance()

    private val serviceRepository = FirestoreServiceRepository(firestore)
    private val branchRepository = FirestoreBranchRepository(firestore)
    private val repairRequestRepository = FirestoreRepairRequestRepository(firestore)
    private val paymentRepository = FirestorePaymentRepository(firestore)
    private val technicianRepository = FirestoreTechnicianRepository(firestore)
    private val sparePartRepository = SupabaseSparePartRepository()

    @Before
    fun signInAndSeedOnce() = runBlocking {
        signInAsSeedAdmin()
        if (!seeded) {
            FirestoreSeedData.seed(firestore)
            seeded = true
        }
    }

    // ------------------------------------------------------------- Firestore

    @Test
    fun branchesCollectionHoldsBothSeededBranches() = runBlocking {
        val branches = branchRepository.getBranches().getOrThrow()

        assertEquals("expected exactly the two branches", 2, branches.size)
        assertEquals(setOf("colombo", "galle"), branches.map { it.id }.toSet())

        val colombo = branchRepository.getBranch("colombo").getOrThrow()
        assertEquals("Fixora Colombo", colombo.name)
        assertEquals(6.9271, colombo.latitude, 0.0001)
        assertEquals(79.8612, colombo.longitude, 0.0001)
        assertTrue(colombo.address.isNotBlank())
    }

    @Test
    fun servicesCollectionCoversAllFourCategories() = runBlocking {
        val services = serviceRepository.getServices().getOrThrow()

        assertEquals(FirestoreSeedData.services.size, services.size)
        assertEquals(
            "every device category needs at least one service",
            DeviceCategory.entries.toSet(),
            services.map { it.category }.toSet(),
        )
        assertTrue("prices must be seeded", services.all { it.basePrice > 0.0 })

        val laptop = serviceRepository.getServicesByCategory(DeviceCategory.LAPTOP).getOrThrow()
        assertTrue("category query returned nothing", laptop.isNotEmpty())
        assertTrue(laptop.all { it.category == DeviceCategory.LAPTOP })

        val single = serviceRepository.getService("mobile-screen-replacement").getOrThrow()
        assertEquals("Phone Screen Replacement", single.name)
    }

    @Test
    fun repairRequestsCollectionRoundTrips() = runBlocking {
        val customerId = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
        val requestId = repairRequestRepository.createRepairRequest(
            RepairRequest(
                id = "",
                customerId = customerId,
                serviceId = "mobile-screen-replacement",
                deviceDetails = DeviceDetails(
                    category = DeviceCategory.MOBILE,
                    brand = "Google",
                    model = "Pixel 3",
                    serialNumber = "BLOCK3-VERIFY",
                ),
                issueDescription = "Block 3 verification write — deleted at the end of this test.",
                imageUrls = listOf("https://example.invalid/block3.jpg"),
                branchId = "colombo",
                status = RepairStatus.SUBMITTED,
            )
        ).getOrThrow()

        try {
            val stored = repairRequestRepository.getRepairRequest(requestId).getOrThrow()
            assertEquals(customerId, stored.customerId)
            assertEquals(DeviceCategory.MOBILE, stored.deviceDetails.category)
            assertEquals(1, stored.imageUrls.size)
            assertEquals(RepairStatus.SUBMITTED, stored.status)
            assertNotNull("createdAt server timestamp was not written", stored.createdAt)

            repairRequestRepository.updateStatus(requestId, RepairStatus.IN_PROGRESS).getOrThrow()
            assertEquals(
                RepairStatus.IN_PROGRESS,
                repairRequestRepository.getRepairRequest(requestId).getOrThrow().status,
            )

            val history = repairRequestRepository
                .getRepairRequestsForCustomer(customerId)
                .getOrThrow()
            assertTrue(
                "customer history query did not return the new request",
                history.any { it.id == requestId },
            )

            val queue = repairRequestRepository.getRepairRequestsForBranch("colombo").getOrThrow()
            assertTrue(
                "branch queue query did not return the new request",
                queue.any { it.id == requestId },
            )

            verifyPaymentsRoundTrip(requestId)
        } finally {
            firestore.collection(FirestoreCollections.REPAIR_REQUESTS)
                .document(requestId)
                .delete()
                .await()
        }
    }

    private suspend fun verifyPaymentsRoundTrip(repairRequestId: String) {
        val paymentId = paymentRepository.createPayment(
            Payment(
                id = "",
                repairRequestId = repairRequestId,
                amount = 12500.0,
                method = PaymentMethod.CARD,
                status = PaymentStatus.SUCCESS,
                receiptId = "FX-BLOCK3-VERIFY",
            )
        ).getOrThrow()

        try {
            val payments = paymentRepository
                .getPaymentsForRepairRequest(repairRequestId)
                .getOrThrow()
            val stored = payments.single { it.id == paymentId }
            assertEquals(12500.0, stored.amount, 0.001)
            assertEquals(PaymentMethod.CARD, stored.method)
            assertEquals(PaymentStatus.SUCCESS, stored.status)
            assertEquals("FX-BLOCK3-VERIFY", stored.receiptId)
            assertNotNull("createdAt server timestamp was not written", stored.createdAt)
        } finally {
            firestore.collection(FirestoreCollections.PAYMENTS).document(paymentId).delete().await()
        }
    }

    // ------------------------------------------------------------ Technicians

    @Test
    fun techniciansTableHoldsSeededStaffPerBranch() = runBlocking {
        val technicians = technicianRepository.getTechnicians().getOrThrow()
        assertEquals(6, technicians.size)

        assertEquals(3, technicianRepository.getTechniciansForBranch("colombo").getOrThrow().size)
        assertEquals(3, technicianRepository.getTechniciansForBranch("galle").getOrThrow().size)

        val colomboLaptop = technicianRepository
            .getAvailableTechnicians("colombo", DeviceCategory.LAPTOP)
            .getOrThrow()
        assertTrue("Colombo should have an available laptop technician", colomboLaptop.isNotEmpty())
        assertTrue(colomboLaptop.all { it.available })
        assertTrue(colomboLaptop.all { DeviceCategory.LAPTOP in it.categorySkills })

        // Deliberate gap in the seed data: Galle has no desktop technician, so
        // Block 5's matching cannot fall back to distance alone.
        assertTrue(
            "Galle was seeded with no DESKTOP technician",
            technicianRepository
                .getAvailableTechnicians("galle", DeviceCategory.DESKTOP)
                .getOrThrow()
                .isEmpty(),
        )
    }

    @Test
    fun sparePartsAndStockAreSeededUnevenlyAcrossBranches() = runBlocking {
        val parts = sparePartRepository.getSpareParts().getOrThrow()
        assertEquals(9, parts.size)

        val colomboStock = sparePartRepository.getStockForBranch("colombo").getOrThrow()
        val galleStock = sparePartRepository.getStockForBranch("galle").getOrThrow()
        assertEquals(9, colomboStock.size)
        assertEquals(9, galleStock.size)

        val laptopAtGalle = sparePartRepository
            .getAvailabilityForCategory("galle", DeviceCategory.LAPTOP)
            .getOrThrow()
        assertTrue("no laptop-compatible parts came back", laptopAtGalle.isNotEmpty())

        val gallePanel = laptopAtGalle.single { it.part.name == "Laptop LCD Panel 15.6\"" }
        assertEquals("Galle was seeded with zero laptop panels", 0, gallePanel.quantity)
        assertTrue(!gallePanel.inStock)

        val colomboPanel = sparePartRepository
            .getAvailabilityForCategory("colombo", DeviceCategory.LAPTOP)
            .getOrThrow()
            .single { it.part.name == "Laptop LCD Panel 15.6\"" }
        assertEquals(4, colomboPanel.quantity)
        assertTrue(colomboPanel.inStock)

        // The two branches must genuinely differ, otherwise branch matching has
        // nothing to weigh against distance.
        val colomboByPart = colomboStock.associate { it.partId to it.quantity }
        assertTrue(
            "stock is identical at both branches — the seed data is not uneven",
            galleStock.any { colomboByPart[it.partId] != it.quantity },
        )
    }

    // ------------------------------------------------------------------ setup

    private suspend fun signInAsSeedAdmin() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return

        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("seedAdminEmail").orEmpty()
        val password = arguments.getString("seedAdminPassword").orEmpty()
        check(email.isNotBlank() && password.isNotBlank()) {
            "SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD are missing from local.properties — " +
                "the Firestore rules only let an ADMIN write the seed data."
        }

        auth.signInWithEmailAndPassword(email, password).await()
        val role = firestore.collection(FirestoreCollections.USERS)
            .document(requireNotNull(auth.currentUser).uid)
            .get()
            .await()
            .getString("role")
        check(role == "ADMIN") {
            "Seed account $email has role $role, not ADMIN — promote it in the Firebase console."
        }
    }

    private companion object {
        @Volatile
        var seeded = false

        @JvmStatic
        @BeforeClass
        fun resetSeedFlag() {
            seeded = false
        }
    }
}
