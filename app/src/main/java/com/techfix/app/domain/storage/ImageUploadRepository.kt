package com.techfix.app.domain.storage

import java.io.File

/**
 * Hides that repair images live in Supabase Storage, not Firebase (decision
 * of 2026-08-21, see CLAUDE.md), so ViewModels never call the Supabase
 * client directly.
 */
interface ImageUploadRepository {
    /** Uploads an already-compressed local file and returns its public URL. */
    suspend fun uploadRepairImage(ownerId: String, file: File): Result<String>

    /** Uploads an immutable profile photo using the existing owner-folder convention. */
    suspend fun uploadProfileImage(ownerId: String, file: File): Result<String>

    /** Best-effort cleanup when a user removes a photo before submitting. */
    suspend fun deleteRepairImage(publicUrl: String): Result<Unit>
}
