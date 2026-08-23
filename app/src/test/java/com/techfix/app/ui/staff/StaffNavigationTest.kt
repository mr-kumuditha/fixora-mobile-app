package com.techfix.app.ui.staff

import com.techfix.app.core.navigation.StaffRoutes
import com.techfix.app.core.navigation.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffNavigationTest {
    @Test
    fun `admin and branch manager receive operational five destination shell`() {
        listOf(UserRole.ADMIN, UserRole.BRANCH_MANAGER).forEach { role ->
            val routes = staffDestinations(role).map { it.route.substringBefore('?') }
            assertEquals(5, routes.size)
            assertTrue(StaffRoutes.TECHNICIANS in routes)
            assertTrue(StaffRoutes.INVENTORY in routes)
            assertTrue(StaffRoutes.MORE in routes)
        }
    }

    @Test
    fun `technician shell excludes team administration and opens assigned repairs`() {
        val destinations = staffDestinations(UserRole.TECHNICIAN)
        assertFalse(destinations.any { it.route == StaffRoutes.TECHNICIANS })
        assertTrue(destinations.any { it.label == "My repairs" && it.route.contains(StaffQueueTab.ACTIVE.name) })
        assertEquals(4, destinations.size)
    }
}
