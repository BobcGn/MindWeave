package org.example.mindweave.server.service

import kotlinx.serialization.encodeToString
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.sync.ChangeEnvelope
import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.util.MindWeaveJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemorySyncServiceTest {
    @Test
    fun duplicatePushShouldBeIdempotent() {
        val repository = InMemoryServerSyncRepository()
        val service = InMemorySyncService(repository = repository, nowProvider = { 100L })
        register(service, "device-a")
        register(service, "device-b")

        val first = service.push(pushRequest("device-a", version = 1, updatedAtEpochMs = 10))
        val second = service.push(pushRequest("device-a", version = 1, updatedAtEpochMs = 10))
        val pull = service.pull(SyncPullRequest(userId = "user-1", deviceId = "device-b", afterSeq = 0))

        assertEquals(1, first.acceptedCount)
        assertEquals(0, second.acceptedCount)
        assertEquals(1, pull.changes.size)
        assertEquals(1, pull.latestSeq)
    }

    @Test
    fun stalePushShouldRecordConflictAndKeepNewestVersion() {
        val repository = InMemoryServerSyncRepository()
        val service = InMemorySyncService(repository = repository, nowProvider = { 200L })
        register(service, "device-a")
        register(service, "device-b")

        service.push(pushRequest("device-a", version = 1, updatedAtEpochMs = 10, content = "v1"))
        service.push(pushRequest("device-b", version = 2, updatedAtEpochMs = 20, content = "v2"))

        val conflict = assertFailsWith<SyncConflictException> {
            service.push(pushRequest("device-a", version = 1, updatedAtEpochMs = 10, content = "stale"))
        }
        val pull = service.pull(SyncPullRequest(userId = "user-1", deviceId = "device-a", afterSeq = 1))

        assertEquals(1, conflict.conflicts.size)
        assertEquals(1, repository.listConflicts("user-1").size)
        assertEquals(1, pull.changes.size)
        assertEquals("diary-1", pull.changes.single().entityId)
    }

    @Test
    fun pullShouldOnlyReturnChangesAfterCursorFromOtherDevices() {
        val service = InMemorySyncService(nowProvider = { 300L })
        register(service, "device-a")
        register(service, "device-b")

        service.push(pushRequest("device-a", version = 1, updatedAtEpochMs = 10))
        service.push(pushRequest("device-b", version = 2, updatedAtEpochMs = 20))

        val pull = service.pull(SyncPullRequest(userId = "user-1", deviceId = "device-a", afterSeq = 1))

        assertEquals(1, pull.changes.size)
        assertEquals("device-b", pull.changes.single().deviceId)
        assertEquals(2, pull.latestSeq)
    }

    private fun register(service: InMemorySyncService, deviceId: String) {
        service.registerDevice(
            DeviceRegistrationRequest(
                userId = "user-1",
                deviceId = deviceId,
                deviceName = deviceId,
            ),
        )
    }

    private fun pushRequest(
        deviceId: String,
        version: Long,
        updatedAtEpochMs: Long,
        content: String = "entry",
    ): SyncPushRequest = SyncPushRequest(
        userId = "user-1",
        deviceId = deviceId,
        changes = listOf(
            ChangeEnvelope(
                entityType = EntityType.DIARY_ENTRY,
                entityId = "diary-1",
                operation = SyncOperation.UPSERT,
                payload = MindWeaveJson.encodeToString(
                    DiaryEntry(
                        id = "diary-1",
                        userId = "user-1",
                        title = "Title",
                        content = content,
                        mood = DiaryMood.CALM,
                        aiSummary = null,
                        createdAtEpochMs = 1,
                        updatedAtEpochMs = updatedAtEpochMs,
                        deletedAtEpochMs = null,
                        version = version,
                        lastModifiedByDeviceId = deviceId,
                    ),
                ),
                createdAtEpochMs = updatedAtEpochMs,
                deviceId = deviceId,
            ),
        ),
    )
}
