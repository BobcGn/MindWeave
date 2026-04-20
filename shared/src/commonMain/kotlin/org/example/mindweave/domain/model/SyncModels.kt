package org.example.mindweave.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OutboxChange(
    val id: String,
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMs: Long,
    val retryCount: Long,
    val status: OutboxStatus,
)

@Serializable
data class SyncConflictRecord(
    val id: String,
    val entityType: EntityType,
    val entityId: String,
    val localPayload: String,
    val remotePayload: String,
    val status: ConflictStatus,
    val createdAtEpochMs: Long,
)

@Serializable
data class SyncState(
    val pendingChanges: Long,
    val lastSyncSeq: Long,
)
