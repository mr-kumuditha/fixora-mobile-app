package com.techfix.app.core.data.technician

import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.technician.Technician
import org.junit.Assert.assertThrows
import org.junit.Test

class FirestoreTechnicianPersistenceTest {

    private val persisted = Technician(
        id = "3a1d1153-4a6a-4e70-b5be-d2ca9135b502",
        name = "Dilshan Fernando",
        branchId = "colombo",
        categorySkills = listOf(DeviceCategory.LAPTOP, DeviceCategory.DESKTOP),
        available = false,
    )

    @Test
    fun `matching server document confirms available to unavailable update`() {
        requirePersistedFirestoreTechnician(
            actual = persisted,
            expected = persisted.copy(
                name = " Dilshan Fernando ",
                categorySkills = listOf(DeviceCategory.DESKTOP, DeviceCategory.LAPTOP),
            ),
        )
    }

    @Test
    fun `available to unavailable mismatch is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            requirePersistedFirestoreTechnician(
                actual = persisted.copy(available = true),
                expected = persisted,
            )
        }
    }

    @Test
    fun `unavailable to available mismatch is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            requirePersistedFirestoreTechnician(
                actual = persisted.copy(available = false),
                expected = persisted.copy(available = true),
            )
        }
    }

    @Test
    fun `different stable id is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            requirePersistedFirestoreTechnician(
                actual = persisted,
                expected = persisted.copy(id = "ffab1d72-ab1b-4a17-a01a-4a7d01f61f45"),
            )
        }
    }

    @Test
    fun `changed name branch or skills are rejected`() {
        listOf(
            persisted.copy(name = "Different"),
            persisted.copy(branchId = "galle"),
            persisted.copy(categorySkills = listOf(DeviceCategory.MOBILE)),
        ).forEach { expected ->
            assertThrows(IllegalStateException::class.java) {
                requirePersistedFirestoreTechnician(actual = persisted, expected = expected)
            }
        }
    }
}
