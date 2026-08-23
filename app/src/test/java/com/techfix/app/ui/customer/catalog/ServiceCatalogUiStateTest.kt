package com.techfix.app.ui.customer.catalog

import com.techfix.app.core.navigation.CustomerRoutes
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCatalogUiStateTest {

    @Test
    fun `all category preserves the complete original catalog`() {
        val state = loadedState()

        assertEquals(12, state.groupedServices.sumOf { it.second.size })
        assertEquals(
            listOf(
                DeviceCategory.MOBILE to 3,
                DeviceCategory.LAPTOP to 4,
                DeviceCategory.DESKTOP to 3,
                DeviceCategory.TABLET to 2,
            ),
            state.groupedServices.map { (category, services) -> category to services.size },
        )
        assertEquals(12, originalServices.map { it.id }.toSet().size)
    }

    @Test
    fun `each device category shows only its own services`() {
        val expectedCounts = mapOf(
            DeviceCategory.MOBILE to 3,
            DeviceCategory.LAPTOP to 4,
            DeviceCategory.DESKTOP to 3,
            DeviceCategory.TABLET to 2,
        )

        expectedCounts.forEach { (category, expectedCount) ->
            val filtered = loadedState(selectedCategory = category).groupedServices
            assertEquals(listOf(category), filtered.map { it.first })
            assertEquals(expectedCount, filtered.single().second.size)
            assertTrue(filtered.single().second.all { it.category == category })
        }
    }

    @Test
    fun `search is case insensitive and clearing it restores all services`() {
        val batterySearch = loadedState(query = "BATTERY")
        assertEquals(
            setOf("mobile-battery-replacement", "tablet-battery-replacement"),
            batterySearch.groupedServices.flatMap { it.second }.map { it.id }.toSet(),
        )

        val laptopCategorySearch = loadedState(query = "laptop")
        assertEquals(4, laptopCategorySearch.groupedServices.sumOf { it.second.size })

        val cleared = batterySearch.copy(query = "", selectedCategory = null)
        assertEquals(12, cleared.groupedServices.sumOf { it.second.size })
    }

    @Test
    fun `backend errors cannot be presented as an empty catalog`() {
        val error = ServiceCatalogUiState(
            isLoading = false,
            errorMessage = "Unable to load services. Please try again.",
            allServices = emptyList(),
        )

        assertFalse(error.isEmpty)
        assertTrue(error.errorMessage != null)
    }

    @Test
    fun `a successful empty database result uses the empty state`() {
        val empty = ServiceCatalogUiState(
            isLoading = false,
            errorMessage = null,
            allServices = emptyList(),
        )

        assertTrue(empty.isEmpty)
    }

    @Test
    fun `service detail navigation keeps the selected service id`() {
        originalServices.forEach { service ->
            assertEquals("customer/service/${service.id}", CustomerRoutes.serviceDetail(service.id))
        }
    }

    private fun loadedState(
        query: String = "",
        selectedCategory: DeviceCategory? = null,
    ) = ServiceCatalogUiState(
        isLoading = false,
        allServices = originalServices,
        query = query,
        selectedCategory = selectedCategory,
    )

    private companion object {
        val originalServices = listOf(
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

        fun service(id: String, category: DeviceCategory, name: String) = RepairService(
            id = id,
            category = category,
            name = name,
            description = "$name service for ${category.name.lowercase()} devices.",
            basePrice = 1.0,
        )
    }
}
