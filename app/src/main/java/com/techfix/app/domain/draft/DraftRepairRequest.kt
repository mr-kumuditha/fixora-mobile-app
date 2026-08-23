package com.techfix.app.domain.draft

import com.techfix.app.domain.catalog.DeviceCategory

/**
 * A Book Repair flow that was started but never submitted, kept so the
 * booking survives the app being killed or the connection dropping partway
 * through. Mirrors the fields the customer fills in across steps 1-4; the
 * repair request itself is only created in Firestore on submit.
 */
data class DraftRepairRequest(
    val customerId: String,
    val serviceId: String,
    val step: Int,
    val category: DeviceCategory?,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val issueDescription: String,
    val images: List<DraftImage>,
    val selectedBranchId: String?,
    val scheduledAt: Long?,
    /**
     * How many photos were still uploading or failed when the draft was
     * written. They are not stored (see [DraftImage]), but the count is, so
     * the restored flow can say what went missing instead of silently
     * dropping them.
     */
    val unsavedImageCount: Int = 0,
) {
    /** Nothing worth restoring — used to avoid writing an empty draft row. */
    val isBlank: Boolean
        get() = brand.isBlank() &&
            model.isBlank() &&
            serialNumber.isBlank() &&
            issueDescription.isBlank() &&
            images.isEmpty()
}

/**
 * A photo that already reached Supabase Storage. Only uploaded photos are
 * kept in a draft: a local `content://` URI from the photo picker is only
 * readable for the life of the process that was granted it, so restoring one
 * after the app was killed would give a broken thumbnail and a retry that
 * could never succeed. The Supabase URL, by contrast, is still valid.
 */
data class DraftImage(
    val id: String,
    val remoteUrl: String,
)
