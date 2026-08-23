package com.techfix.app.core.data.storage

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import com.techfix.app.core.data.SupabaseBuckets
import com.techfix.app.core.data.SupabaseClientProvider
import com.techfix.app.core.util.UploadDiagnostics
import com.techfix.app.domain.storage.ImageUploadRepository
import java.io.File
import java.util.UUID

/**
 * Uploads repair-request photos to the Supabase Storage `repair-images`
 * bucket (see docs/supabase/storage_setup.sql) and returns their public
 * URL, which is what gets stored on the Firestore repair request.
 */
class SupabaseImageUploadRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) : ImageUploadRepository {

    override suspend fun uploadRepairImage(ownerId: String, file: File): Result<String> =
        uploadJpeg(ownerId = ownerId, file = file, filePrefix = null)

    override suspend fun uploadProfileImage(ownerId: String, file: File): Result<String> =
        uploadJpeg(ownerId = ownerId, file = file, filePrefix = "profile")

    private suspend fun uploadJpeg(
        ownerId: String,
        file: File,
        filePrefix: String?,
    ): Result<String> {
        var path = "<not-generated>"
        val result = runCatching {
            require(ownerId.isNotBlank()) { "Authenticated Firebase user ID is missing" }
            require(file.isFile && file.canRead() && file.length() > 0L) {
                "Compressed upload file is empty or unreadable"
            }

            val bucketName = SupabaseBuckets.REPAIR_IMAGES
            val objectId = UUID.randomUUID().toString()
            path = if (filePrefix == null) {
                StorageObjectPath.repairImage(ownerId, objectId)
            } else {
                StorageObjectPath.profileImage(ownerId, objectId)
            }
            val bytes = file.readBytes()
            check(bytes.isNotEmpty()) { "Compressed upload payload is empty" }

            UploadDiagnostics.debug(
                "Upload prepared. supabaseUrl=${client.supabaseUrl} bucket=$bucketName path=$path " +
                    "firebaseUserId=$ownerId supabaseAuth=anon mimeType=image/jpeg " +
                    "compressedPath=${file.absolutePath} compressedSize=${file.length()} " +
                    "payloadSize=${bytes.size}",
            )
            UploadDiagnostics.debug("Upload started. bucket=$bucketName path=$path")

            val bucket = client.storage.from(bucketName)
            val response = bucket.upload(path, bytes) {
                upsert = false
                contentType = ContentType.Image.JPEG
            }
            UploadDiagnostics.debug(
                "Upload succeeded. bucket=$bucketName path=$path responseId=${response.id} " +
                    "responsePath=${response.path} responseKey=${response.key}",
            )
            bucket.publicUrl(path)
        }

        result.exceptionOrNull()?.let { exception ->
            logUploadFailure(path, exception)
        }
        return result
    }

    override suspend fun deleteRepairImage(publicUrl: String): Result<Unit> = runCatching {
        val bucket = client.storage.from(SupabaseBuckets.REPAIR_IMAGES)
        val path = publicUrl.substringAfter("${SupabaseBuckets.REPAIR_IMAGES}/")
        bucket.delete(listOf(path))
    }

    private suspend fun logUploadFailure(path: String, exception: Throwable) {
        UploadDiagnostics.error(
            "Photo upload failed. bucket=${SupabaseBuckets.REPAIR_IMAGES} path=$path",
            exception,
        )
        when (exception) {
            is RestException -> UploadDiagnostics.debug(
                "Supabase failure response. httpStatus=${exception.statusCode} " +
                    "error=${exception.error} responseBody=${exception.description}",
            )

            is ResponseException -> {
                val body = runCatching { exception.response.bodyAsText() }
                    .getOrElse { "<response body unavailable: ${it.message}>" }
                UploadDiagnostics.debug(
                    "HTTP failure response. httpStatus=${exception.response.status.value} " +
                        "responseBody=$body",
                )
            }
        }
    }
}
