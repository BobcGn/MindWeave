package org.example.mindweave.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.mindweave.DEFAULT_BOOTSTRAP_PASSWORD
import org.example.mindweave.DEFAULT_BOOTSTRAP_USERNAME
import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.ai.ModelPackage
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatConversation
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.ConflictStatus
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.OutboxChange
import org.example.mindweave.domain.model.OutboxStatus
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.SyncConflictRecord
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.domain.model.Tag
import org.example.mindweave.domain.model.UserAccount
import org.example.mindweave.domain.model.UserPreferences
import org.example.mindweave.platform.PlatformContext
import org.example.mindweave.repository.AccountRepository
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ModelPackageRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.repository.SyncRepository
import org.example.mindweave.repository.TagRepository
import org.example.mindweave.repository.UserPreferencesRepository
import org.example.mindweave.util.*
import platform.posix.EEXIST
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rename
import platform.posix.rewind
import platform.posix.stat

internal fun createOhosLocalRepositories(
    platformContext: PlatformContext,
    session: AppSession,
): LocalRepositories {
    val store = OhosLocalStore(resolveStorePath(platformContext.databaseName))
    val syncRepository = OhosSyncRepository(store)
    val outboxRecorder = OhosOutboxRecorder(syncRepository)
    val tagRepository = OhosTagRepository(store, outboxRecorder)
    val accountRepository = OhosAccountRepository(store)
    val userPreferencesRepository = OhosUserPreferencesRepository(store)
    val modelPackageRepository = OhosModelPackageRepository(store)
    return LocalRepositories(
        diaryRepository = OhosDiaryRepository(store, tagRepository, outboxRecorder),
        scheduleRepository = OhosScheduleRepository(store, outboxRecorder),
        tagRepository = tagRepository,
        chatRepository = OhosChatRepository(store, outboxRecorder),
        syncRepository = syncRepository,
        accountRepository = accountRepository,
        userPreferencesRepository = userPreferencesRepository,
        modelPackageRepository = modelPackageRepository,
        session = session,
    )
}

private fun resolveStorePath(databaseName: String): String = databaseName.ifBlank {
    "mindweave-harmony.db"
}

private data class OhosStoreState(
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val scheduleEvents: List<ScheduleEvent> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val diaryEntryTags: List<DiaryEntryTag> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val outboxChanges: List<OutboxChange> = emptyList(),
    val syncConflicts: List<SyncConflictRecord> = emptyList(),
    val accounts: List<UserAccount> = emptyList(),
    val preferences: List<UserPreferences> = emptyList(),
    val modelPackages: List<ModelPackage> = emptyList(),
    val lastSyncSeq: Long = 0L,
)

private class OhosLocalStore(
    private val filePath: String,
) {
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(loadState())

    fun observe(): StateFlow<OhosStoreState> = stateFlow.asStateFlow()

    fun snapshot(): OhosStoreState = stateFlow.value

    suspend fun update(transform: (OhosStoreState) -> OhosStoreState): OhosStoreState = mutex.withLock {
        val current = stateFlow.value
        val next = transform(current)
        if (next != current) {
            persistState(next)
            stateFlow.value = next
        }
        next
    }

    private fun loadState(): OhosStoreState {
        val payload = readTextFile(filePath)?.takeIf(String::isNotBlank) ?: return OhosStoreState()
        return runCatching {
            parseJson(payload).asObject().toOhosStoreState()
        }.getOrElse {
            OhosStoreState()
        }
    }

    private fun persistState(state: OhosStoreState) {
        val payload = stringifyJson(state.toJson())
        val tempPath = "$filePath.tmp"
        writeTextFile(tempPath, payload)
        if (rename(tempPath, filePath) != 0) {
            writeTextFile(filePath, payload)
            remove(tempPath)
        }
    }
}

