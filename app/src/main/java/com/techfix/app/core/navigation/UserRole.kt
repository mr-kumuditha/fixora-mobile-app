package com.techfix.app.core.navigation

/**
 * Admin, Branch Manager, and Technician are distinct roles in the data model
 * (see CLAUDE.md) but share one staff screen set gated by this flag, rather
 * than three separate screen sets.
 */
enum class UserRole {
    CUSTOMER,
    TECHNICIAN,
    BRANCH_MANAGER,
    ADMIN;

    val isStaff: Boolean
        get() = this != CUSTOMER
}
