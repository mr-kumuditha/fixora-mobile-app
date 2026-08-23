package com.techfix.app.ui.customer.profile

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.auth.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {

    @Test
    fun `name is required and whitespace is normalized`() {
        assertEquals("Full name is required", ProfileValidation.nameError("   "))
        assertEquals("Kumuditha Tharinda", ProfileValidation.normalizeName("  Kumuditha   Tharinda  "))
        assertNull(ProfileValidation.nameError("  Kumuditha   Tharinda  "))
    }

    @Test
    fun `name length is bounded`() {
        assertEquals("Enter at least 2 characters", ProfileValidation.nameError("K"))
        assertNull(ProfileValidation.nameError("K".repeat(ProfileValidation.MAX_NAME_LENGTH)))
        assertEquals(
            "Use ${ProfileValidation.MAX_NAME_LENGTH} characters or fewer",
            ProfileValidation.nameError("K".repeat(ProfileValidation.MAX_NAME_LENGTH + 1)),
        )
    }

    @Test
    fun `phone remains optional and accepts existing punctuation`() {
        assertNull(ProfileValidation.phoneError(""))
        assertNull(ProfileValidation.phoneError("+94 (77) 123-4567"))
        assertEquals("+94 (77) 123-4567", ProfileValidation.filterPhoneInput("+94a (77) 123-4567"))
    }

    @Test
    fun `phone rejects incomplete and excessive digit counts`() {
        assertEquals("Enter a complete phone number", ProfileValidation.phoneError("123 45"))
        assertEquals("Phone number has too many digits", ProfileValidation.phoneError("1234567890123456"))
    }

    @Test
    fun `save is enabled only for valid meaningful changes`() {
        val user = AuthUser(
            uid = "uid",
            email = "customer@example.com",
            role = UserRole.CUSTOMER,
            name = "Kumuditha Tharinda",
            phone = "+94 77 123 4567",
        )

        assertFalse(ProfileUiState(user = user).canSave)
        assertTrue(ProfileUiState(user = user, name = "Kumuditha T", phone = user.phone.orEmpty()).canSave)
        assertFalse(ProfileUiState(user = user, name = "K", phone = user.phone.orEmpty()).canSave)
        assertFalse(ProfileUiState(user = user, name = user.name.orEmpty(), phone = user.phone.orEmpty(), isSaving = true).canSave)
    }
}
