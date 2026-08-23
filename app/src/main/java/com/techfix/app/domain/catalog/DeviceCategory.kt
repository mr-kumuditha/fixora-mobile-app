package com.techfix.app.domain.catalog

/**
 * The four device categories the repair catalog is organised by. The same
 * values are stored in Firestore (`services.category`) and in Supabase
 * (`technicians.category_skills`, `spare_parts.compatible_categories`), so
 * the branch-matching query in Block 5 can join across the two backends on
 * a category name.
 */
enum class DeviceCategory {
    MOBILE,
    LAPTOP,
    DESKTOP,
    TABLET;

    companion object {
        fun fromRaw(raw: String?): DeviceCategory? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
