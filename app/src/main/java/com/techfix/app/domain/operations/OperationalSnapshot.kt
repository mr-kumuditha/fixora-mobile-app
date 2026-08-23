package com.techfix.app.domain.operations

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.sparepart.SparePartStock
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.user.UserAccountSummary

/** One reusable, immutable source for dashboard and report calculations. */
data class OperationalSnapshot(
    val repairs: List<RepairRequest>,
    val payments: List<Payment> = emptyList(),
    val technicians: List<Technician> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val stockByBranch: Map<String, List<SparePartStock>> = emptyMap(),
    val users: List<UserAccountSummary> = emptyList(),
) {
    val metrics: OperationalMetrics get() = OperationalMetrics.from(this)
    val branchPerformance: List<BranchPerformance> get() = branches.map { branch ->
        val branchRepairs = repairs.filter { it.branchId == branch.id }
        val repairIds = branchRepairs.mapTo(hashSetOf()) { it.id }
        BranchPerformance(
            branch = branch,
            totalRepairs = branchRepairs.size,
            openRepairs = branchRepairs.count { !it.status.isTerminal },
            completedRepairs = branchRepairs.count { it.status == RepairStatus.COMPLETED },
            availableTechnicians = technicians.count { it.branchId == branch.id && it.available },
            recordedRevenue = payments
                .filter { it.status == PaymentStatus.SUCCESS && it.repairRequestId in repairIds }
                .sumOf { it.amount },
            outOfStockParts = stockByBranch[branch.id].orEmpty().count { it.quantity == 0 },
        )
    }
}

data class OperationalMetrics(
    val totalRepairs: Int,
    val pendingRepairs: Int,
    val activeRepairs: Int,
    val readyRepairs: Int,
    val completedRepairs: Int,
    val cancelledRepairs: Int,
    val availableTechnicians: Int,
    val totalUsers: Int,
    val customerUsers: Int,
    val recordedRevenue: Double,
    val outOfStockParts: Int,
) {
    val completionRate: Double
        get() {
            val terminal = completedRepairs + cancelledRepairs
            return if (terminal == 0) 0.0 else completedRepairs.toDouble() / terminal
        }

    companion object {
        fun from(snapshot: OperationalSnapshot) = OperationalMetrics(
            totalRepairs = snapshot.repairs.size,
            pendingRepairs = snapshot.repairs.count { it.status == RepairStatus.SUBMITTED },
            activeRepairs = snapshot.repairs.count { !it.status.isTerminal && it.status != RepairStatus.SUBMITTED },
            readyRepairs = snapshot.repairs.count { it.status == RepairStatus.READY_FOR_PICKUP },
            completedRepairs = snapshot.repairs.count { it.status == RepairStatus.COMPLETED },
            cancelledRepairs = snapshot.repairs.count { it.status == RepairStatus.CANCELLED },
            availableTechnicians = snapshot.technicians.count { it.available },
            totalUsers = snapshot.users.size,
            customerUsers = snapshot.users.count { it.role == UserRole.CUSTOMER },
            recordedRevenue = snapshot.payments.filter { it.status == PaymentStatus.SUCCESS }.sumOf { it.amount },
            outOfStockParts = snapshot.stockByBranch.values.flatten().count { it.quantity == 0 },
        )
    }
}

data class BranchPerformance(
    val branch: Branch,
    val totalRepairs: Int,
    val openRepairs: Int,
    val completedRepairs: Int,
    val availableTechnicians: Int,
    val recordedRevenue: Double,
    val outOfStockParts: Int,
)
