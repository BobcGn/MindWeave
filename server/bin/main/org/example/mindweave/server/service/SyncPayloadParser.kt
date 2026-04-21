package org.example.mindweave.server.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.sync.ChangeEnvelope
import org.example.mindweave.util.MindWeaveJson

class SyncPayloadParser(
    private val json: Json = MindWeaveJson,
) {
    fun parse(userId: String, requestDeviceId: String, change: ChangeEnvelope): ParsedSyncChange {
        if (change.deviceId != requestDeviceId) {
            throw InvalidSyncRequestException("Change deviceId must match request deviceId.")
        }
        if (change.createdAtEpochMs <= 0) {
            throw InvalidSyncRequestException("Change createdAtEpochMs must be positive.")
        }

        val payload = runCatching { json.parseToJsonElement(change.payload).jsonObject }
            .getOrElse {
                throw InvalidSyncRequestException(
                    "Change payload must be a JSON object for ${change.entityType.name}/${change.entityId}.",
                )
            }

        val payloadUserId = payload.requiredString("userId")
        val payloadId = payload.requiredString("id")
        val version = payload.requiredLong("version")
        val createdAtEpochMs = payload.requiredLong("createdAtEpochMs")
        val updatedAtEpochMs = payload.requiredLong("updatedAtEpochMs")
        val deletedAtEpochMs = payload.optionalLong("deletedAtEpochMs")
        val lastModifiedByDeviceId = payload.requiredString("lastModifiedByDeviceId")

        if (payloadUserId != userId) {
            throw InvalidSyncRequestException("Payload userId does not match the push request.")
        }
        if (payloadId != change.entityId) {
            throw InvalidSyncRequestException("Payload id does not match entityId.")
        }
        if (lastModifiedByDeviceId != requestDeviceId) {
            throw InvalidSyncRequestException("Payload lastModifiedByDeviceId must match the request device.")
        }
        if (version < 1) {
            throw InvalidSyncRequestException("Payload version must start from 1.")
        }
        if (updatedAtEpochMs < createdAtEpochMs) {
            throw InvalidSyncRequestException("Payload updatedAtEpochMs must be greater than or equal to createdAtEpochMs.")
        }
        if (change.operation == SyncOperation.SOFT_DELETE && deletedAtEpochMs == null) {
            throw InvalidSyncRequestException("SOFT_DELETE payload must carry deletedAtEpochMs.")
        }
        if (change.operation == SyncOperation.UPSERT && deletedAtEpochMs != null) {
            throw InvalidSyncRequestException("UPSERT payload must not carry deletedAtEpochMs.")
        }

        return ParsedSyncChange(
            userId = userId,
            entityType = change.entityType,
            entityId = change.entityId,
            operation = change.operation,
            payload = change.payload,
            changeCreatedAtEpochMs = change.createdAtEpochMs,
            deviceId = requestDeviceId,
            version = version,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            lastModifiedByDeviceId = lastModifiedByDeviceId,
        )
    }
}

data class ParsedSyncChange(
    val userId: String,
    val entityType: EntityType,
    val entityId: String,
    val operation: SyncOperation,
    val payload: String,
    val changeCreatedAtEpochMs: Long,
    val deviceId: String,
    val version: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val lastModifiedByDeviceId: String,
) {
    fun dedupeKey(): String = listOf(
        userId,
        deviceId,
        entityType.name,
        entityId,
        operation.name,
        version.toString(),
        updatedAtEpochMs.toString(),
        payload,
    ).joinToString("|")

    fun toSnapshot(): ServerEntitySnapshot = ServerEntitySnapshot(
        userId = userId,
        entityType = entityType,
        entityId = entityId,
        payload = payload,
        version = version,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )

    fun toPendingChangeLogRecord(): PendingChangeLogRecord = PendingChangeLogRecord(
        userId = userId,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        createdAtEpochMs = changeCreatedAtEpochMs,
        deviceId = deviceId,
        version = version,
        updatedAtEpochMs = updatedAtEpochMs,
        dedupeKey = dedupeKey(),
    )
}

private fun kotlinx.serialization.json.JsonObject.requiredString(name: String): String {
    val value = this[name]?.jsonPrimitive?.contentOrNull?.trim()
    if (value.isNullOrEmpty()) {
        throw InvalidSyncRequestException("Payload field '$name' is required.")
    }
    return value
}

private fun kotlinx.serialization.json.JsonObject.requiredLong(name: String): Long {
    val primitive = this[name]?.jsonPrimitive
        ?: throw InvalidSyncRequestException("Payload field '$name' is required.")
    return primitive.longOrNull
        ?: throw InvalidSyncRequestException("Payload field '$name' must be a long value.")
}

private fun kotlinx.serialization.json.JsonObject.optionalLong(name: String): Long? {
    val element = this[name] ?: return null
    if (element is JsonNull) {
        return null
    }
    return element.jsonPrimitive.longOrNull
        ?: throw InvalidSyncRequestException("Payload field '$name' must be a long value when present.")
}
