package com.techfix.app.core.data.storage

/** Object names compatible with the live one-folder Supabase insert policy. */
internal object StorageObjectPath {
    fun repairImage(ownerId: String, objectId: String): String = jpeg(ownerId, objectId)

    fun profileImage(ownerId: String, objectId: String): String = jpeg(ownerId, "profile_$objectId")

    private fun jpeg(ownerId: String, fileName: String): String {
        require(ownerId.isNotBlank() && '/' !in ownerId) { "Storage owner ID is invalid" }
        require(fileName.isNotBlank() && '/' !in fileName) { "Storage object ID is invalid" }
        return "$ownerId/$fileName.jpg"
    }
}
