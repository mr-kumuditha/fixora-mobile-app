package com.techfix.app.ui.customer.catalog

import com.techfix.app.domain.catalog.DeviceCategory

/**
 * Maintainable customer-facing device suggestions. These are suggestions,
 * not backend facts: the booking still accepts Other and a manually entered
 * model so the catalog never blocks a device that is not listed here.
 */
object DeviceBrandCatalog {
    private val brandsByCategory = mapOf(
        DeviceCategory.MOBILE to listOf("Apple", "Samsung", "Xiaomi", "Redmi", "OnePlus", "Oppo", "Vivo", "Huawei", "Google", "Nokia", "Sony", "Other"),
        DeviceCategory.LAPTOP to listOf("Apple", "Dell", "HP", "Lenovo", "Asus", "Acer", "MSI", "Microsoft", "Samsung", "Toshiba", "Other"),
        DeviceCategory.DESKTOP to listOf("Dell", "HP", "Lenovo", "Asus", "Acer", "MSI", "Custom Build", "Other"),
        DeviceCategory.TABLET to listOf("Apple", "Samsung", "Xiaomi", "Huawei", "Lenovo", "Microsoft", "Amazon", "Other"),
    )

    private val modelsByBrand = mapOf(
        "Apple" to listOf("iPhone 15 Pro", "iPhone 14", "iPhone 13", "MacBook Air", "iPad Pro", "Other model"),
        "Samsung" to listOf("Galaxy S24 Ultra", "Galaxy S23", "Galaxy A55", "Galaxy Book", "Galaxy Tab S9", "Other model"),
        "Xiaomi" to listOf("Xiaomi 14", "Xiaomi 13", "Redmi Note 13", "Redmi Note 12", "Other model"),
        "Redmi" to listOf("Redmi Note 13", "Redmi Note 12", "Redmi 12", "Other model"),
        "OnePlus" to listOf("OnePlus 12", "OnePlus 11", "OnePlus Nord", "Other model"),
        "Oppo" to listOf("Reno 11", "Reno 10", "A78", "Other model"),
        "Vivo" to listOf("V29", "V27", "Y100", "Other model"),
        "Huawei" to listOf("P60 Pro", "Nova 11", "MatePad", "Other model"),
        "Google" to listOf("Pixel 8 Pro", "Pixel 8", "Pixel 7", "Other model"),
        "Dell" to listOf("Inspiron", "XPS 13", "Latitude", "OptiPlex", "Other model"),
        "HP" to listOf("Pavilion", "Envy", "ProBook", "EliteBook", "Other model"),
        "Lenovo" to listOf("ThinkPad", "IdeaPad", "Yoga", "Legion", "Other model"),
        "Asus" to listOf("ZenBook", "VivoBook", "ROG", "Other model"),
        "Acer" to listOf("Aspire", "Swift", "Nitro", "Other model"),
        "MSI" to listOf("Modern", "Prestige", "Katana", "Other model"),
        "Microsoft" to listOf("Surface Laptop", "Surface Pro", "Other model"),
        "Amazon" to listOf("Fire HD 10", "Fire Max 11", "Other model"),
        "Nokia" to listOf("G60", "X30", "Other model"),
        "Sony" to listOf("Xperia 1", "Xperia 5", "Other model"),
        "Toshiba" to listOf("Satellite", "Tecra", "Other model"),
        "Custom Build" to listOf("Custom desktop", "Other model"),
    )

    fun brandsFor(category: DeviceCategory?): List<String> = brandsByCategory[category].orEmpty()

    fun suggestedModelsFor(category: DeviceCategory?, brand: String): List<String> {
        val suggestions = modelsByBrand[brand].orEmpty()
        return when (category) {
            DeviceCategory.MOBILE -> suggestions.filterNot { it.contains("MacBook") || it.contains("Book") || it.contains("Tab") || it.contains("Pad") }.ensureOtherModel()
            DeviceCategory.LAPTOP -> suggestions.filterNot { it.startsWith("iPhone") || it.startsWith("iPad") || it.startsWith("Pixel") || it.startsWith("Galaxy S") || it.startsWith("Galaxy A") || it.startsWith("Galaxy Tab") || it.startsWith("Reno") || it.startsWith("OnePlus") || it.startsWith("Redmi") || it.startsWith("Xiaomi") || it == "V29" || it == "V27" || it == "Y100" }.ensureOtherModel()
            DeviceCategory.TABLET -> suggestions.filterNot { it.startsWith("iPhone") || it.startsWith("MacBook") || it.startsWith("Pixel") || it.startsWith("Galaxy S") || it.startsWith("Galaxy A") || it.startsWith("Galaxy Book") || it.startsWith("P60") || it.startsWith("Nova") || it.startsWith("Reno") || it.startsWith("OnePlus") || it.startsWith("Redmi") || it.startsWith("Xiaomi") || it == "V29" || it == "V27" || it == "Y100" || it == "G60" || it == "X30" || it.startsWith("Xperia") }.ensureOtherModel()
            DeviceCategory.DESKTOP, null -> suggestions.ifEmpty { listOf("Other model") }
        }
    }

    private fun List<String>.ensureOtherModel(): List<String> = if ("Other model" in this) this else this + "Other model"
}
