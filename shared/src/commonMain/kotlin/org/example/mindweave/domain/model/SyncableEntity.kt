package org.example.mindweave.domain.model

import kotlinx.serialization.Serializable

interface SyncableEntity {
    val id: String
    val userId: String
    val createdAtEpochMs: Long
    val updatedAtEpochMs: Long
    val deletedAtEpochMs: Long?
    val version: Long
    val lastModifiedByDeviceId: String
}

@Serializable
data class AppSession(
    val userId: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
enum class EntityType {
    DIARY_ENTRY,
    SCHEDULE_EVENT,
    TAG,
    DIARY_ENTRY_TAG,
    CHAT_SESSION,
    CHAT_MESSAGE,
}

@Serializable
enum class SyncOperation {
    UPSERT,
    SOFT_DELETE,
}

@Serializable
enum class OutboxStatus {
    PENDING,
    FAILED,
    SYNCED,
}

@Serializable
enum class ConflictStatus {
    DETECTED,
    RESOLVED_KEEP_LOCAL,
    RESOLVED_KEEP_REMOTE,
}
