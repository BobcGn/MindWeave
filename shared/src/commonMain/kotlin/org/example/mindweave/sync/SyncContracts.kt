package org.example.mindweave.sync

import kotlinx.serialization.Serializable
import org.example.mindweave.domain.ai.AiChatRequest
import org.example.mindweave.domain.ai.AiChatResponse
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.SyncOperation

@Serializable
data class DeviceRegistrationRequest(
    val userId: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class DeviceRegistrationResponse(
    val acknowledged: Boolean,
    val issuedAtEpochMs: Long,
)

@Serializable
data class ChangeEnvelope(
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMs: Long,
    val deviceId: String,
)

@Serializable
data class RemoteChangeEnvelope(
    val seq: Long,
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val createdAtEpochMs: Long,
    val deviceId: String,
)

@Serializable
data class SyncPushRequest(
    val userId: String,
    val deviceId: String,
    val changes: List<ChangeEnvelope>,
)

@Serializable
data class SyncPushResponse(
    val acceptedCount: Int,
    val latestSeq: Long,
)

@Serializable
data class SyncPullRequest(
    val userId: String,
    val deviceId: String,
    val afterSeq: Long,
)

@Serializable
data class SyncPullResponse(
    val changes: List<RemoteChangeEnvelope>,
    val latestSeq: Long,
)

typealias RemoteAiChatRequest = AiChatRequest
typealias RemoteAiChatResponse = AiChatResponse
