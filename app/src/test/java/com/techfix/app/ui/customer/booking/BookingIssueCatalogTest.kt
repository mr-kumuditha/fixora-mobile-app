package com.techfix.app.ui.customer.booking

import com.techfix.app.domain.catalog.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingIssueCatalogTest {
    @Test
    fun `service-specific suggestions take priority`() {
        assertEquals(
            listOf("Keys not working", "Liquid spill", "Keys feel stuck"),
            BookingIssueCatalog.suggestionsFor(DeviceCategory.LAPTOP, "Laptop Keyboard Replacement"),
        )
    }

    @Test
    fun `fallback suggestions follow device category`() {
        val mobile = BookingIssueCatalog.suggestionsFor(DeviceCategory.MOBILE, "General diagnosis")
        val desktop = BookingIssueCatalog.suggestionsFor(DeviceCategory.DESKTOP, "General diagnosis")

        assertTrue("Water damage" in mobile)
        assertTrue("No display" in desktop)
    }
}
