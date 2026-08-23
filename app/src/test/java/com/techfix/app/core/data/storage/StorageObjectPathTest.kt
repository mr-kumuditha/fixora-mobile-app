package com.techfix.app.core.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StorageObjectPathTest {

    @Test
    fun `profile image follows the live one-folder jpeg policy`() {
        assertEquals(
            "firebase-uid/profile_object-id.jpg",
            StorageObjectPath.profileImage("firebase-uid", "object-id"),
        )
    }

    @Test
    fun `owner cannot inject another storage folder`() {
        assertThrows(IllegalArgumentException::class.java) {
            StorageObjectPath.profileImage("other/uid", "object-id")
        }
    }
}
