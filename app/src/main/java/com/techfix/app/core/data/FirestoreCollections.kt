package com.techfix.app.core.data

/** Firestore collection names, in one place so seeding and reads can't drift. */
object FirestoreCollections {
    const val USERS = "users"
    const val SERVICES = "services"
    const val BRANCHES = "branches"
    const val REPAIR_REQUESTS = "repairRequests"
    const val PAYMENTS = "payments"
}
