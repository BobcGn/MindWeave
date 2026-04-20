package org.example.mindweave.sync

import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.repository.SyncRepository

data class SyncRunResult(
    val pushed: Int,
    val pulled: Int,
    val latestSeq: Long,
)

class SyncManager(
    private val syncApi: SyncApi,
    private val syncRepository: SyncRepository,
    private val localChangeApplier: LocalChangeApplier,
) {
    suspend fun synchronize(session: AppSession): SyncRunResult {
        syncApi.registerDevice(
            DeviceRegistrationRequest(
                userId = session.userId,
                deviceId = session.deviceId,
                deviceName = session.deviceName,
            ),
        )

        val pending = syncRepository.pendingChanges()
        if (pending.isNotEmpty()) {
            runCatching {
                syncApi.push(
                    SyncPushRequest(
                        userId = session.userId,
                        deviceId = session.deviceId,
                        changes = pending.map {
                            ChangeEnvelope(
                                entityType = it.entityType,
                                entityId = it.entityId,
                                operation = it.operation,
                                payload = it.payload,
                                createdAtEpochMs = it.createdAtEpochMs,
                                deviceId = session.deviceId,
                            )
                        },
                    ),
                )
            }.onSuccess {
                pending.forEach { syncRepository.markSynced(it.id) }
            }.onFailure {
                pending.forEach { change ->
                    syncRepository.markFailed(change.id, change.retryCount + 1)
                }
                throw it
            }
        }

        val lastSeq = syncRepository.getLastSyncSeq()
        val pulled = syncApi.pull(
            SyncPullRequest(
                userId = session.userId,
                deviceId = session.deviceId,
                afterSeq = lastSeq,
            ),
        )

        pulled.changes.forEach { localChangeApplier.apply(it) }
        syncRepository.setLastSyncSeq(pulled.latestSeq)

        return SyncRunResult(
            pushed = pending.size,
            pulled = pulled.changes.size,
            latestSeq = pulled.latestSeq,
        )
    }
}
