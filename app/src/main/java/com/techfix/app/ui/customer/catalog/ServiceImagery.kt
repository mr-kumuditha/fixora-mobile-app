package com.techfix.app.ui.customer.catalog

import androidx.annotation.DrawableRes
import com.techfix.app.R
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService

/**
 * The catalog's visual source of truth. IDs are the seeded Firestore service
 * IDs, so imagery follows a service even when the display name changes.
 * Unknown future services receive a sensible category/keyword fallback until
 * their explicit asset is added here.
 */
object ServiceImagery {
    private val byServiceId = mapOf(
        "mobile-charging-port-repair" to R.drawable.service_mobile_charging_port,
        "mobile-battery-replacement" to R.drawable.service_mobile_battery,
        "mobile-screen-replacement" to R.drawable.service_mobile_screen,
        "laptop-screen-replacement" to R.drawable.service_laptop_screen,
        "laptop-keyboard-replacement" to R.drawable.service_laptop_keyboard,
        "laptop-ssd-upgrade" to R.drawable.service_laptop_upgrade,
        "laptop-thermal-service" to R.drawable.service_laptop_thermal,
        "desktop-diagnostics" to R.drawable.service_desktop_diagnostics,
        "desktop-power-supply-replacement" to R.drawable.service_desktop_power,
        "desktop-storage-upgrade" to R.drawable.service_desktop_storage,
        "tablet-screen-replacement" to R.drawable.service_tablet_screen,
        "tablet-battery-replacement" to R.drawable.service_tablet_battery,
    )

    @DrawableRes
    fun forService(service: RepairService): Int {
        byServiceId[service.id]?.let { return it }
        val normalized = service.name.lowercase()
        return when {
            "screen" in normalized || "display" in normalized -> when (service.category) {
                DeviceCategory.TABLET -> R.drawable.service_tablet_screen
                else -> R.drawable.service_mobile_screen
            }
            "battery" in normalized -> when (service.category) {
                DeviceCategory.TABLET -> R.drawable.service_tablet_battery
                else -> R.drawable.service_mobile_battery
            }
            "keyboard" in normalized -> R.drawable.service_laptop_keyboard
            "ssd" in normalized || "storage" in normalized -> when (service.category) {
                DeviceCategory.DESKTOP -> R.drawable.service_desktop_storage
                else -> R.drawable.service_laptop_upgrade
            }
            "power" in normalized -> R.drawable.service_desktop_power
            "thermal" in normalized || "overheat" in normalized || "clean" in normalized -> R.drawable.service_laptop_thermal
            service.category == DeviceCategory.DESKTOP -> R.drawable.service_desktop_diagnostics
            service.category == DeviceCategory.LAPTOP -> R.drawable.service_laptop_upgrade
            service.category == DeviceCategory.TABLET -> R.drawable.service_tablet_battery
            else -> R.drawable.service_mobile_charging_port
        }
    }
}

@DrawableRes
fun RepairService.catalogImage(): Int = ServiceImagery.forService(this)

fun RepairService.catalogImageDescription(): String = "$name repair service"
