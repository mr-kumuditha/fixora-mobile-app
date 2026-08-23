package com.techfix.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.catalog.DeviceCategory
import kotlinx.coroutines.tasks.await

/**
 * Block 3 seed data for the Firestore side (branches and the service
 * catalog). Test-source only — it is never compiled into the app.
 *
 * Fixed document ids, written with set(), so re-running the seed updates the
 * same rows instead of piling up duplicates. Prices are LKR.
 *
 * Technicians were migrated once into Firestore with stable UUIDs. Spare
 * parts and stock remain seeded by docs/supabase/schema.sql.
 */
object FirestoreSeedData {

    data class SeedBranch(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val address: String,
    )

    data class SeedService(
        val id: String,
        val category: DeviceCategory,
        val name: String,
        val description: String,
        val basePrice: Double,
    )

    val branches = listOf(
        SeedBranch(
            id = "colombo",
            name = "Fixora Colombo",
            latitude = 6.9271,
            longitude = 79.8612,
            address = "No. 42, Union Place, Colombo 02",
        ),
        SeedBranch(
            id = "galle",
            name = "Fixora Galle",
            latitude = 6.0535,
            longitude = 80.2210,
            address = "No. 18, Wackwella Road, Galle",
        ),
    )

    val services = listOf(
        SeedService(
            id = "mobile-screen-replacement",
            category = DeviceCategory.MOBILE,
            name = "Phone Screen Replacement",
            description = "Cracked or unresponsive display replaced with a tested panel, same-day at most branches.",
            basePrice = 12500.0,
        ),
        SeedService(
            id = "mobile-battery-replacement",
            category = DeviceCategory.MOBILE,
            name = "Phone Battery Replacement",
            description = "Worn battery swapped out and health-tested, with a 6-month warranty.",
            basePrice = 6500.0,
        ),
        SeedService(
            id = "mobile-charging-port-repair",
            category = DeviceCategory.MOBILE,
            name = "Charging Port Repair",
            description = "Loose or dead charging port cleaned or the flex assembly replaced.",
            basePrice = 4800.0,
        ),
        SeedService(
            id = "laptop-screen-replacement",
            category = DeviceCategory.LAPTOP,
            name = "Laptop Screen Replacement",
            description = "Cracked, flickering or dim laptop panels replaced and colour-checked.",
            basePrice = 28000.0,
        ),
        SeedService(
            id = "laptop-keyboard-replacement",
            category = DeviceCategory.LAPTOP,
            name = "Laptop Keyboard Replacement",
            description = "Dead keys, spill damage or a worn keyboard module replaced.",
            basePrice = 9500.0,
        ),
        SeedService(
            id = "laptop-ssd-upgrade",
            category = DeviceCategory.LAPTOP,
            name = "SSD Upgrade and Data Migration",
            description = "NVMe SSD fitted with your existing installation and files cloned across.",
            basePrice = 15000.0,
        ),
        SeedService(
            id = "laptop-thermal-service",
            category = DeviceCategory.LAPTOP,
            name = "Overheating and Thermal Service",
            description = "Full teardown clean, fan service and thermal paste replacement.",
            basePrice = 6000.0,
        ),
        SeedService(
            id = "desktop-diagnostics",
            category = DeviceCategory.DESKTOP,
            name = "Desktop Hardware Diagnostics",
            description = "Bench diagnosis of no-boot, crash and stability faults, with a written report.",
            basePrice = 7500.0,
        ),
        SeedService(
            id = "desktop-power-supply-replacement",
            category = DeviceCategory.DESKTOP,
            name = "Power Supply Replacement",
            description = "Faulty PSU replaced with a tested unit rated for your build.",
            basePrice = 11000.0,
        ),
        SeedService(
            id = "desktop-storage-upgrade",
            category = DeviceCategory.DESKTOP,
            name = "Desktop Storage Upgrade",
            description = "Additional or replacement NVMe/SATA storage fitted and configured.",
            basePrice = 13500.0,
        ),
        SeedService(
            id = "tablet-screen-replacement",
            category = DeviceCategory.TABLET,
            name = "Tablet Screen Replacement",
            description = "Digitiser and display assembly replaced, touch calibration verified.",
            basePrice = 18000.0,
        ),
        SeedService(
            id = "tablet-battery-replacement",
            category = DeviceCategory.TABLET,
            name = "Tablet Battery Replacement",
            description = "Swollen or fast-draining tablet battery replaced and load-tested.",
            basePrice = 8500.0,
        ),
    )

    suspend fun seed(firestore: FirebaseFirestore) {
        branches.forEach { branch ->
            firestore.collection(FirestoreCollections.BRANCHES)
                .document(branch.id)
                .set(
                    mapOf(
                        "name" to branch.name,
                        "location" to mapOf("lat" to branch.latitude, "lng" to branch.longitude),
                        "address" to branch.address,
                    )
                )
                .await()
        }

        services.forEach { service ->
            firestore.collection(FirestoreCollections.SERVICES)
                .document(service.id)
                .set(
                    mapOf(
                        "category" to service.category.name,
                        "name" to service.name,
                        "description" to service.description,
                        "basePrice" to service.basePrice,
                    )
                )
                .await()
        }
    }
}
