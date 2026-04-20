package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.OutboxChange
import org.example.mindweave.domain.model.SyncConflictRecord
import org.example.mindweave.domain.model.SyncState

interface SyncRepository {
    fun observeSyncState(): Flow<SyncState>

    suspend fun enqueue(change: OutboxChange)

    suspend fun pendingChanges(): List<OutboxChange>

    suspend fun markSynced(changeId: String)

    suspend fun markFailed(changeId: String, nextRetryCount: Long)

    suspend fun getLastSyncSeq(): Long

    suspend fun setLastSyncSeq(seq: Long)

    suspend fun recordConflict(conflict: SyncConflictRecord)

    suspend fun getConflicts(): List<SyncConflictRecord>
}
