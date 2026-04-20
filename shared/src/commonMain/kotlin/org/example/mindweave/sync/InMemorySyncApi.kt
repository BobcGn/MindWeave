package org.example.mindweave.sync

import org.example.mindweave.util.currentEpochMillis

class InMemorySyncApi(
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : SyncApi {
    private val devices = linkedSetOf<String>()
    private val changeLog = mutableListOf<RemoteChangeEnvelope>()
    private var seqCounter = 0L
    private val fingerprints = linkedSetOf<String>()

    override suspend fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse {
        devices += "${request.userId}:${request.deviceId}:${request.deviceName}"
        return DeviceRegistrationResponse(
            acknowledged = true,
            issuedAtEpochMs = nowProvider(),
        )
    }

    override suspend fun push(request: SyncPushRequest): SyncPushResponse {
        request.changes.forEach { change ->
            val fingerprint = listOf(
                request.userId,
                request.deviceId,
                change.entityType.name,
                change.entityId,
                change.operation.name,
                change.payload,
            ).joinToString("|")
            if (fingerprints.add(fingerprint)) {
                seqCounter += 1
                changeLog += RemoteChangeEnvelope(
                    seq = seqCounter,
                    entityType = change.entityType,
                    entityId = change.entityId,
                    operation = change.operation,
                    payload = change.payload,
                    createdAtEpochMs = change.createdAtEpochMs,
                    deviceId = change.deviceId,
                )
            }
        }
        return SyncPushResponse(
            acceptedCount = request.changes.size,
            latestSeq = seqCounter,
        )
    }

    override suspend fun pull(request: SyncPullRequest): SyncPullResponse {
        val changes = changeLog.filter { it.seq > request.afterSeq && it.deviceId != request.deviceId }
        return SyncPullResponse(
            changes = changes,
            latestSeq = seqCounter,
        )
    }
}
