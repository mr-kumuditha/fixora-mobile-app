package com.techfix.app.domain.technician

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.catalog.DeviceCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicianEligibilityTest {
    private val technician = Technician(
        id = "tech-1",
        name = "Kasun",
        branchId = "colombo",
        categorySkills = listOf(DeviceCategory.MOBILE, DeviceCategory.TABLET),
        available = true,
        active = true,
        linkedUserId = "uid-1",
    )

    @Test fun `active available linked skilled technician is eligible`() {
        assertTrue(technician.isEligibleForAssignment("colombo", DeviceCategory.MOBILE))
    }

    @Test fun `archived technician is excluded`() {
        assertFalse(technician.copy(active = false).isEligibleForAssignment("colombo", DeviceCategory.MOBILE))
    }

    @Test fun `unavailable technician is excluded`() {
        assertFalse(technician.copy(available = false).isEligibleForAssignment("colombo", DeviceCategory.MOBILE))
    }

    @Test fun `missing account link is excluded`() {
        assertFalse(technician.copy(linkedUserId = null).isEligibleForAssignment("colombo", DeviceCategory.MOBILE))
    }

    @Test fun `wrong branch and wrong skill are excluded`() {
        assertFalse(technician.isEligibleForAssignment("galle", DeviceCategory.MOBILE))
        assertFalse(technician.isEligibleForAssignment("colombo", DeviceCategory.DESKTOP))
    }

    @Test fun `exact reciprocal account link is valid`() {
        assertTrue(technician.hasValidAccountLink("uid-1", UserRole.TECHNICIAN, "tech-1", "colombo"))
    }

    @Test fun `wrong uid role technician id or branch invalidates link`() {
        assertFalse(technician.hasValidAccountLink("uid-2", UserRole.TECHNICIAN, "tech-1", "colombo"))
        assertFalse(technician.hasValidAccountLink("uid-1", UserRole.CUSTOMER, "tech-1", "colombo"))
        assertFalse(technician.hasValidAccountLink("uid-1", UserRole.TECHNICIAN, "tech-2", "colombo"))
        assertFalse(technician.hasValidAccountLink("uid-1", UserRole.TECHNICIAN, "tech-1", "galle"))
    }
}

