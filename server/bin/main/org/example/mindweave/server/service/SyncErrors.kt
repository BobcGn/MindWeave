package org.example.mindweave.server.service

import kotlinx.serialization.Serializable
import org.example.mindweave.domain.model.EntityType

open class SyncServiceException(message: String) : IllegalArgumentException(message)

class InvalidSyncRequestException(message: String) : SyncServiceException(message)

class DeviceNotRegisteredException(userId: String, deviceId: String) :
    SyncServiceException("Device '$deviceId' is not registered for user '$userId'.")

class SyncConflictException(
    val conflicts: List<SyncConflictSummary>,
) : SyncServiceException("Incoming changes conflict with newer server snapshots.")

@Serializable
data class SyncConflictSummary(
    val entityType: EntityType,
    val entityId: String,
    val localVersion: Long?,
    val remoteVersion: Long?,
)

@Serializable
data class ServerErrorResponse(
    val code: String,
    val message: String,
    val conflicts: List<SyncConflictSummary> = emptyList(),
)
