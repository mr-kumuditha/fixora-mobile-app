package com.techfix.app.ui.customer.profile

/** Pure profile-form rules, kept outside Compose and directly unit tested. */
object ProfileValidation {
    const val MAX_NAME_LENGTH = 80
    const val MAX_PHONE_LENGTH = 24

    fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    fun normalizePhone(value: String): String = value.trim()

    fun filterPhoneInput(value: String): String = value
        .filter { character -> character.isDigit() || character in "+ -()" }
        .take(MAX_PHONE_LENGTH)

    fun nameError(value: String): String? {
        val name = normalizeName(value)
        return when {
            name.isEmpty() -> "Full name is required"
            name.length < 2 -> "Enter at least 2 characters"
            name.length > MAX_NAME_LENGTH -> "Use $MAX_NAME_LENGTH characters or fewer"
            else -> null
        }
    }

    fun phoneError(value: String): String? {
        val phone = normalizePhone(value)
        if (phone.isEmpty()) return null
        val digitCount = phone.count(Char::isDigit)
        return when {
            digitCount < 7 -> "Enter a complete phone number"
            digitCount > 15 -> "Phone number has too many digits"
            else -> null
        }
    }
}
