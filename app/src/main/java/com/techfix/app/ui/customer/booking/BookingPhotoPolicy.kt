package com.techfix.app.ui.customer.booking

/**
 * Booking-photo rules live outside Compose so the picker, camera, ViewModel,
 * and tests all agree. The current repair-request contract requires one
 * uploaded image and stores only remote URLs, so navigation waits for upload.
 */
object BookingPhotoPolicy {
    const val MAX_PHOTOS = 5
    const val MIN_REQUIRED_PHOTOS = 1

    fun remainingSlots(currentCount: Int): Int =
        (MAX_PHOTOS - currentCount).coerceAtLeast(0)

    fun canContinue(statuses: List<ImageUploadStatus>): Boolean =
        statuses.count { it == ImageUploadStatus.UPLOADED } >= MIN_REQUIRED_PHOTOS &&
            statuses.none { it == ImageUploadStatus.UPLOADING }
}
