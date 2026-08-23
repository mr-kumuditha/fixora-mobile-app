package com.techfix.app.ui.staff

import com.techfix.app.core.navigation.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The role gating behind the single shared staff screen set. Admin, Branch
 * Manager, and Technician are three roles in the data model but one set of
 * screens (see CLAUDE.md), so every difference between them lives here — and
 * these tests are what pin it down.
 */
class StaffContextTest {

    private fun context(
        role: UserRole,
        branchId: String? = "colombo",
        technicianId: String? = null,
    ) = StaffContext(uid = "uid", role = role, branchId = branchId, technicianId = technicianId)

    @Test
    fun `only admin and branch manager can assign an appointment`() {
        assertTrue(context(UserRole.ADMIN, branchId = null).canAssign)
        assertTrue(context(UserRole.BRANCH_MANAGER).canAssign)
        assertFalse(context(UserRole.TECHNICIAN).canAssign)
    }

    @Test
    fun `stock writes fail closed for every Firebase role`() {
        assertFalse(context(UserRole.ADMIN).canEditStock)
        assertFalse(context(UserRole.BRANCH_MANAGER).canEditStock)
        assertFalse(context(UserRole.TECHNICIAN).canEditStock)
    }

    @Test
    fun `only admin can enter the secure inventory management flow`() {
        assertTrue(context(UserRole.ADMIN).canManageInventory)
        assertFalse(context(UserRole.BRANCH_MANAGER).canManageInventory)
        assertFalse(context(UserRole.TECHNICIAN).canManageInventory)
        assertFalse(context(UserRole.CUSTOMER).canManageInventory)
    }

    @Test
    fun `an admin is unscoped, a branch manager is not`() {
        assertTrue(context(UserRole.ADMIN, branchId = null).seesAllBranches)
        assertFalse(context(UserRole.BRANCH_MANAGER, branchId = "galle").seesAllBranches)
    }

    @Test
    fun `an admin stays unscoped even with a branch on their record`() {
        assertTrue(context(UserRole.ADMIN, branchId = "colombo").seesAllBranches)
    }

    @Test
    fun `a staff record with no branch fails closed`() {
        val manager = context(UserRole.BRANCH_MANAGER, branchId = null)
        assertFalse(manager.seesAllBranches)
        assertFalse(manager.hasRequiredScope)
    }

    @Test
    fun `a technician always uses assigned scope and requires a technician link`() {
        assertTrue(context(UserRole.TECHNICIAN, technicianId = "tech-1").seesOnlyOwnRepairs)
        assertTrue(context(UserRole.TECHNICIAN, technicianId = null).seesOnlyOwnRepairs)
        assertFalse(context(UserRole.TECHNICIAN, technicianId = null).hasRequiredScope)
        assertFalse(context(UserRole.BRANCH_MANAGER, technicianId = "tech-1").seesOnlyOwnRepairs)
    }
}
