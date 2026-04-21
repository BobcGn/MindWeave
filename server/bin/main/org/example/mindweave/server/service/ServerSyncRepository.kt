package org.example.mindweave.server.service

import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.SyncOperation

interface ServerSyncRepository {
    fun upsertUser(record: ServerUserRecord)
    fun findUser(userId: String): ServerUserRecord?
    fun upsertDevice(record: RegisteredDeviceRecord)
    fun findDevice(userId: String, deviceId: String): RegisteredDeviceRecord?
    fun findEntitySnapshot(userId: String, entityType: EntityType, entityId: String): ServerEntitySnapshot?
    fun saveEntitySnapshot(snapshot: ServerEntitySnapshot)
    fun hasDedupeKey(userId: String, dedupeKey: String): Boolean
    fun saveDedupeKey(userId: String, dedupeKey: String)
    fun appendChange(record: PendingChangeLogRecord): ServerChangeLogRecord
    fun listChangesAfter(userId: String, afterSeq: Long, excludedDeviceId: String): List<ServerChangeLogRecord>
    fun latestSeq(userId: String): Long
    fun getDeviceSyncState(userId: String, deviceId: String): DeviceSyncStateRecord?
    fun updateDeviceSyncState(record: DeviceSyncStateRecord)
    fun saveConflict(record: ServerSyncConflictRecord)
    fun <T> inTransaction(block: () -> T): T
}

data class ServerUserRecord(
    val id: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class RegisteredDeviceRecord(
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val registeredAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
)

data class ServerEntitySnapshot(
    val userId: String,
    val entityType: EntityType,
    val entityId: String,
    val payload: String,
    val version: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val lastModifiedByDeviceId: String,
)

data class PendingChangeLogRecord(
    val userId: String,
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMs: Long,
    val deviceId: String,
    val version: Long,
    val updatedAtEpochMs: Long,
    val dedupeKey: String,
)

data class ServerChangeLogRecord(
    val seq: Long,
    val userId: String,
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMs: Long,
    val deviceId: String,
    val version: Long,
    val updatedAtEpochMs: Long,
    val dedupeKey: String,
)

data class DeviceSyncStateRecord(
    val userId: String,
    val deviceId: String,
    val lastPulledSeq: Long,
    val lastPushAtEpochMs: Long?,
    val lastPullAtEpochMs: Long?,
)

data class ServerSyncConflictRecord(
    val id: String,
    val userId: String,
    val entityType: EntityType,
    val entityId: String,
    val localPayload: String,
    val remotePayload: String,
    val status: String,
    val localVersion: Long?,
    val remoteVersion: Long?,
    val createdAtEpochMs: Long,
)