private class OhosDiaryRepository(
    private val store: OhosLocalStore,
    private val tagRepository: TagRepository,
    private val outboxRecorder: OhosOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : DiaryRepository {
    override fun observeTimeline(userId: String): Flow<List<DiaryTimelineItem>> =
        store.observe().map { state -> state.timelineForUser(userId) }

    override suspend fun createDraft(userId: String, deviceId: String, draft: DiaryDraft): DiaryEntry {
        val now = nowProvider()
        val entry = DiaryEntry(
            id = IdGenerator.next("diary", now),
            userId = userId,
            title = draft.title.trim().ifBlank { draft.content.take(18).ifBlank { "未命名日记" } },
            content = draft.content.trim(),
            mood = draft.mood,
            aiSummary = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null,
            version = 1,
            lastModifiedByDeviceId = deviceId,
        )
        upsert(entry, trackSync = true)
        tagRepository.replaceTagsForDiary(
            userId = userId,
            deviceId = deviceId,
            entryId = entry.id,
            tagNames = draft.tags,
            baseVersion = 1,
        )
        return entry
    }

    override suspend fun upsert(entry: DiaryEntry, trackSync: Boolean) {
        store.update { state ->
            state.copy(diaryEntries = state.diaryEntries.upsertBy(DiaryEntry::id, entry))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.DIARY_ENTRY,
                entityId = entry.id,
                operation = if (entry.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(entry),
                createdAtEpochMs = entry.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getById(id: String): DiaryTimelineItem? {
        val state = store.snapshot()
        val entry = state.diaryEntries.firstOrNull { it.id == id } ?: return null
        return DiaryTimelineItem(
            entry = entry,
            tags = state.activeTagsForEntry(id).map(Tag::name),
        )
    }

    override suspend fun getRecent(userId: String, limit: Long): List<DiaryTimelineItem> =
        store.snapshot().timelineForUser(userId).take(limit.toSafeCount())

    override suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long) {
        val existing = getById(id)?.entry ?: return
        val deleted = existing.copy(
            deletedAtEpochMs = deletedAtEpochMs,
            updatedAtEpochMs = deletedAtEpochMs,
            version = nextVersion,
            lastModifiedByDeviceId = deviceId,
        )
        store.update { state ->
            state.copy(diaryEntries = state.diaryEntries.upsertBy(DiaryEntry::id, deleted))
        }
        outboxRecorder.record(
            entityType = EntityType.DIARY_ENTRY,
            entityId = id,
            operation = SyncOperation.SOFT_DELETE,
            payload = encodeSyncableEntity(deleted),
            createdAtEpochMs = deletedAtEpochMs,
        )
    }

    override suspend fun countActive(userId: String): Long =
        store.snapshot().diaryEntries.count { it.userId == userId && it.deletedAtEpochMs == null }.toLong()
}

private class OhosScheduleRepository(
    private val store: OhosLocalStore,
    private val outboxRecorder: OhosOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ScheduleRepository {
    override fun observeUpcoming(userId: String): Flow<List<ScheduleEvent>> =
        store.observe().map { state -> state.activeSchedulesForUser(userId) }

    override suspend fun createDraft(userId: String, deviceId: String, draft: ScheduleDraft): ScheduleEvent {
        val now = nowProvider()
        val event = ScheduleEvent(
            id = IdGenerator.next("schedule", now),
            userId = userId,
            title = draft.title.trim(),
            description = draft.description.trim(),
            startTimeEpochMs = draft.startTimeEpochMs,
            endTimeEpochMs = draft.endTimeEpochMs,
            remindAtEpochMs = draft.remindAtEpochMs,
            type = draft.type,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null,
            version = 1,
            lastModifiedByDeviceId = deviceId,
        )
        upsert(event, trackSync = true)
        return event
    }

    override suspend fun upsert(event: ScheduleEvent, trackSync: Boolean) {
        store.update { state ->
            state.copy(scheduleEvents = state.scheduleEvents.upsertBy(ScheduleEvent::id, event))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.SCHEDULE_EVENT,
                entityId = event.id,
                operation = if (event.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(event),
                createdAtEpochMs = event.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getById(id: String): ScheduleEvent? =
        store.snapshot().scheduleEvents.firstOrNull { it.id == id }

    override suspend fun getUpcoming(userId: String, fromEpochMs: Long, limit: Long): List<ScheduleEvent> =
        store.snapshot()
            .activeSchedulesForUser(userId)
            .filter { it.endTimeEpochMs >= fromEpochMs }
            .take(limit.toSafeCount())

    override suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long) {
        val existing = getById(id) ?: return
        val deleted = existing.copy(
            deletedAtEpochMs = deletedAtEpochMs,
            updatedAtEpochMs = deletedAtEpochMs,
            version = nextVersion,
            lastModifiedByDeviceId = deviceId,
        )
        store.update { state ->
            state.copy(scheduleEvents = state.scheduleEvents.upsertBy(ScheduleEvent::id, deleted))
        }
        outboxRecorder.record(
            entityType = EntityType.SCHEDULE_EVENT,
            entityId = id,
            operation = SyncOperation.SOFT_DELETE,
            payload = encodeSyncableEntity(deleted),
            createdAtEpochMs = deletedAtEpochMs,
        )
    }

    override suspend fun countActive(userId: String): Long =
        store.snapshot().scheduleEvents.count { it.userId == userId && it.deletedAtEpochMs == null }.toLong()
}

private class OhosTagRepository(
    private val store: OhosLocalStore,
    private val outboxRecorder: OhosOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : TagRepository {
    override fun observeTags(userId: String): Flow<List<Tag>> =
        store.observe().map { state -> state.activeTagsForUser(userId) }

    override suspend fun ensureTags(userId: String, deviceId: String, names: List<String>): List<Tag> {
        val normalized = names.normalizeTags()
        if (normalized.isEmpty()) return emptyList()

        val existing = store.snapshot()
            .activeTagsForUser(userId)
            .associateByTo(mutableMapOf()) { it.name.lowercase() }

        normalized.forEach { name ->
            if (existing.containsKey(name.lowercase())) return@forEach
            val now = nowProvider()
            val tag = Tag(
                id = IdGenerator.next("tag", now),
                userId = userId,
                name = name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                deletedAtEpochMs = null,
                version = 1,
                lastModifiedByDeviceId = deviceId,
            )
            upsertTag(tag, trackSync = true)
            existing[name.lowercase()] = tag
        }
        return normalized.mapNotNull { existing[it.lowercase()] }
    }

    override suspend fun replaceTagsForDiary(
        userId: String,
        deviceId: String,
        entryId: String,
        tagNames: List<String>,
        baseVersion: Long,
    ) {
        val targetTags = ensureTags(userId, deviceId, tagNames)
        val state = store.snapshot()
        val existingLinks = state.activeDiaryTagLinksForEntry(entryId)
        val existingTags = state.activeTagsForEntry(entryId).associateBy { it.id }
        val targetTagIds = targetTags.map { it.id }.toSet()

        existingLinks.forEach { link ->
            if (link.tagId !in targetTagIds) {
                val deletedAt = nowProvider()
                val deletedLink = link.copy(
                    deletedAtEpochMs = deletedAt,
                    updatedAtEpochMs = deletedAt,
                    version = link.version + 1,
                    lastModifiedByDeviceId = deviceId,
                )
                store.update { current ->
                    current.copy(
                        diaryEntryTags = current.diaryEntryTags.upsertBy(DiaryEntryTag::id, deletedLink),
                    )
                }
                outboxRecorder.record(
                    entityType = EntityType.DIARY_ENTRY_TAG,
                    entityId = deletedLink.id,
                    operation = SyncOperation.SOFT_DELETE,
                    payload = encodeSyncableEntity(deletedLink),
                    createdAtEpochMs = deletedAt,
                )
            }
        }

        targetTags.forEach { tag ->
            if (existingTags.containsKey(tag.id)) return@forEach
            val now = nowProvider()
            upsertDiaryEntryTag(
                DiaryEntryTag(
                    id = IdGenerator.next("entry-tag", now),
                    userId = userId,
                    entryId = entryId,
                    tagId = tag.id,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    deletedAtEpochMs = null,
                    version = baseVersion,
                    lastModifiedByDeviceId = deviceId,
                ),
                trackSync = true,
            )
        }
    }

    override suspend fun getTagsForDiary(entryId: String): List<Tag> =
        store.snapshot().activeTagsForEntry(entryId)

    override suspend fun getTagById(id: String): Tag? =
        store.snapshot().tags.firstOrNull { it.id == id }

    override suspend fun upsertTag(tag: Tag, trackSync: Boolean) {
        store.update { state ->
            state.copy(tags = state.tags.upsertBy(Tag::id, tag))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.TAG,
                entityId = tag.id,
                operation = if (tag.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(tag),
                createdAtEpochMs = tag.updatedAtEpochMs,
            )
        }
    }

    override suspend fun upsertDiaryEntryTag(link: DiaryEntryTag, trackSync: Boolean) {
        store.update { state ->
            state.copy(diaryEntryTags = state.diaryEntryTags.upsertBy(DiaryEntryTag::id, link))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.DIARY_ENTRY_TAG,
                entityId = link.id,
                operation = if (link.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(link),
                createdAtEpochMs = link.updatedAtEpochMs,
            )
        }
    }
}

private class OhosChatRepository(
    private val store: OhosLocalStore,
    private val outboxRecorder: OhosOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ChatRepository {
    override fun observeSessions(userId: String): Flow<List<ChatSession>> =
        store.observe().map { state -> state.activeChatSessionsForUser(userId) }

    override fun observeConversation(sessionId: String): Flow<List<ChatMessage>> =
        store.observe().map { state -> state.activeMessagesForSession(sessionId) }

    override suspend fun createSession(userId: String, deviceId: String, title: String): ChatSession {
        val now = nowProvider()
        val session = ChatSession(
            id = IdGenerator.next("chat", now),
            userId = userId,
            title = title.trim().ifBlank { "新的对话" },
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null,
            version = 1,
            lastModifiedByDeviceId = deviceId,
        )
        upsertSession(session, trackSync = true)
        return session
    }

    override suspend fun appendMessage(
        sessionId: String,
        userId: String,
        deviceId: String,
        role: ChatRole,
        content: String,
    ): ChatMessage {
        val now = nowProvider()
        val message = ChatMessage(
            id = IdGenerator.next("msg", now),
            sessionId = sessionId,
            userId = userId,
            role = role,
            content = content.trim(),
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null,
            version = 1,
            lastModifiedByDeviceId = deviceId,
        )
        getSessionById(sessionId)?.let { current ->
            upsertSession(
                current.copy(
                    title = if (current.title == "新的对话" && role == ChatRole.USER) {
                        content.take(16).ifBlank { current.title }
                    } else {
                        current.title
                    },
                    updatedAtEpochMs = now,
                    version = current.version + 1,
                    lastModifiedByDeviceId = deviceId,
                ),
                trackSync = true,
            )
        }
        upsertMessage(message, trackSync = true)
        return message
    }

    override suspend fun upsertSession(session: ChatSession, trackSync: Boolean) {
        store.update { state ->
            state.copy(chatSessions = state.chatSessions.upsertBy(ChatSession::id, session))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.CHAT_SESSION,
                entityId = session.id,
                operation = if (session.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(session),
                createdAtEpochMs = session.updatedAtEpochMs,
            )
        }
    }

    override suspend fun upsertMessage(message: ChatMessage, trackSync: Boolean) {
        store.update { state ->
            state.copy(chatMessages = state.chatMessages.upsertBy(ChatMessage::id, message))
        }
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.CHAT_MESSAGE,
                entityId = message.id,
                operation = if (message.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = encodeSyncableEntity(message),
                createdAtEpochMs = message.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getSessionById(id: String): ChatSession? =
        store.snapshot().chatSessions.firstOrNull { it.id == id }

    override suspend fun getMessageById(id: String): ChatMessage? =
        store.snapshot().chatMessages.firstOrNull { it.id == id }

    override suspend fun getRecentMessages(userId: String, limit: Long): List<ChatMessage> =
        store.snapshot()
            .chatMessages
            .filter { it.userId == userId && it.deletedAtEpochMs == null }
            .sortedByDescending(ChatMessage::createdAtEpochMs)
            .take(limit.toSafeCount())

    override suspend fun getConversation(sessionId: String): ChatConversation? {
        val state = store.snapshot()
        val session = state.chatSessions.firstOrNull { it.id == sessionId } ?: return null
        return ChatConversation(
            session = session,
            messages = state.activeMessagesForSession(sessionId),
        )
    }
}

private class OhosSyncRepository(
    private val store: OhosLocalStore,
) : SyncRepository {
    override fun observeSyncState(): Flow<SyncState> =
        store.observe().map { state ->
            SyncState(
                pendingChanges = state.outboxChanges.count { it.status != OutboxStatus.SYNCED }.toLong(),
                lastSyncSeq = state.lastSyncSeq,
            )
        }

    override suspend fun enqueue(change: OutboxChange) {
        store.update { state ->
            state.copy(outboxChanges = state.outboxChanges.upsertBy(OutboxChange::id, change))
        }
    }

    override suspend fun pendingChanges(): List<OutboxChange> =
        store.snapshot()
            .outboxChanges
            .filter { it.status != OutboxStatus.SYNCED }
            .sortedBy(OutboxChange::createdAtEpochMs)

    override suspend fun markSynced(changeId: String) {
        store.update { state ->
            state.copy(outboxChanges = state.outboxChanges.map { change ->
                if (change.id == changeId) {
                    change.copy(retryCount = 0, status = OutboxStatus.SYNCED)
                } else {
                    change
                }
            })
        }
    }

    override suspend fun markFailed(changeId: String, nextRetryCount: Long) {
        store.update { state ->
            state.copy(outboxChanges = state.outboxChanges.map { change ->
                if (change.id == changeId) {
                    change.copy(retryCount = nextRetryCount, status = OutboxStatus.FAILED)
                } else {
                    change
                }
            })
        }
    }

    override suspend fun getLastSyncSeq(): Long = store.snapshot().lastSyncSeq

    override suspend fun setLastSyncSeq(seq: Long) {
        store.update { state -> state.copy(lastSyncSeq = seq) }
    }

    override suspend fun recordConflict(conflict: SyncConflictRecord) {
        store.update { state ->
            state.copy(syncConflicts = state.syncConflicts.upsertBy(SyncConflictRecord::id, conflict))
        }
    }

    override suspend fun getConflicts(): List<SyncConflictRecord> =
        store.snapshot().syncConflicts.sortedByDescending(SyncConflictRecord::createdAtEpochMs)
}

private class OhosAccountRepository(
    private val store: OhosLocalStore,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : AccountRepository {
    override fun observeAccount(userId: String): Flow<UserAccount?> =
        store.observe().map { state -> state.accounts.firstOrNull { it.userId == userId } }

    override suspend fun getAccount(userId: String): UserAccount? =
        store.snapshot().accounts.firstOrNull { it.userId == userId }

    override suspend fun authenticate(userId: String, username: String, password: String): UserAccount? {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) return null
        val account = getAccount(userId) ?: return null
        if (account.username != normalizedUsername) return null
        if (account.passwordHash != hashPassword(password)) return null

        val loginAtEpochMs = nowProvider()
        val updated = account.copy(lastLoginAtEpochMs = loginAtEpochMs)
        store.update { state ->
            state.copy(accounts = state.accounts.upsertBy(UserAccount::userId, updated))
        }
        return updated
    }

    override suspend fun ensureDefaultAccount(userId: String) {
        if (getAccount(userId) != null) return
        val now = nowProvider()
        store.update { state ->
            state.copy(
                accounts = state.accounts.upsertBy(
                    UserAccount::userId,
                    UserAccount(
                        userId = userId,
                        username = DEFAULT_BOOTSTRAP_USERNAME,
                        passwordHash = hashPassword(DEFAULT_BOOTSTRAP_PASSWORD),
                        mustChangeCredentials = true,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        credentialsUpdatedAtEpochMs = now,
                        lastLoginAtEpochMs = null,
                    ),
                ),
            )
        }
    }

    override suspend fun forceResetCredentials(
        userId: String,
        newUsername: String,
        newPassword: String,
    ): String? = updateCredentials(
        userId = userId,
        currentPassword = null,
        newUsername = newUsername,
        newPassword = newPassword,
    )

    override suspend fun changeCredentials(
        userId: String,
        currentPassword: String,
        newUsername: String,
        newPassword: String,
    ): String? = updateCredentials(
        userId = userId,
        currentPassword = currentPassword,
        newUsername = newUsername,
        newPassword = newPassword,
    )

    private suspend fun updateCredentials(
        userId: String,
        currentPassword: String?,
        newUsername: String,
        newPassword: String,
    ): String? {
        val account = getAccount(userId) ?: return "账户不存在。"
        val normalizedUsername = newUsername.trim()
        if (normalizedUsername.isBlank()) return "账号不能为空。"
        if (newPassword.isBlank()) return "密码不能为空。"
        if (normalizedUsername == DEFAULT_BOOTSTRAP_USERNAME) return "默认账号不能继续使用，请更换为个人账号。"
        if (newPassword == DEFAULT_BOOTSTRAP_PASSWORD) return "默认密码不能继续使用，请设置新密码。"
        if (currentPassword != null && account.passwordHash != hashPassword(currentPassword)) {
            return "当前密码不正确。"
        }

        val existing = store.snapshot().accounts.firstOrNull { it.username == normalizedUsername }
        if (existing != null && existing.userId != userId) {
            return "该账号已存在。"
        }

        val now = nowProvider()
        val updated = account.copy(
            username = normalizedUsername,
            passwordHash = hashPassword(newPassword),
            mustChangeCredentials = false,
            updatedAtEpochMs = now,
            credentialsUpdatedAtEpochMs = now,
        )
        store.update { state ->
            state.copy(accounts = state.accounts.upsertBy(UserAccount::userId, updated))
        }
        return null
    }
}

private class OhosUserPreferencesRepository(
    private val store: OhosLocalStore,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : UserPreferencesRepository {
    override fun observePreferences(userId: String): Flow<UserPreferences?> =
        store.observe().map { state -> state.preferences.firstOrNull { it.userId == userId } }

    override suspend fun getPreferences(userId: String): UserPreferences? =
        store.snapshot().preferences.firstOrNull { it.userId == userId }

    override suspend fun ensureDefaultPreferences(
        userId: String,
        bootstrapAiSettings: AiSettings,
    ) {
        if (getPreferences(userId) != null) return
        val now = nowProvider()
        val seed = when (bootstrapAiSettings) {
            AiSettings.Disabled -> UserPreferences(
                userId = userId,
                aiMode = AiOperatingMode.DISABLED,
                cloudEnhancementBaseUrl = "",
                localLightweightModelPackageId = AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
                localGenerativeModelPackageId = AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
                modelDownloadPolicy = ModelDownloadPolicy.PREBUNDLED,
                updatedAtEpochMs = now,
            )
            is AiSettings.LocalOnly -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = "",
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = now,
            )
            is AiSettings.LocalFirstCloudEnhancement -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = bootstrapAiSettings.cloudEnhancementBaseUrl,
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = now,
            )
            is AiSettings.ManualCloudEnhancement -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = bootstrapAiSettings.cloudEnhancementBaseUrl,
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = now,
            )
        }
        store.update { state ->
            state.copy(preferences = state.preferences.upsertBy(UserPreferences::userId, seed))
        }
    }

    override suspend fun savePreferences(
        userId: String,
        aiMode: AiOperatingMode,
        cloudEnhancementBaseUrl: String,
        localLightweightModelPackageId: String,
        localGenerativeModelPackageId: String,
        modelDownloadPolicy: ModelDownloadPolicy,
    ) {
        val next = UserPreferences(
            userId = userId,
            aiMode = aiMode,
            cloudEnhancementBaseUrl = cloudEnhancementBaseUrl.trim(),
            localLightweightModelPackageId = localLightweightModelPackageId.trim()
                .ifBlank { AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID },
            localGenerativeModelPackageId = localGenerativeModelPackageId.trim()
                .ifBlank { AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID },
            modelDownloadPolicy = modelDownloadPolicy,
            updatedAtEpochMs = nowProvider(),
        )
        store.update { state ->
            state.copy(preferences = state.preferences.upsertBy(UserPreferences::userId, next))
        }
    }
}

private class OhosModelPackageRepository(
    private val store: OhosLocalStore,
) : ModelPackageRepository {
    override suspend fun listModelPackages(): List<ModelPackage> =
        store.snapshot()
            .modelPackages
            .sortedWith(compareBy<ModelPackage>({ it.kind.name }, { it.packageId }))

    override suspend fun getModelPackage(packageId: String): ModelPackage? =
        store.snapshot().modelPackages.firstOrNull { it.packageId == packageId }

    override suspend fun upsertModelPackage(modelPackage: ModelPackage) {
        store.update { state ->
            state.copy(modelPackages = state.modelPackages.upsertBy(ModelPackage::packageId, modelPackage))
        }
    }
}

private class OhosOutboxRecorder(
    private val syncRepository: SyncRepository,
) {
    suspend fun record(
        entityType: EntityType,
        entityId: String,
        operation: SyncOperation,
        payload: String,
        createdAtEpochMs: Long,
    ) {
        syncRepository.enqueue(
            OutboxChange(
                id = IdGenerator.next("outbox", createdAtEpochMs),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payload = payload,
                createdAtEpochMs = createdAtEpochMs,
                retryCount = 0,
                status = OutboxStatus.PENDING,
            ),
        )
    }
}

private fun OhosStoreState.timelineForUser(userId: String): List<DiaryTimelineItem> =
    diaryEntries
        .filter { it.userId == userId && it.deletedAtEpochMs == null }
        .sortedByDescending(DiaryEntry::createdAtEpochMs)
        .map { entry ->
            DiaryTimelineItem(
                entry = entry,
                tags = activeTagsForEntry(entry.id).map(Tag::name),
            )
        }

private fun OhosStoreState.activeSchedulesForUser(userId: String): List<ScheduleEvent> =
    scheduleEvents
        .filter { it.userId == userId && it.deletedAtEpochMs == null }
        .sortedBy(ScheduleEvent::startTimeEpochMs)

private fun OhosStoreState.activeTagsForUser(userId: String): List<Tag> =
    tags
        .filter { it.userId == userId && it.deletedAtEpochMs == null }
        .sortedBy(Tag::name)

private fun OhosStoreState.activeDiaryTagLinksForEntry(entryId: String): List<DiaryEntryTag> =
    diaryEntryTags.filter { it.entryId == entryId && it.deletedAtEpochMs == null }

private fun OhosStoreState.activeTagsForEntry(entryId: String): List<Tag> {
    val activeTagIds = activeDiaryTagLinksForEntry(entryId).map(DiaryEntryTag::tagId).toSet()
    return tags
        .filter { it.id in activeTagIds && it.deletedAtEpochMs == null }
        .sortedBy(Tag::name)
}

private fun OhosStoreState.activeChatSessionsForUser(userId: String): List<ChatSession> =
    chatSessions
        .filter { it.userId == userId && it.deletedAtEpochMs == null }
        .sortedByDescending(ChatSession::updatedAtEpochMs)

private fun OhosStoreState.activeMessagesForSession(sessionId: String): List<ChatMessage> =
    chatMessages
        .filter { it.sessionId == sessionId && it.deletedAtEpochMs == null }
        .sortedBy(ChatMessage::createdAtEpochMs)

private fun List<String>.normalizeTags(): List<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

private fun Long.toSafeCount(): Int = when {
    this <= 0L -> 0
    this >= Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
    else -> toInt()
}

private fun <T> List<T>.upsertBy(keySelector: (T) -> String, value: T): List<T> {
    val key = keySelector(value)
    val index = indexOfFirst { keySelector(it) == key }
    if (index < 0) return this + value
    return toMutableList().also { it[index] = value }
}

private fun OhosStoreState.toJson(): JsonObject = jsonObjectOf(
    "diaryEntries" to jsonArrayOf(diaryEntries.map { it.toJson() }),
    "scheduleEvents" to jsonArrayOf(scheduleEvents.map { it.toJson() }),
    "tags" to jsonArrayOf(tags.map { it.toJson() }),
    "diaryEntryTags" to jsonArrayOf(diaryEntryTags.map { it.toJson() }),
    "chatSessions" to jsonArrayOf(chatSessions.map { it.toJson() }),
    "chatMessages" to jsonArrayOf(chatMessages.map { it.toJson() }),
    "outboxChanges" to jsonArrayOf(outboxChanges.map { it.toJson() }),
    "syncConflicts" to jsonArrayOf(syncConflicts.map { it.toJson() }),
    "accounts" to jsonArrayOf(accounts.map { it.toJson() }),
    "preferences" to jsonArrayOf(preferences.map { it.toJson() }),
    "modelPackages" to jsonArrayOf(modelPackages.map { it.toJson() }),
    "lastSyncSeq" to jsonLong(lastSyncSeq),
)

private fun JsonObject.toOhosStoreState(): OhosStoreState = OhosStoreState(
    diaryEntries = array("diaryEntries").map { it.asObject().toDiaryEntry() },
    scheduleEvents = array("scheduleEvents").map { it.asObject().toScheduleEvent() },
    tags = array("tags").map { it.asObject().toTag() },
    diaryEntryTags = array("diaryEntryTags").map { it.asObject().toDiaryEntryTag() },
    chatSessions = array("chatSessions").map { it.asObject().toChatSession() },
    chatMessages = array("chatMessages").map { it.asObject().toChatMessage() },
    outboxChanges = array("outboxChanges").map { it.asObject().toOutboxChange() },
    syncConflicts = array("syncConflicts").map { it.asObject().toSyncConflictRecord() },
    accounts = array("accounts").map { it.asObject().toUserAccount() },
    preferences = array("preferences").map { it.asObject().toUserPreferences() },
    modelPackages = array("modelPackages").map { it.asObject().toModelPackage() },
    lastSyncSeq = long("lastSyncSeq"),
)

@OptIn(ExperimentalForeignApi::class)
private fun readTextFile(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    return try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val size = ftell(file).toInt()
        if (size < 0) return null
        rewind(file)
        if (size == 0) return ""
        val bytes = ByteArray(size)
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), size.convert(), file).toInt()
        }
        if (read < size) {
            bytes.copyOf(read.coerceAtLeast(0)).decodeToString()
        } else {
            bytes.decodeToString()
        }
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeTextFile(path: String, content: String) {
    ensureParentDirectory(path)
    val file = fopen(path, "wb") ?: error("Unable to open $path for writing.")
    try {
        val bytes = content.encodeToByteArray()
        if (bytes.isNotEmpty()) {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
            }
            if (written != bytes.size) {
                error("Unable to fully write $path.")
            }
        }
        fflush(file)
    } finally {
        fclose(file)
    }
}

private fun ensureParentDirectory(path: String) {
    val parent = path.substringBeforeLast("/", missingDelimiterValue = "")
    if (parent.isNotBlank()) {
        ensureDirectory(parent)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureDirectory(path: String) {
    if (path.isBlank() || directoryExists(path)) return
    val parent = path.substringBeforeLast("/", missingDelimiterValue = "")
    if (parent.isNotBlank() && parent != path) {
        ensureDirectory(parent)
    }
    if (mkdir(path, 493.convert()) != 0 && errno != EEXIST) {
        error("Unable to create directory $path.")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun directoryExists(path: String): Boolean = memScoped {
    val info = alloc<stat>()
    stat(path, info.ptr) == 0
}
