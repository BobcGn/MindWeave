package org.example.mindweave.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.mindweave.data.local.LocalRepositories
import org.example.mindweave.data.local.createLocalRepositories
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.network.KtorSyncApi
import org.example.mindweave.server.service.InMemorySyncService
import org.example.mindweave.sync.LocalChangeApplier
import org.example.mindweave.sync.SyncApi
import org.example.mindweave.sync.SyncManager
import org.example.mindweave.util.MindWeaveJson

class CrossDeviceSyncIntegrationTest {
    @Test
    fun serverShouldSynchronizeOfflineChangesAcrossDevices() = testApplication {
        application {
            appModule(syncService = InMemorySyncService())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(MindWeaveJson)
            }
        }
        val api = KtorSyncApi(baseUrl = "", client = client)
        val sessionA = AppSession(userId = "user-1", deviceId = "device-a", deviceName = "Phone")
        val sessionB = AppSession(userId = "user-1", deviceId = "device-b", deviceName = "Tablet")
        val repoA = createRepositories(sessionA)
        val repoB = createRepositories(sessionB)
        val syncA = createSyncManager(api, repoA)
        val syncB = createSyncManager(api, repoB)

        repoA.diaryRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = DiaryDraft(
                title = "A diary",
                content = "created offline on device A",
                mood = DiaryMood.GRATEFUL,
                tags = listOf("offline", "server"),
            ),
        )
        repoA.scheduleRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = ScheduleDraft(
                title = "A schedule",
                description = "synced through server",
                startTimeEpochMs = 1_800_000_000_000,
                endTimeEpochMs = 1_800_000_360_000,
                remindAtEpochMs = 1_800_000_000_000,
                type = ScheduleType.WORK,
            ),
        )
        val chatSession = repoA.chatRepository.createSession(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            title = "A chat",
        )
        repoA.chatRepository.appendMessage(
            sessionId = chatSession.id,
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            role = ChatRole.USER,
            content = "hello from device A",
        )

        val firstSyncA = syncA.synchronize(sessionA)
        val firstSyncB = syncB.synchronize(sessionB)

        val diaryOnB = repoB.diaryRepository.getRecent(sessionB.userId, 10).single()
        val scheduleOnB = repoB.scheduleRepository.getUpcoming(sessionB.userId, 0, 10).single()
        val messagesOnB = repoB.chatRepository.getRecentMessages(sessionB.userId, 10)

        assertEquals(0, firstSyncA.pulled)
        assertTrue(firstSyncA.pushed > 0)
        assertTrue(firstSyncB.pulled > 0)
        assertEquals("created offline on device A", diaryOnB.entry.content)
        assertContentEquals(listOf("offline", "server"), diaryOnB.tags.sorted())
        assertEquals("A schedule", scheduleOnB.title)
        assertEquals(1, messagesOnB.size)
        assertEquals("hello from device A", messagesOnB.single().content)

        repoB.diaryRepository.createDraft(
            userId = sessionB.userId,
            deviceId = sessionB.deviceId,
            draft = DiaryDraft(
                title = "B diary",
                content = "created offline on device B",
                mood = DiaryMood.CALM,
                tags = listOf("peer"),
            ),
        )

        val secondSyncB = syncB.synchronize(sessionB)
        val secondSyncA = syncA.synchronize(sessionA)
        val diariesOnA = repoA.diaryRepository.getRecent(sessionA.userId, 10)

        assertTrue(secondSyncB.pushed > 0)
        assertTrue(secondSyncA.pulled > 0)
        assertEquals(2, diariesOnA.size)
        assertTrue(diariesOnA.any { it.entry.content == "created offline on device B" })
        assertTrue(repoA.syncRepository.getLastSyncSeq() > 0L)
        assertTrue(repoB.syncRepository.getLastSyncSeq() > 0L)
    }

    @Test
    fun syncShouldRecoverFromConflictsWithoutBlockingOtherChanges() = testApplication {
        application {
            appModule(syncService = InMemorySyncService())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(MindWeaveJson)
            }
        }
        val api = KtorSyncApi(baseUrl = "", client = client)
        val sessionA = AppSession(userId = "user-2", deviceId = "device-a", deviceName = "Phone")
        val sessionB = AppSession(userId = "user-2", deviceId = "device-b", deviceName = "Tablet")
        val repoA = createRepositories(sessionA)
        val repoB = createRepositories(sessionB)
        val syncA = createSyncManager(api, repoA)
        val syncB = createSyncManager(api, repoB)

        val sharedEntry = repoA.diaryRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = DiaryDraft(
                title = "shared",
                content = "original",
                mood = DiaryMood.CALM,
                tags = emptyList(),
            ),
        )

        syncA.synchronize(sessionA)
        syncB.synchronize(sessionB)

        val entryOnA = repoA.diaryRepository.getById(sharedEntry.id)?.entry ?: error("missing local entry on A")
        val updatedAtA = entryOnA.createdAtEpochMs + 2_000L
        repoA.diaryRepository.upsert(
            entryOnA.copy(
                content = "edited on A",
                updatedAtEpochMs = updatedAtA,
                version = 2L,
                lastModifiedByDeviceId = sessionA.deviceId,
            ),
            trackSync = true,
        )
        val entryOnB = repoB.diaryRepository.getById(sharedEntry.id)?.entry ?: error("missing local entry on B")
        val updatedAtB = entryOnB.createdAtEpochMs + 3_000L
        repoB.diaryRepository.upsert(
            entryOnB.copy(
                content = "edited on B",
                updatedAtEpochMs = updatedAtB,
                version = 2L,
                lastModifiedByDeviceId = sessionB.deviceId,
            ),
            trackSync = true,
        )
        repoA.diaryRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = DiaryDraft(
                title = "fresh",
                content = "should still sync",
                mood = DiaryMood.GRATEFUL,
                tags = emptyList(),
            ),
        )

        syncB.synchronize(sessionB)
        val syncResultA = syncA.synchronize(sessionA)
        syncB.synchronize(sessionB)

        assertTrue(syncResultA.pushed > 0)
        assertTrue(syncResultA.pulled > 0)
        assertEquals("edited on B", repoA.diaryRepository.getById(sharedEntry.id)?.entry?.content)
        assertTrue(repoA.syncRepository.pendingChanges().none { it.entityId == sharedEntry.id })
        assertTrue(
            repoB.diaryRepository.getRecent(sessionB.userId, 10)
                .any { it.entry.content == "should still sync" },
        )
    }

    @Test
    fun sameNamedOfflineTagsShouldStayAttachedOnBothDevices() = testApplication {
        application {
            appModule(syncService = InMemorySyncService())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(MindWeaveJson)
            }
        }
        val api = KtorSyncApi(baseUrl = "", client = client)
        val sessionA = AppSession(userId = "user-3", deviceId = "device-a", deviceName = "Phone")
        val sessionB = AppSession(userId = "user-3", deviceId = "device-b", deviceName = "Tablet")
        val repoA = createRepositories(sessionA)
        val repoB = createRepositories(sessionB)
        val syncA = createSyncManager(api, repoA)
        val syncB = createSyncManager(api, repoB)

        repoA.diaryRepository.createDraft(
            userId = sessionA.userId,
            deviceId = sessionA.deviceId,
            draft = DiaryDraft(
                title = "A diary",
                content = "from A",
                mood = DiaryMood.CALM,
                tags = listOf("shared-tag"),
            ),
        )
        repoB.diaryRepository.createDraft(
            userId = sessionB.userId,
            deviceId = sessionB.deviceId,
            draft = DiaryDraft(
                title = "B diary",
                content = "from B",
                mood = DiaryMood.GRATEFUL,
                tags = listOf("shared-tag"),
            ),
        )

        syncA.synchronize(sessionA)
        syncB.synchronize(sessionB)
        syncA.synchronize(sessionA)
        syncB.synchronize(sessionB)

        val tagsOnA = repoA.diaryRepository.getRecent(sessionA.userId, 10).map { it.tags }
        val tagsOnB = repoB.diaryRepository.getRecent(sessionB.userId, 10).map { it.tags }

        assertEquals(2, tagsOnA.size)
        assertEquals(2, tagsOnB.size)
        assertTrue(tagsOnA.all { it == listOf("shared-tag") })
        assertTrue(tagsOnB.all { it == listOf("shared-tag") })
    }

    private fun createRepositories(session: AppSession): LocalRepositories = createLocalRepositories(
        database = createDatabase(),
        session = session,
    )

    private fun createDatabase(): MindWeaveDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MindWeaveDatabase.Schema.create(driver)
        return MindWeaveDatabase(driver)
    }

    private fun createSyncManager(
        api: SyncApi,
        repositories: LocalRepositories,
    ): SyncManager = SyncManager(
        syncApi = api,
        syncRepository = repositories.syncRepository,
        localChangeApplier = LocalChangeApplier(
            diaryRepository = repositories.diaryRepository,
            scheduleRepository = repositories.scheduleRepository,
            tagRepository = repositories.tagRepository,
            chatRepository = repositories.chatRepository,
            syncRepository = repositories.syncRepository,
        ),
    )
}
