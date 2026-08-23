package com.techfix.app.core.data.draft

import com.techfix.app.core.data.local.DraftRepairRequestDao
import com.techfix.app.core.data.local.DraftRepairRequestEntity
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.draft.DraftImage
import com.techfix.app.domain.draft.DraftRepairRequest
import com.techfix.app.domain.draft.DraftRepairRequestRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Room-backed store for the one in-progress booking draft.
 *
 * Every method swallows storage failures rather than propagating them: a
 * draft that cannot be written is a lost convenience, not a reason to break
 * the booking the customer is in the middle of.
 */
class RoomDraftRepairRequestRepository(
    private val dao: DraftRepairRequestDao,
) : DraftRepairRequestRepository {

    @Serializable
    private data class StoredImage(val id: String, val remoteUrl: String)

    override suspend fun load(customerId: String, serviceId: String): DraftRepairRequest? {
        val entity = runCatching { dao.get() }.getOrNull() ?: return null
        if (entity.customerId != customerId || entity.serviceId != serviceId) return null
        return DraftRepairRequest(
            customerId = entity.customerId,
            serviceId = entity.serviceId,
            step = entity.step,
            category = DeviceCategory.fromRaw(entity.category),
            brand = entity.brand,
            model = entity.model,
            serialNumber = entity.serialNumber,
            issueDescription = entity.issueDescription,
            images = decodeImages(entity.imagesJson),
            selectedBranchId = entity.selectedBranchId,
            unsavedImageCount = entity.unsavedImageCount,
            scheduledAt = entity.scheduledAt,
        )
    }

    override suspend fun save(draft: DraftRepairRequest) {
        runCatching {
            dao.upsert(
                DraftRepairRequestEntity(
                    customerId = draft.customerId,
                    serviceId = draft.serviceId,
                    step = draft.step,
                    category = draft.category?.name,
                    brand = draft.brand,
                    model = draft.model,
                    serialNumber = draft.serialNumber,
                    issueDescription = draft.issueDescription,
                    imagesJson = encodeImages(draft.images),
                    unsavedImageCount = draft.unsavedImageCount,
                    selectedBranchId = draft.selectedBranchId,
                    scheduledAt = draft.scheduledAt,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun clear() {
        runCatching { dao.clear() }
    }

    private fun encodeImages(images: List<DraftImage>): String =
        json.encodeToString(
            ListSerializer(StoredImage.serializer()),
            images.map { StoredImage(it.id, it.remoteUrl) },
        )

    private fun decodeImages(raw: String): List<DraftImage> =
        runCatching { json.decodeFromString(ListSerializer(StoredImage.serializer()), raw) }
            .getOrDefault(emptyList())
            .map { DraftImage(it.id, it.remoteUrl) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
