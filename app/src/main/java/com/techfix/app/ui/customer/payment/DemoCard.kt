package com.techfix.app.ui.customer.payment

import java.util.Calendar

/**
 * Format validation for the **simulated** payment form.
 *
 * Nothing here contacts a payment processor, and no card details are stored,
 * transmitted, or written to Firestore — the receipt record keeps only the
 * amount, the method, and a generated receipt id (see CLAUDE.md: payment is a
 * demo flow only). These checks exist so the form behaves like a real one on
 * screen, not so a real card can be charged.
 */
object DemoCard {

    const val CARD_NUMBER_DIGITS = 16
    const val CVV_DIGITS = 3

    /** Strips grouping spaces so validation always sees digits only. */
    fun digitsOf(value: String): String = value.filter(Char::isDigit)

    /** Groups a card number in fours as it is typed: 4242 4242 4242 4242. */
    fun formatCardNumber(raw: String): String =
        digitsOf(raw).take(CARD_NUMBER_DIGITS).chunked(4).joinToString(" ")

    /** Keeps the expiry field as MM/YY while the user types. */
    fun formatExpiry(raw: String): String {
        val digits = digitsOf(raw).take(4)
        return when {
            digits.length <= 2 -> digits
            else -> "${digits.take(2)}/${digits.drop(2)}"
        }
    }

    /**
     * The Luhn checksum every real card number satisfies. Included so the
     * form rejects a mistyped number the way a real checkout would — it is a
     * format check, not an authorisation.
     */
    fun passesLuhn(number: String): Boolean {
        val digits = digitsOf(number)
        if (digits.length != CARD_NUMBER_DIGITS) return false
        var sum = 0
        digits.reversed().forEachIndexed { index, char ->
            val digit = char - '0'
            sum += if (index % 2 == 1) {
                (digit * 2).let { if (it > 9) it - 9 else it }
            } else {
                digit
            }
        }
        return sum % 10 == 0
    }

    fun cardNumberError(value: String): String? {
        val digits = digitsOf(value)
        return when {
            digits.isEmpty() -> "Enter a card number"
            digits.length < CARD_NUMBER_DIGITS -> "Card number must be $CARD_NUMBER_DIGITS digits"
            !passesLuhn(digits) -> "That card number isn't valid"
            else -> null
        }
    }

    /** MM/YY, a real month, and not already past. */
    fun expiryError(value: String, now: Calendar = Calendar.getInstance()): String? {
        val digits = digitsOf(value)
        if (digits.length < 4) return "Enter the expiry as MM/YY"
        val month = digits.take(2).toInt()
        if (month !in 1..12) return "Month must be between 01 and 12"

        val year = 2000 + digits.drop(2).toInt()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        return if (year < currentYear || (year == currentYear && month < currentMonth)) {
            "That card has expired"
        } else {
            null
        }
    }

    fun cvvError(value: String): String? {
        val digits = digitsOf(value)
        return when {
            digits.isEmpty() -> "Enter the CVV"
            digits.length != CVV_DIGITS -> "CVV must be $CVV_DIGITS digits"
            else -> null
        }
    }

    fun nameError(value: String): String? =
        if (value.isBlank()) "Enter the name on the card" else null

    /** Last four, for the receipt line — never the full number. */
    fun lastFour(value: String): String = digitsOf(value).takeLast(4)
}
