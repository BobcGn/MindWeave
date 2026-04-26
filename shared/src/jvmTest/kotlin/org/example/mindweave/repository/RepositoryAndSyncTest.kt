package org.example.mindweave.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.mindweave.ai.ChatContextAssembler
import org.example.mindweave.data.local.createLocalRepositories
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.sync.InMemorySyncApi
import org.example.mindweave.sync.LocalChangeApplier
import org.example.mindweave.sync.RemoteChangeEnvelope
import org.example.mindweave.sync.SyncManager
import org.example.mindweave.util.MindWeaveJson
import org.example.mindweave.util.currentEpochMillis

class RepositoryAndSyncTest {
    @Test
    fun localWriteShouldCreateOutboxAndSupportSoftDelete() = runTest {
        val session = AppSession("user-1", "device-a", "Device A")
        val repositories = createRepositories(session)

        val entry = repositories.diaryRepository.createDraft(
            userId = session.userId,
            deviceId = session.deviceId,
            draft = DiaryDraft(
                title = "test",
                content = "content",
                mood = DiaryMood.CALM,
                tags = listOf("one", "two"),
            ),
        )

        assertEquals(1L, repositories.diaryRepository.countActive(session.userId))
        assertTrue(repositories.syncRepository.pendingChanges().isNotEmpty())

        repositories.diaryRepository.softDelete(
            id = entry.id,
            deviceId = session.deviceId,
            deletedAtEpochMs = 123L,
            nextVersion = 2L,
        )

        assertEquals(0L, repositories.diaryRepository.countActive(session.userId))
        assertTrue(repositories.syncRepository.pendingChanges().any { it.entityId == entry.id })
    }

    @Test
    fun syncManagerShouldPushAndPullIncrementally() = runTest {
        val api = InMemorySyncApi()
        val sessionA = AppSession("user-1", "device-a", "Device A")
        val sessionB = AppSession("user-1", "device-b", "Device B")
        val repoA = createRepositories(sessionA)
        val repoB = createRepositories(sessionB)

        repoA.diaryRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = DiaryDraft(
                title = "shared",
                content = "from A",
                mood = DiaryMood.GRATEFUL,
                tags = listOf("sync"),
            ),
        )

        val syncA = SyncManager(
            syncApi = api,
            syncRepository = repoA.syncRepository,
            localChangeApplier = LocalChangeApplier(
                diaryRepository = repoA.diaryRepository,
                scheduleRepository = repoA.scheduleRepository,
                tagRepository = repoA.tagRepository,
                chatRepository = repoA.chatRepository,
                syncRepository = repoA.syncRepository,
            ),
        )
        val syncB = SyncManager(
            syncApi = api,
            syncRepository = repoB.syncRepository,
            localChangeApplier = LocalChangeApplier(
                diaryRepository = repoB.diaryRepository,
                scheduleRepository = repoB.scheduleRepository,
                tagRepository = repoB.tagRepository,
                chatRepository = repoB.chatRepository,
                syncRepository = repoB.syncRepository,
            ),
        )

        syncA.synchronize(sessionA)
        syncB.synchronize(sessionB)
        syncB.synchronize(sessionB)

        assertEquals(1, repoB.diaryRepository.getRecent(sessionB.userId, 10).size)
        assertTrue(repoB.syncRepository.getLastSyncSeq() > 0L)
    }

    @Test
    fun contextAssemblerShouldGatherDiaryScheduleAndChat() = runTest {
        val session = AppSession("user-2", "device-a", "Device A")
        val repositories = createRepositories(session)

        repositories.diaryRepository.createDraft(
            userId = session.userId,
            deviceId = session.deviceId,
            draft = DiaryDraft(
                title = "ctx",
                content = "diary body",
                mood = DiaryMood.UNCERTAIN,
                tags = listOf("ctx"),
            ),
        )
        repositories.scheduleRepository.createDraft(
            userId = session.userId,
            deviceId = session.deviceId,
            draft = ScheduleDraft(
                title = "meeting",
                description = "desc",
                startTimeEpochMs = currentEpochMillis() + 10_000L,
                endTimeEpochMs = currentEpochMillis() + 20_000L,
                remindAtEpochMs = null,
                type = ScheduleType.WORK,
            ),
        )
        val sessionId = repositories.chatRepository.createSession(session.userId, session.deviceId, "chat").id
        repositories.chatRepository.appendMessage(sessionId, session.userId, session.deviceId, org.example.mindweave.domain.model.ChatRole.USER, "hello")

        val assembler = ChatContextAssembler(
            diaryRepository = repositories.diaryRepository,
            scheduleRepository = repositories.scheduleRepository,
            chatRepository = repositories.chatRepository,
        )
        val context = assembler.build(session.userId, sessionId)

        assertEquals(1, context.recentDiaries.size)
        assertEquals(1, context.upcomingEvents.size)
        assertEquals(1, context.recentMessages.size)
    }

    @Test
    fun localChangeApplierShouldUseDeviceTieBreakerAndClearObsoleteOutbox() = runTest {
        val session = AppSession("user-3", "device-a", "Device A")
        val repositories = createRepositories(session)
        val applier = LocalChangeApplier(
            diaryRepository = repositories.diaryRepository,
            scheduleRepository = repositories.scheduleRepository,
            tagRepository = repositories.tagRepository,
            chatRepository = repositories.chatRepository,
            syncRepository = repositories.syncRepository,
        )

        repositories.diaryRepository.upsert(
            org.example.mindweave.domain.model.DiaryEntry(
                id = "diary-shared",
                userId = session.userId,
                title = "local",
                content = "local content",
                mood = DiaryMood.CALM,
                aiSummary = null,
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 100L,
                deletedAtEpochMs = null,
                version = 2L,
                lastModifiedByDeviceId = session.deviceId,
            ),
            trackSync = true,
        )

        val remote = org.example.mindweave.domain.model.DiaryEntry(
            id = "diary-shared",
            userId = session.userId,
            title = "remote",
            content = "remote content",
            mood = DiaryMood.GRATEFUL,
            aiSummary = null,
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 100L,
            deletedAtEpochMs = null,
            version = 2L,
            lastModifiedByDeviceId = "device-z",
        )

        applier.apply(
            RemoteChangeEnvelope(
                seq = 1L,
                entityType = org.example.mindweave.domain.model.EntityType.DIARY_ENTRY,
                entityId = remote.id,
                operation = org.example.mindweave.domain.model.SyncOperation.UPSERT,
                payload = MindWeaveJson.encodeToString(org.example.mindweave.domain.model.DiaryEntry.serializer(), remote),
                createdAtEpochMs = remote.updatedAtEpochMs,
                deviceId = remote.lastModifiedByDeviceId,
            ),
        )

        assertEquals("remote content", repositories.diaryRepository.getById(remote.id)?.entry?.content)
        assertTrue(repositories.syncRepository.pendingChanges().isEmpty())
    }

    private fun createRepositories(session: AppSession) = createLocalRepositories(
        database = createDatabase(),
        session = session,
    )

    private fun createDatabase(): MindWeaveDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MindWeaveDatabase.Schema.create(driver)
        return MindWeaveDatabase(driver)
    }
}
