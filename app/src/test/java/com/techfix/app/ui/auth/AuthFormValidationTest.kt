package com.techfix.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline field validation on the sign-in and sign-up screens. Pure
 * functions, so these run on the JVM with no device and no Firebase.
 */
class AuthFormValidationTest {

    @Test
    fun `an empty email asks for one rather than complaining about the format`() {
        assertEquals("Enter your email address", AuthFormValidation.emailError(""))
        assertEquals("Enter your email address", AuthFormValidation.emailError("   "))
    }

    @Test
    fun `an address without an at sign or a domain is rejected`() {
        assertNotNull(AuthFormValidation.emailError("kumuditha"))
        assertNotNull(AuthFormValidation.emailError("kumuditha@"))
        assertNotNull(AuthFormValidation.emailError("kumuditha@example"))
        assertNotNull(AuthFormValidation.emailError("@example.com"))
        assertNotNull(AuthFormValidation.emailError("kumuditha example@mail.com"))
    }

    @Test
    fun `ordinary addresses pass, including subdomains and plus tags`() {
        assertNull(AuthFormValidation.emailError("kumuditha@example.com"))
        assertNull(AuthFormValidation.emailError("first.last+fixora@mail.example.co.uk"))
        assertNull(AuthFormValidation.emailError("staff123@fixora.lk"))
    }

    @Test
    fun `surrounding whitespace does not make a valid address invalid`() {
        assertNull(AuthFormValidation.emailError("  kumuditha@example.com  "))
    }

    @Test
    fun `an empty password asks for one rather than complaining about length`() {
        assertEquals("Enter your password", AuthFormValidation.passwordError(""))
    }

    @Test
    fun `a password shorter than Firebase's minimum is rejected with the length`() {
        assertEquals("Use at least 6 characters", AuthFormValidation.passwordError("abc12"))
        assertEquals(6, AuthFormValidation.MIN_PASSWORD_LENGTH)
    }

    @Test
    fun `a password at the minimum length is accepted`() {
        assertNull(AuthFormValidation.passwordError("abc123"))
        assertNull(AuthFormValidation.passwordError("a very long passphrase"))
    }

    @Test
    fun `spaces count towards password length because Firebase counts them too`() {
        // Not trimmed, unlike the email: a leading space is part of the
        // password the account was created with.
        assertNull(AuthFormValidation.passwordError("   abc"))
    }

    @Test
    fun `the form is submittable only when both fields pass`() {
        assertTrue(AuthFormValidation.isSubmittable("kumuditha@example.com", "abc123"))
        assertFalse(AuthFormValidation.isSubmittable("kumuditha", "abc123"))
        assertFalse(AuthFormValidation.isSubmittable("kumuditha@example.com", "abc"))
        assertFalse(AuthFormValidation.isSubmittable("", ""))
    }
}
