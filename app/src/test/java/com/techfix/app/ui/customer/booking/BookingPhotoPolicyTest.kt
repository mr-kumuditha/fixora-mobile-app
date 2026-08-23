package com.techfix.app.ui.customer.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingPhotoPolicyTest {
    @Test
    fun `remaining slots never exceeds range`() {
        assertEquals(5, BookingPhotoPolicy.remainingSlots(0))
        assertEquals(2, BookingPhotoPolicy.remainingSlots(3))
        assertEquals(0, BookingPhotoPolicy.remainingSlots(5))
        assertEquals(0, BookingPhotoPolicy.remainingSlots(8))
    }

    @Test
    fun `continue waits for at least one completed upload`() {
        assertFalse(BookingPhotoPolicy.canContinue(emptyList()))
        assertFalse(BookingPhotoPolicy.canContinue(listOf(ImageUploadStatus.UPLOADING)))
        assertFalse(BookingPhotoPolicy.canContinue(listOf(ImageUploadStatus.FAILED)))
        assertTrue(BookingPhotoPolicy.canContinue(listOf(ImageUploadStatus.UPLOADED)))
    }

    @Test
    fun `an in-flight upload keeps continue disabled`() {
        assertFalse(
            BookingPhotoPolicy.canContinue(
                listOf(ImageUploadStatus.UPLOADED, ImageUploadStatus.UPLOADING),
            ),
        )
        assertTrue(
            BookingPhotoPolicy.canContinue(
                listOf(ImageUploadStatus.UPLOADED, ImageUploadStatus.FAILED),
            ),
        )
    }
}
