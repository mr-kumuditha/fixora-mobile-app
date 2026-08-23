package com.techfix.app.ui.staff

import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.repair.DeviceDetails
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StaffAppointmentsUiStateTest {
    @Test
    fun `search matches repair reference device and resolved metadata`() {
        val repair = RepairRequest(
            "FX-123", "customer", "screen", DeviceDetails(DeviceCategory.MOBILE, "Samsung", "S24"),
            "Broken display", branchId = "colombo", technicianId = "tech", status = RepairStatus.IN_PROGRESS,
        )
        val base = StaffAppointmentsUiState(
            isLoading = false,
            tab = StaffQueueTab.ACTIVE,
            activeRequests = listOf(repair),
            serviceNames = mapOf("screen" to "Screen repair"),
            branchNames = mapOf("colombo" to "Colombo"),
            technicianNames = mapOf("tech" to "Nuwan Perera"),
        )
        listOf("FX-123", "Samsung", "screen", "Colombo", "Nuwan").forEach { query ->
            assertEquals(1, base.copy(query = query).visibleRequests.size)
        }
        assertEquals(0, base.copy(query = "Galle").visibleRequests.size)
    }
}
