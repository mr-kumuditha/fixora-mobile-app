package com.techfix.app.domain.technician

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.catalog.DeviceCategory

/** Pure assignment predicates shared by the Firestore repository and JVM tests. */
fun Technician.isEligibleForAssignment(branchId: String, category: DeviceCategory): Boolean =
    active && available && linkedUserId != null && this.branchId == branchId &&
        category in categorySkills

fun Technician.hasValidAccountLink(
    userUid: String?,
    userRole: UserRole?,
    userTechnicianId: String?,
    userBranchId: String?,
): Boolean =
    linkedUserId != null && linkedUserId == userUid && userRole == UserRole.TECHNICIAN &&
        userTechnicianId == id && userBranchId == branchId
