package com.techfix.app.domain.operations

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.payment.PaymentMethod
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.repair.DeviceDetails
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.sparepart.SparePartStock
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.user.UserAccountSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalSnapshotTest {
    @Test
    fun `metrics use approved status and successful payment definitions`() {
        val repairs = listOf(
            repair("pending", RepairStatus.SUBMITTED),
            repair("active", RepairStatus.IN_PROGRESS),
            repair("ready", RepairStatus.READY_FOR_PICKUP),
            repair("complete", RepairStatus.COMPLETED),
            repair("cancelled", RepairStatus.CANCELLED),
        )
        val snapshot = OperationalSnapshot(
            repairs = repairs,
            payments = listOf(
                payment("complete", 4_800.0, PaymentStatus.SUCCESS),
                payment("active", 9_999.0, PaymentStatus.FAILED),
            ),
            technicians = listOf(
                Technician("t1", "One", "colombo", listOf(DeviceCategory.MOBILE), true),
                Technician("t2", "Two", "colombo", listOf(DeviceCategory.LAPTOP), false),
            ),
            users = listOf(user("customer", UserRole.CUSTOMER), user("admin", UserRole.ADMIN)),
        )

        with(snapshot.metrics) {
            assertEquals(5, totalRepairs)
            assertEquals(1, pendingRepairs)
            assertEquals(2, activeRepairs)
            assertEquals(1, readyRepairs)
            assertEquals(1, completedRepairs)
            assertEquals(1, cancelledRepairs)
            assertEquals(1, availableTechnicians)
            assertEquals(4_800.0, recordedRevenue, 0.0)
            assertEquals(0.5, completionRate, 0.0)
        }
    }

    @Test
    fun `branch performance joins payments through repair id and stock by branch`() {
        val branch = Branch("colombo", "Colombo", 0.0, 0.0, "Address")
        val snapshot = OperationalSnapshot(
            repairs = listOf(repair("r1", RepairStatus.COMPLETED), repair("r2", RepairStatus.IN_PROGRESS)),
            payments = listOf(payment("r1", 5_000.0, PaymentStatus.SUCCESS)),
            technicians = listOf(Technician("t1", "One", "colombo", listOf(DeviceCategory.MOBILE), true)),
            branches = listOf(branch),
            stockByBranch = mapOf("colombo" to listOf(SparePartStock("p1", "colombo", 0))),
        )

        with(snapshot.branchPerformance.single()) {
            assertEquals(2, totalRepairs)
            assertEquals(1, openRepairs)
            assertEquals(1, completedRepairs)
            assertEquals(1, availableTechnicians)
            assertEquals(5_000.0, recordedRevenue, 0.0)
            assertEquals(1, outOfStockParts)
        }
    }

    private fun repair(id: String, status: RepairStatus) = RepairRequest(
        id = id,
        customerId = "customer",
        serviceId = "service",
        deviceDetails = DeviceDetails(DeviceCategory.MOBILE, "Fixora", "Test"),
        issueDescription = "Issue",
        branchId = "colombo",
        technicianId = "t1",
        status = status,
    )

    private fun payment(repairId: String, amount: Double, status: PaymentStatus) = Payment(
        id = "payment-$repairId-$status",
        repairRequestId = repairId,
        amount = amount,
        method = PaymentMethod.CARD,
        status = status,
        receiptId = "receipt",
    )

    private fun user(uid: String, role: UserRole) = UserAccountSummary(
        uid, "$uid@example.com", uid, null, null, role, null, null, null,
    )
}
