package org.example.mindweave.server.service

import org.example.mindweave.domain.model.EntityType

class InMemoryServerSyncRepository : ServerSyncRepository {
    private val lock = Any()
    private val users = linkedMapOf<String, ServerUserRecord>()
    private val devices = linkedMapOf<String, RegisteredDeviceRecord>()
    private val snapshots = linkedMapOf<String, ServerEntitySnapshot>()
    private val changeLog = mutableListOf<ServerChangeLogRecord>()
    private val latestSeqByUser = linkedMapOf<String, Long>()
    private val dedupeKeysByUser = linkedMapOf<String, MutableSet<String>>()
    private val syncStates = linkedMapOf<String, DeviceSyncStateRecord>()
    private val conflicts = mutableListOf<ServerSyncConflictRecord>()
    private var seqCounter = 0L

    override fun upsertUser(record: ServerUserRecord) {
        withLock {
            users[record.id] = record
        }
    }

    override fun findUser(userId: String): ServerUserRecord? = withLock {
        users[userId]
    }

    override fun upsertDevice(record: RegisteredDeviceRecord) {
        withLock {
            devices[deviceKey(record.userId, record.deviceId)] = record
        }
    }

    override fun findDevice(userId: String, deviceId: String): RegisteredDeviceRecord? = withLock {
        devices[deviceKey(userId, deviceId)]
    }

    override fun findEntitySnapshot(
        userId: String,
        entityType: EntityType,
        entityId: String,
    ): ServerEntitySnapshot? = withLock {
        snapshots[entityKey(userId, entityType, entityId)]
    }

    override fun saveEntitySnapshot(snapshot: ServerEntitySnapshot) {
        withLock {
            snapshots[entityKey(snapshot.userId, snapshot.entityType, snapshot.entityId)] = snapshot
        }
    }

    override fun hasDedupeKey(userId: String, dedupeKey: String): Boolean = withLock {
        dedupeKeysByUser[userId]?.contains(dedupeKey) == true
    }

    override fun saveDedupeKey(userId: String, dedupeKey: String) {
        withLock {
            dedupeKeysByUser.getOrPut(userId) { linkedSetOf() }.add(dedupeKey)
        }
    }

    override fun appendChange(record: PendingChangeLogRecord): ServerChangeLogRecord = withLock {
        seqCounter += 1
        val stored = ServerChangeLogRecord(
            seq = seqCounter,
            userId = record.userId,
            entityType = record.entityType,
            entityId = record.entityId,
            operation = record.operation,
            payload = record.payload,
            createdAtEpochMs = record.createdAtEpochMs,
            deviceId = record.deviceId,
            version = record.version,
            updatedAtEpochMs = record.updatedAtEpochMs,
            dedupeKey = record.dedupeKey,
        )
        changeLog += stored
        latestSeqByUser[record.userId] = stored.seq
        stored
    }

    override fun listChangesAfter(
        userId: String,
        afterSeq: Long,
        excludedDeviceId: String,
    ): List<ServerChangeLogRecord> = withLock {
        changeLog
            .asSequence()
            .filter { it.userId == userId }
            .filter { it.seq > afterSeq }
            .filter { it.deviceId != excludedDeviceId }
            .toList()
    }

    override fun latestSeq(userId: String): Long = withLock {
        latestSeqByUser[userId] ?: 0L
    }

    override fun getDeviceSyncState(userId: String, deviceId: String): DeviceSyncStateRecord? = withLock {
        syncStates[deviceKey(userId, deviceId)]
    }

    override fun updateDeviceSyncState(record: DeviceSyncStateRecord) {
        withLock {
            syncStates[deviceKey(record.userId, record.deviceId)] = record
        }
    }

    override fun saveConflict(record: ServerSyncConflictRecord) {
        withLock {
            conflicts += record
        }
    }

    override fun <T> inTransaction(block: () -> T): T = withLock(block)

    fun listConflicts(userId: String): List<ServerSyncConflictRecord> = withLock {
        conflicts.filter { it.userId == userId }
    }

    private fun deviceKey(userId: String, deviceId: String): String = "$userId|$deviceId"

    private fun entityKey(userId: String, entityType: EntityType, entityId: String): String =
        "$userId|${entityType.name}|$entityId"

    private inline fun <T> withLock(block: () -> T): T = synchronized(lock, block)
}
