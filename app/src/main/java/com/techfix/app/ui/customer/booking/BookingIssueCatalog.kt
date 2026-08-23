package com.techfix.app.ui.customer.booking

import com.techfix.app.domain.catalog.DeviceCategory

/** Customer-facing issue shortcuts. They assist description entry only. */
object BookingIssueCatalog {
    fun suggestionsFor(category: DeviceCategory?, serviceName: String): List<String> {
        val normalizedService = serviceName.lowercase()
        return when {
            "screen" in normalizedService || "display" in normalizedService ->
                listOf("Cracked screen", "Touch not responding", "Lines on display")
            "battery" in normalizedService ->
                listOf("Drains quickly", "Won't charge", "Battery is swollen")
            "charging" in normalizedService || "port" in normalizedService ->
                listOf("Loose connection", "Won't charge", "Port feels damaged")
            "keyboard" in normalizedService ->
                listOf("Keys not working", "Liquid spill", "Keys feel stuck")
            else -> defaultsByCategory[category].orEmpty()
        }
    }

    private val defaultsByCategory = mapOf(
        DeviceCategory.MOBILE to listOf(
            "Won't turn on",
            "Overheating",
            "Water damage",
            "Slow or freezing",
        ),
        DeviceCategory.LAPTOP to listOf(
            "Won't turn on",
            "Slow performance",
            "Keyboard issue",
            "Screen issue",
            "Battery issue",
        ),
        DeviceCategory.DESKTOP to listOf(
            "Won't turn on",
            "No display",
            "Slow performance",
            "Unusual noise",
            "Overheating",
        ),
        DeviceCategory.TABLET to listOf(
            "Won't turn on",
            "Touch not responding",
            "Battery draining",
            "Water damage",
        ),
    )
}
