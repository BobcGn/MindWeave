package org.example.mindweave.sync

import kotlinx.serialization.Serializable
import org.example.mindweave.domain.model.EntityType

open class SyncApiException(
    val code: String,
    message: String,
) : IllegalStateException(message)

class DeviceNotRegisteredSyncException(
    message: String,
) : SyncApiException(code = "DEVICE_NOT_REGISTERED", message = message)

class InvalidSyncRequestSyncException(
    message: String,
) : SyncApiException(code = "INVALID_SYNC_REQUEST", message = message)

class SyncConflictApiException(
    val conflicts: List<SyncConflictSummary>,
    message: String,
) : SyncApiException(code = "SYNC_CONFLICT", message = message)

@Serializable
data class SyncConflictSummary(
    val entityType: EntityType,
    val entityId: String,
    val localVersion: Long? = null,
    val remoteVersion: Long? = null,
)

@Serializable
internal data class SyncApiErrorResponse(
    val code: String,
    val message: String,
    val conflicts: List<SyncConflictSummary> = emptyList(),
)
