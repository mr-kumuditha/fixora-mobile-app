package com.techfix.app.core.data.inventory

import com.google.firebase.auth.FirebaseAuth
import com.techfix.app.BuildConfig
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.inventory.AdminInventoryItem
import com.techfix.app.domain.inventory.AdminInventoryRepository
import com.techfix.app.domain.inventory.AdminInventorySnapshot
import com.techfix.app.domain.inventory.InventoryAdjustment
import com.techfix.app.domain.inventory.InventoryAuthorizationException
import com.techfix.app.domain.inventory.InventoryBranchStock
import com.techfix.app.domain.inventory.InventoryItemDraft
import com.techfix.app.domain.inventory.InventoryServiceException
import com.techfix.app.domain.inventory.StockAdjustmentDraft
import com.techfix.app.domain.inventory.StockAdjustmentType
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.math.BigDecimal
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Admin inventory mutations go only through the inventory-admin Edge Function.
 * The function verifies this Firebase ID token and the Firestore ADMIN role
 * before it receives a Supabase service-role client. No privileged credential
 * exists in this APK.
 */
class SupabaseAdminInventoryRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val httpClient: HttpClient = HttpClient(Android),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AdminInventoryRepository {

    override suspend fun getInventory(): Result<AdminInventorySnapshot> = runCatching {
        val response = invoke(action = "list")
        val items = response.array("items").map { it.jsonObject.toItem() }
        val adjustments = response.array("recentAdjustments").map { it.jsonObject.toAdjustment() }
        AdminInventorySnapshot(items, adjustments)
    }

    override suspend fun createItem(requestId: String, draft: InventoryItemDraft): Result<Unit> =
        runCatching {
            invoke("create_item", draft.toJson(), requestId)
            Unit
        }

    override suspend fun updateItem(itemId: String, draft: InventoryItemDraft): Result<Unit> =
        runCatching {
            invoke(
                "update_item",
                JsonObject(draft.toJson() + ("itemId" to JsonPrimitive(itemId))),
            )
            Unit
        }

    override suspend fun setItemAvailability(itemId: String, isAvailable: Boolean): Result<Unit> =
        runCatching {
            invoke(
                "set_availability",
                buildJsonObject {
                    put("itemId", itemId)
                    put("isAvailable", isAvailable)
                },
            )
            Unit
        }

    override suspend fun adjustStock(requestId: String, draft: StockAdjustmentDraft): Result<Unit> =
        runCatching {
            invoke(
                "adjust_stock",
                buildJsonObject {
                    put("itemId", draft.itemId)
                    put("branchId", draft.branchId)
                    put("adjustmentType", draft.type.name)
                    put("quantity", requireNotNull(draft.quantity))
                    put("reason", draft.reason.trim())
                },
                requestId,
            )
            Unit
        }

    private suspend fun invoke(
        action: String,
        payload: JsonObject = JsonObject(emptyMap()),
        requestId: String? = null,
    ): JsonObject {
        val user = firebaseAuth.currentUser ?: throw InventoryAuthorizationException()
        val token = user.getIdToken(false).await().token ?: throw InventoryAuthorizationException()
        val requestBody = buildJsonObject {
            put("action", action)
            put("payload", payload)
            requestId?.let { put("requestId", it) }
        }
        val response = httpClient.post("${BuildConfig.SUPABASE_URL}/functions/v1/inventory-admin") {
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }
        val responseText = response.bodyAsText()
        val parsed = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrElse {
            throw InventoryServiceException("Unable to reach inventory services. Please try again.")
        }
        when (response.status.value) {
            in 200..299 -> return parsed
            401, 403 -> throw InventoryAuthorizationException()
            else -> throw InventoryServiceException(
                parsed.string("message").takeIf { it.isNotBlank() }
                    ?: "Unable to update inventory. Please try again."
            )
        }
    }

    private fun InventoryItemDraft.toJson() = buildJsonObject {
        put("name", name.trim())
        put("category", category.trim())
        put("description", description.trim())
        put("sku", sku.trim())
        put("compatibleCategories", buildJsonArray {
            compatibleCategories.sortedBy(DeviceCategory::name).forEach { add(JsonPrimitive(it.name)) }
        })
        put("minimumStockLevel", requireNotNull(minimumStockLevel))
        put("unitCost", unitCost?.let { JsonPrimitive(it.toDouble()) } ?: JsonNull)
        put("sellingPrice", sellingPrice?.let { JsonPrimitive(it.toDouble()) } ?: JsonNull)
        put("supplierName", supplierName.trim())
        put("supplierContact", supplierContact.trim())
        put("isAvailable", isAvailable)
    }

    private fun JsonObject.toItem() = AdminInventoryItem(
        id = string("id"),
        name = string("name"),
        category = string("category"),
        description = nullableString("description"),
        sku = nullableString("sku"),
        compatibleCategories = array("compatibleCategories").mapNotNull {
            DeviceCategory.fromRaw(it.jsonPrimitive.contentOrNull.orEmpty())
        },
        minimumStockLevel = int("minimumStockLevel"),
        unitCost = decimalOrNull("unitCost"),
        sellingPrice = decimalOrNull("sellingPrice"),
        supplierName = nullableString("supplierName"),
        supplierContact = nullableString("supplierContact"),
        isAvailable = boolean("isAvailable", true),
        createdAt = nullableString("createdAt"),
        updatedAt = nullableString("updatedAt"),
        archivedAt = nullableString("archivedAt"),
        stocks = array("stocks").map { stock ->
            val value = stock.jsonObject
            InventoryBranchStock(
                branchId = value.string("branchId"),
                quantity = value.int("quantity"),
                updatedAt = value.nullableString("updatedAt"),
            )
        },
    )

    private fun JsonObject.toAdjustment() = InventoryAdjustment(
        id = string("id"),
        requestId = string("requestId"),
        itemId = string("itemId"),
        itemName = string("itemName").ifBlank { "Inventory item" },
        branchId = string("branchId"),
        previousQuantity = int("previousQuantity"),
        newQuantity = int("newQuantity"),
        type = runCatching { StockAdjustmentType.valueOf(string("adjustmentType")) }
            .getOrDefault(StockAdjustmentType.CORRECT),
        reason = string("reason"),
        performedByUid = string("performedByUid"),
        performedByEmail = nullableString("performedByEmail"),
        createdAt = nullableString("createdAt"),
    )

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.nullableString(key: String) = string(key).takeIf(String::isNotBlank)
    private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0
    private fun JsonObject.boolean(key: String, default: Boolean) =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
    private fun JsonObject.decimalOrNull(key: String): BigDecimal? =
        this[key]?.jsonPrimitive?.doubleOrNull?.let(BigDecimal::valueOf)
    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())
}
