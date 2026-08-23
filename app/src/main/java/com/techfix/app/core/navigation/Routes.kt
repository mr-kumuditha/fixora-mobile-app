package com.techfix.app.core.navigation

object Graph {
    const val AUTH = "auth_graph"
    const val CUSTOMER = "customer_graph"
    const val STAFF = "staff_graph"
}

object AuthRoutes {
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
}

/**
 * The five routes that are bottom-bar destinations — [HOME], [CATALOG],
 * [BOOK_START], [HISTORY], [PROFILE] — are listed in
 * [com.techfix.app.ui.customer.CustomerTab]. Everything else here is a
 * drill-down: it takes the whole screen and keeps its own Back arrow.
 */
object CustomerRoutes {
    const val HOME = "customer/home"

    /**
     * The catalog, optionally pre-filtered to one device category — that's
     * what the Home service tiles open. The filter is a query argument so the
     * unfiltered route stays a plain `customer/catalog`, and both forms match
     * this one registered destination.
     */
    const val CATEGORY_ARG = "category"
    const val CATALOG = "customer/catalog?$CATEGORY_ARG={$CATEGORY_ARG}"

    /**
     * The Book Repair tab. A booking is always for a specific service, so
     * this is the same catalog screen in booking mode: picking a service goes
     * straight into the booking flow instead of via the service detail.
     */
    const val BOOK_START = "customer/book"

    const val HISTORY = "customer/history"
    const val PROFILE = "customer/profile"
    const val EDIT_PROFILE = "customer/profile/edit"

    const val SERVICE_ID_ARG = "serviceId"
    const val SERVICE_DETAIL = "customer/service/{$SERVICE_ID_ARG}"
    const val BOOK_REPAIR = "customer/book/{$SERVICE_ID_ARG}"

    const val REQUEST_ID_ARG = "requestId"
    const val TRACKING = "customer/tracking/{$REQUEST_ID_ARG}"
    const val HISTORY_DETAIL = "customer/history/{$REQUEST_ID_ARG}"

    const val PAYMENT = "customer/payment/{$REQUEST_ID_ARG}"

    /** `categoryName` is a [com.techfix.app.domain.catalog.DeviceCategory] name, or null for everything. */
    fun catalog(categoryName: String? = null) =
        if (categoryName == null) "customer/catalog" else "customer/catalog?$CATEGORY_ARG=$categoryName"

    fun serviceDetail(serviceId: String) = "customer/service/$serviceId"
    fun bookRepair(serviceId: String) = "customer/book/$serviceId"
    fun tracking(requestId: String) = "customer/tracking/$requestId"
    fun historyDetail(requestId: String) = "customer/history/$requestId"
    fun payment(requestId: String) = "customer/payment/$requestId"
}

/**
 * One screen set for Admin, Branch Manager, and Technician — what each role
 * can see or do on these screens is a permission flag
 * ([com.techfix.app.ui.staff.StaffContext]), not a separate set of routes.
 */
object StaffRoutes {
    const val DASHBOARD = "staff/dashboard"

    const val TAB_ARG = "tab"
    const val QUEUE = "staff/queue?$TAB_ARG={$TAB_ARG}"

    const val REQUEST_ID_ARG = "requestId"
    const val APPOINTMENT = "staff/appointment/{$REQUEST_ID_ARG}"

    const val INVENTORY = "staff/inventory"
    const val TECHNICIANS = "staff/technicians"
    const val MORE = "staff/more"
    const val BRANCHES = "staff/branches"
    const val USERS = "staff/users"
    const val REPORTS = "staff/reports"

    fun queue(tab: String) = "staff/queue?$TAB_ARG=$tab"
    fun appointment(requestId: String) = "staff/appointment/$requestId"
}
