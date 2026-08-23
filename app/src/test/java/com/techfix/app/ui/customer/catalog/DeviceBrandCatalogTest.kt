package com.techfix.app.ui.customer.catalog

import com.techfix.app.domain.catalog.DeviceCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBrandCatalogTest {
    @Test
    fun `brand options follow category and retain manual fallback`() {
        val mobile = DeviceBrandCatalog.brandsFor(DeviceCategory.MOBILE)
        val desktop = DeviceBrandCatalog.brandsFor(DeviceCategory.DESKTOP)

        assertTrue("Google" in mobile)
        assertFalse("Custom Build" in mobile)
        assertTrue("Custom Build" in desktop)
        assertTrue("Other" in mobile)
        assertTrue("Other" in desktop)
    }

    @Test
    fun `suggested models always permit manual entry`() {
        assertTrue(
            "Other model" in DeviceBrandCatalog.suggestedModelsFor(DeviceCategory.MOBILE, "Samsung"),
        )
        assertTrue(
            "Other model" in DeviceBrandCatalog.suggestedModelsFor(DeviceCategory.TABLET, "Other"),
        )
    }
}
