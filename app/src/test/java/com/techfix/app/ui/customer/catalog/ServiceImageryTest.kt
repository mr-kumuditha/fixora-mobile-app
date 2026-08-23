package com.techfix.app.ui.customer.catalog

import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceImageryTest {
    @Test
    fun `seeded services use distinct relevant assets`() {
        val services = listOf(
            service("mobile-screen-replacement", DeviceCategory.MOBILE, "Phone Screen Replacement"),
            service("mobile-battery-replacement", DeviceCategory.MOBILE, "Phone Battery Replacement"),
            service("mobile-charging-port-repair", DeviceCategory.MOBILE, "Charging Port Repair"),
            service("laptop-screen-replacement", DeviceCategory.LAPTOP, "Laptop Screen Replacement"),
            service("laptop-keyboard-replacement", DeviceCategory.LAPTOP, "Laptop Keyboard Replacement"),
            service("laptop-ssd-upgrade", DeviceCategory.LAPTOP, "SSD Upgrade and Data Migration"),
            service("laptop-thermal-service", DeviceCategory.LAPTOP, "Overheating and Thermal Service"),
            service("desktop-diagnostics", DeviceCategory.DESKTOP, "Desktop Hardware Diagnostics"),
            service("desktop-power-supply-replacement", DeviceCategory.DESKTOP, "Power Supply Replacement"),
            service("desktop-storage-upgrade", DeviceCategory.DESKTOP, "Desktop Storage Upgrade"),
            service("tablet-screen-replacement", DeviceCategory.TABLET, "Tablet Screen Replacement"),
            service("tablet-battery-replacement", DeviceCategory.TABLET, "Tablet Battery Replacement"),
        )

        val imageIds = services.map { it.catalogImage() }
        assertEquals("Every seeded service should have its own image", imageIds.size, imageIds.toSet().size)
        assertTrue("The charging service should use a charging-port asset", services[2].catalogImageDescription().contains("Charging Port"))
    }

    private fun service(id: String, category: DeviceCategory, name: String) = RepairService(
        id = id,
        category = category,
        name = name,
        description = "Repair service",
        basePrice = 1000.0,
    )
}
