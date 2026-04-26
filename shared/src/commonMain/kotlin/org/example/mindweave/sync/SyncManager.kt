package org.example.mindweave.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.OutboxChange
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
    private val synchronizeMutex = Mutex()

    suspend fun synchronize(session: AppSession): SyncRunResult =
        synchronizeMutex.withLock {
            syncApi.registerDevice(
                DeviceRegistrationRequest(
                    userId = session.userId,
                    deviceId = session.deviceId,
                    deviceName = session.deviceName,
                ),
            )

            val pending = syncRepository.pendingChanges()
            val lastSeq = syncRepository.getLastSyncSeq()
            val pushResult = pushPendingChanges(session, pending)
            pushResult.failure?.let { throw it }

            val pulled = syncApi.pull(
                SyncPullRequest(
                    userId = session.userId,
                    deviceId = session.deviceId,
                    afterSeq = lastSeq,
                ),
            )

            pulled.changes.forEach { localChangeApplier.apply(it) }
            syncRepository.setLastSyncSeq(pulled.latestSeq)

            SyncRunResult(
                pushed = pushResult.acceptedCount,
                pulled = pulled.changes.size,
                latestSeq = pulled.latestSeq,
            )
        }

    private suspend fun pushPendingChanges(
        session: AppSession,
        pending: List<OutboxChange>,
    ): PushResult {
        if (pending.isEmpty()) {
            return PushResult()
        }

        var acceptedCount = 0
        var failure: Throwable? = null

        pending.withIndex()
            .sortedWith(compareBy<IndexedValue<OutboxChange>> { entityPushRank(it.value.entityType) }.thenBy { it.index })
            .map { it.value }
            .forEach { change ->
            if (failure != null) {
                return@forEach
            }

            val request = SyncPushRequest(
                userId = session.userId,
                deviceId = session.deviceId,
                changes = listOf(
                    ChangeEnvelope(
                        entityType = change.entityType,
                        entityId = change.entityId,
                        operation = change.operation,
                        payload = change.payload,
                        createdAtEpochMs = change.createdAtEpochMs,
                        deviceId = session.deviceId,
                    ),
                ),
            )

            runCatching {
                syncApi.push(request)
            }.onSuccess { response ->
                acceptedCount += response.acceptedCount
                syncRepository.markSynced(change.id)
            }.onFailure { throwable ->
                syncRepository.markFailed(change.id, change.retryCount + 1)
                if (throwable !is SyncConflictApiException) {
                    failure = throwable
                }
            }
        }

        return PushResult(
            acceptedCount = acceptedCount,
            failure = failure,
        )
    }

    private fun entityPushRank(entityType: EntityType): Int = when (entityType) {
        EntityType.TAG -> 0
        EntityType.DIARY_ENTRY -> 1
        EntityType.SCHEDULE_EVENT -> 2
        EntityType.CHAT_SESSION -> 3
        EntityType.CHAT_MESSAGE -> 4
        EntityType.DIARY_ENTRY_TAG -> 5
    }

    private data class PushResult(
        val acceptedCount: Int = 0,
        val failure: Throwable? = null,
    )
}
