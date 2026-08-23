package com.techfix.app.ui.auth

/**
 * Form-level validation for the sign-in and sign-up screens.
 *
 * This is presentation validation, not authentication: it decides what the
 * fields say before anything is sent, so an empty or malformed entry never
 * costs a network round trip and never comes back as a raw Firebase message.
 * The auth logic itself (Block 2) is untouched — Firebase remains the only
 * thing that decides whether credentials are actually valid.
 *
 * Kept as pure functions rather than `android.util.Patterns` so they can be
 * unit-tested on the JVM: `Patterns.EMAIL_ADDRESS` is a stub in a plain JUnit
 * run and would throw rather than match.
 */
object AuthFormValidation {

    /**
     * Firebase Auth rejects anything shorter on registration, so a shorter
     * password can never belong to a real account and is worth catching on
     * both screens rather than only on sign-up.
     */
    const val MIN_PASSWORD_LENGTH = 6

    /**
     * Deliberately loose: one or more non-space, non-@ characters, an @, a
     * domain with at least one dot and a 2+ letter suffix. It is here to
     * catch a typo before submitting, not to be the authority on what an
     * address is — over-strict client-side email regexes reject valid
     * addresses, and the server checks anyway.
     */
    private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)*\\.[A-Za-z]{2,}$")

    /** Null when the field is fine; otherwise the message to show under it. */
    fun emailError(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "Enter your email address"
            !EMAIL_PATTERN.matches(trimmed) -> "Enter a valid email address, like you@example.com"
            else -> null
        }
    }

    /** Null when the field is fine; otherwise the message to show under it. */
    fun passwordError(value: String): String? = when {
        value.isEmpty() -> "Enter your password"
        value.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    /** True when both fields would pass, i.e. the form is worth submitting. */
    fun isSubmittable(email: String, password: String): Boolean =
        emailError(email) == null && passwordError(password) == null
}
