package com.techfix.app.ui.customer.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.ui.graphics.vector.ImageVector
import com.techfix.app.domain.catalog.DeviceCategory

/** Display label + icon per device category — shared across catalog, detail, and booking. */
val DeviceCategory.label: String
    get() = when (this) {
        DeviceCategory.MOBILE -> "Mobile"
        DeviceCategory.LAPTOP -> "Laptop"
        DeviceCategory.DESKTOP -> "Desktop"
        DeviceCategory.TABLET -> "Tablet"
    }

val DeviceCategory.icon: ImageVector
    get() = when (this) {
        DeviceCategory.MOBILE -> Icons.Rounded.Smartphone
        DeviceCategory.LAPTOP -> Icons.Rounded.Laptop
        DeviceCategory.DESKTOP -> Icons.Rounded.DesktopWindows
        DeviceCategory.TABLET -> Icons.Rounded.TabletMac
    }
