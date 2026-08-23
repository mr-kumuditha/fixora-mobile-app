package com.techfix.app.domain.draft

/**
 * Persistence for the single in-progress booking draft. Local-only by
 * design — a draft is not a repair request and is never sent to Firestore
 * until the customer submits it.
 */
interface DraftRepairRequestRepository {

    /**
     * The stored draft, but only when it belongs to this customer and this
     * service. A draft for a different service is not returned (and is
     * replaced by the next save) because only one draft is kept.
     */
    suspend fun load(customerId: String, serviceId: String): DraftRepairRequest?

    suspend fun save(draft: DraftRepairRequest)

    suspend fun clear()
}
