package org.example.mindweave.server.service

import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.DeviceRegistrationResponse
import org.example.mindweave.sync.RemoteChangeEnvelope
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPullResponse
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.sync.SyncPushResponse
import org.example.mindweave.util.IdGenerator
import org.example.mindweave.util.currentEpochMillis

class InMemorySyncService(
    private val repository: ServerSyncRepository = InMemoryServerSyncRepository(),
    private val payloadParser: SyncPayloadParser = SyncPayloadParser(),
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ServerSyncService {
    override fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse {
        validateIdentifier("userId", request.userId)
        validateIdentifier("deviceId", request.deviceId)
        validateIdentifier("deviceName", request.deviceName)

        val now = nowProvider()
        repository.inTransaction {
            val existingUser = repository.findUser(request.userId)
            repository.upsertUser(
                ServerUserRecord(
                    id = request.userId,
                    createdAtEpochMs = existingUser?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                ),
            )

            val existingDevice = repository.findDevice(request.userId, request.deviceId)
            repository.upsertDevice(
                RegisteredDeviceRecord(
                    userId = request.userId,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    registeredAtEpochMs = existingDevice?.registeredAtEpochMs ?: now,
                    lastSeenAtEpochMs = now,
                ),
            )

            repository.updateDeviceSyncState(
                DeviceSyncStateRecord(
                    userId = request.userId,
                    deviceId = request.deviceId,
                    lastPulledSeq = repository.getDeviceSyncState(request.userId, request.deviceId)?.lastPulledSeq ?: 0L,
                    lastPushAtEpochMs = repository.getDeviceSyncState(request.userId, request.deviceId)?.lastPushAtEpochMs,
                    lastPullAtEpochMs = repository.getDeviceSyncState(request.userId, request.deviceId)?.lastPullAtEpochMs,
                ),
            )
        }
        return DeviceRegistrationResponse(acknowledged = true, issuedAtEpochMs = now)
    }

    override fun push(request: SyncPushRequest): SyncPushResponse {
        validateIdentifier("userId", request.userId)
        validateIdentifier("deviceId", request.deviceId)

        val now = nowProvider()
        return repository.inTransaction {
            val device = requireRegisteredDevice(request.userId, request.deviceId)
            val plannedChanges = mutableListOf<ParsedSyncChange>()
            val batchDedupeKeys = linkedSetOf<String>()
            val workingSnapshots = linkedMapOf<EntityKey, ServerEntitySnapshot?>()
            val conflicts = mutableListOf<ServerConflictCandidate>()

            request.changes.forEach { change ->
                val parsed = payloadParser.parse(
                    userId = request.userId,
                    requestDeviceId = request.deviceId,
                    change = change,
                )
                val dedupeKey = parsed.dedupeKey()
                if (!batchDedupeKeys.add(dedupeKey)) {
                    return@forEach
                }
                if (repository.hasDedupeKey(request.userId, dedupeKey)) {
                    return@forEach
                }

                val entityKey = EntityKey(parsed.entityType, parsed.entityId)
                val currentSnapshot = if (workingSnapshots.containsKey(entityKey)) {
                    workingSnapshots[entityKey]
                } else {
                    repository.findEntitySnapshot(
                        userId = request.userId,
                        entityType = parsed.entityType,
                        entityId = parsed.entityId,
                    ).also { workingSnapshots[entityKey] = it }
                }

                when {
                    currentSnapshot == null -> {
                        plannedChanges += parsed
                        workingSnapshots[entityKey] = parsed.toSnapshot()
                    }

                    isEquivalent(currentSnapshot, parsed) -> {
                        repository.saveDedupeKey(request.userId, dedupeKey)
                    }

                    compareIncomingToCurrent(parsed, currentSnapshot) > 0 -> {
                        plannedChanges += parsed
                        workingSnapshots[entityKey] = parsed.toSnapshot()
                    }

                    else -> {
                        conflicts += ServerConflictCandidate(
                            current = currentSnapshot,
                            incoming = parsed,
                        )
                    }
                }
            }

            if (conflicts.isNotEmpty()) {
                conflicts.forEach { conflict ->
                    repository.saveConflict(
                        ServerSyncConflictRecord(
                            id = IdGenerator.next("server-conflict", now),
                            userId = request.userId,
                            entityType = conflict.current.entityType,
                            entityId = conflict.current.entityId,
                            localPayload = conflict.current.payload,
                            remotePayload = conflict.incoming.payload,
                            status = "DETECTED",
                            localVersion = conflict.current.version,
                            remoteVersion = conflict.incoming.version,
                            createdAtEpochMs = now,
                        ),
                    )
                }
                throw SyncConflictException(
                    conflicts = conflicts.map {
                        SyncConflictSummary(
                            entityType = it.current.entityType,
                            entityId = it.current.entityId,
                            localVersion = it.current.version,
                            remoteVersion = it.incoming.version,
                        )
                    },
                )
            }

            val changesToPersist = orderForPersistence(plannedChanges)
            var latestSeq = repository.latestSeq(request.userId)
            changesToPersist.forEach { change ->
                repository.saveEntitySnapshot(change.toSnapshot())
                repository.saveDedupeKey(request.userId, change.dedupeKey())
                latestSeq = repository.appendChange(change.toPendingChangeLogRecord()).seq
            }

            repository.upsertDevice(device.copy(lastSeenAtEpochMs = now))
            val existingState = repository.getDeviceSyncState(request.userId, request.deviceId)
            repository.updateDeviceSyncState(
                DeviceSyncStateRecord(
                    userId = request.userId,
                    deviceId = request.deviceId,
                    lastPulledSeq = existingState?.lastPulledSeq ?: 0L,
                    lastPushAtEpochMs = now,
                    lastPullAtEpochMs = existingState?.lastPullAtEpochMs,
                ),
            )

            SyncPushResponse(
                acceptedCount = changesToPersist.size,
                latestSeq = latestSeq,
            )
        }
    }

    override fun pull(request: SyncPullRequest): SyncPullResponse {
        validateIdentifier("userId", request.userId)
        validateIdentifier("deviceId", request.deviceId)
        if (request.afterSeq < 0) {
            throw InvalidSyncRequestException("afterSeq must be greater than or equal to 0.")
        }

        val now = nowProvider()
        return repository.inTransaction {
            val device = requireRegisteredDevice(request.userId, request.deviceId)
            val changes = repository.listChangesAfter(
                userId = request.userId,
                afterSeq = request.afterSeq,
                excludedDeviceId = request.deviceId,
            ).map { stored ->
                RemoteChangeEnvelope(
                    seq = stored.seq,
                    entityType = stored.entityType,
                    entityId = stored.entityId,
                    operation = stored.operation,
                    payload = stored.payload,
                    createdAtEpochMs = stored.createdAtEpochMs,
                    deviceId = stored.deviceId,
                )
            }
            val latestSeq = repository.latestSeq(request.userId)

            repository.upsertDevice(device.copy(lastSeenAtEpochMs = now))
            val existingState = repository.getDeviceSyncState(request.userId, request.deviceId)
            repository.updateDeviceSyncState(
                DeviceSyncStateRecord(
                    userId = request.userId,
                    deviceId = request.deviceId,
                    lastPulledSeq = maxOf(existingState?.lastPulledSeq ?: 0L, latestSeq),
                    lastPushAtEpochMs = existingState?.lastPushAtEpochMs,
                    lastPullAtEpochMs = now,
                ),
            )

            SyncPullResponse(
                changes = changes,
                latestSeq = latestSeq,
            )
        }
    }

    private fun validateIdentifier(label: String, value: String) {
        if (value.isBlank()) {
            throw InvalidSyncRequestException("$label must not be blank.")
        }
    }

    private fun requireRegisteredDevice(userId: String, deviceId: String): RegisteredDeviceRecord =
        repository.findDevice(userId, deviceId) ?: throw DeviceNotRegisteredException(userId, deviceId)

    private fun isEquivalent(current: ServerEntitySnapshot, incoming: ParsedSyncChange): Boolean =
        current.version == incoming.version &&
            current.updatedAtEpochMs == incoming.updatedAtEpochMs &&
            current.deletedAtEpochMs == incoming.deletedAtEpochMs &&
            current.lastModifiedByDeviceId == incoming.lastModifiedByDeviceId &&
            current.payload == incoming.payload

    private fun orderForPersistence(changes: List<ParsedSyncChange>): List<ParsedSyncChange> =
        changes.withIndex()
            .sortedWith(compareBy<IndexedValue<ParsedSyncChange>> { entityPersistenceRank(it.value.entityType) }.thenBy { it.index })
            .map { it.value }

    // Initial conflict strategy follows the spec: LWW by version, then updatedAt, then deviceId.
    private fun compareIncomingToCurrent(incoming: ParsedSyncChange, current: ServerEntitySnapshot): Int {
        if (incoming.version != current.version) {
            return incoming.version.compareTo(current.version)
        }
        if (incoming.updatedAtEpochMs != current.updatedAtEpochMs) {
            return incoming.updatedAtEpochMs.compareTo(current.updatedAtEpochMs)
        }
        return incoming.lastModifiedByDeviceId.compareTo(current.lastModifiedByDeviceId)
    }

    private fun entityPersistenceRank(entityType: EntityType): Int = when (entityType) {
        EntityType.TAG -> 0
        EntityType.DIARY_ENTRY -> 1
        EntityType.SCHEDULE_EVENT -> 2
        EntityType.CHAT_SESSION -> 3
        EntityType.CHAT_MESSAGE -> 4
        EntityType.DIARY_ENTRY_TAG -> 5
    }

    private data class EntityKey(
        val entityType: EntityType,
        val entityId: String,
    )

    private data class ServerConflictCandidate(
        val current: ServerEntitySnapshot,
        val incoming: ParsedSyncChange,
    )
}
