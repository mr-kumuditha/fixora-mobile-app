package com.techfix.app.ui.customer.payment

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Format validation for the simulated payment form.
 *
 * These check the *form*, not a payment: nothing in this app is ever charged
 * (see CLAUDE.md). What they pin down is that the demo behaves like a real
 * checkout on screen — a mistyped number, a past expiry, or a short CVV is
 * caught before the flow moves on.
 */
class DemoCardTest {

    private fun calendarAt(year: Int, month: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 15)
        }

    @Test
    fun `card number is grouped in fours as it is typed`() {
        assertEquals("4242 4242 4242 4242", DemoCard.formatCardNumber("4242424242424242"))
        assertEquals("4242 42", DemoCard.formatCardNumber("424242"))
    }

    @Test
    fun `card number ignores anything past sixteen digits`() {
        assertEquals("4242 4242 4242 4242", DemoCard.formatCardNumber("42424242424242429999"))
    }

    @Test
    fun `expiry is punctuated as MM slash YY`() {
        assertEquals("12", DemoCard.formatExpiry("12"))
        assertEquals("12/29", DemoCard.formatExpiry("1229"))
    }

    @Test
    fun `a luhn-valid sixteen digit number passes`() {
        assertNull(DemoCard.cardNumberError("4242 4242 4242 4242"))
        assertNull(DemoCard.cardNumberError("4111 1111 1111 1111"))
    }

    @Test
    fun `a single mistyped digit fails the checksum`() {
        // 4242…4243 is the same length and shape but not a valid number, which
        // is exactly the typo a real checkout catches.
        assertNotNull(DemoCard.cardNumberError("4242 4242 4242 4243"))
    }

    @Test
    fun `a short card number is rejected before the checksum`() {
        assertEquals("Card number must be 16 digits", DemoCard.cardNumberError("4242 4242"))
        assertEquals("Enter a card number", DemoCard.cardNumberError(""))
    }

    @Test
    fun `an expiry in the past is rejected`() {
        val now = calendarAt(year = 2026, month = 8)
        assertEquals("That card has expired", DemoCard.expiryError("07/26", now))
        assertEquals("That card has expired", DemoCard.expiryError("12/25", now))
    }

    @Test
    fun `the current month still counts as valid`() {
        val now = calendarAt(year = 2026, month = 8)
        assertNull(DemoCard.expiryError("08/26", now))
        assertNull(DemoCard.expiryError("01/30", now))
    }

    @Test
    fun `a month outside one to twelve is rejected`() {
        val now = calendarAt(year = 2026, month = 8)
        assertEquals("Month must be between 01 and 12", DemoCard.expiryError("13/29", now))
        assertEquals("Month must be between 01 and 12", DemoCard.expiryError("00/29", now))
    }

    @Test
    fun `cvv must be exactly three digits`() {
        assertNull(DemoCard.cvvError("123"))
        assertEquals("CVV must be 3 digits", DemoCard.cvvError("12"))
        assertEquals("Enter the CVV", DemoCard.cvvError(""))
    }

    @Test
    fun `the receipt only ever shows the last four digits`() {
        assertEquals("4242", DemoCard.lastFour("4242 4242 4242 4242"))
    }
}
