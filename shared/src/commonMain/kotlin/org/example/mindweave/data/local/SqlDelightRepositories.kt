package org.example.mindweave.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import org.example.mindweave.db.ChatQueries
import org.example.mindweave.db.DiaryEntryQueries
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.db.ScheduleEventQueries
import org.example.mindweave.db.SyncQueries
import org.example.mindweave.db.TagQueries
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatConversation
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.ConflictStatus
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.OutboxChange
import org.example.mindweave.domain.model.OutboxStatus
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncConflictRecord
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.domain.model.Tag
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ModelPackageRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.repository.SyncRepository
import org.example.mindweave.repository.TagRepository
import org.example.mindweave.repository.AccountRepository
import org.example.mindweave.repository.UserPreferencesRepository
import org.example.mindweave.util.IdGenerator
import org.example.mindweave.util.MindWeaveJson
import org.example.mindweave.util.currentEpochMillis

class SqlDelightDiaryRepository(
    database: MindWeaveDatabase,
    private val tagRepository: TagRepository,
    private val outboxRecorder: SqlOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : DiaryRepository {
    private val queries: DiaryEntryQueries = database.diaryEntryQueries

    override fun observeTimeline(userId: String): Flow<List<DiaryTimelineItem>> =
        queries.selectDiaryTimeline(userId, ::mapDiaryTimelineItem)
            .asFlow()
            .mapToList(Dispatchers.Default)

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
        queries.upsertDiaryEntry(
            id = entry.id,
            user_id = entry.userId,
            title = entry.title,
            content = entry.content,
            mood = entry.mood.name,
            ai_summary = entry.aiSummary,
            created_at_epoch_ms = entry.createdAtEpochMs,
            updated_at_epoch_ms = entry.updatedAtEpochMs,
            deleted_at_epoch_ms = entry.deletedAtEpochMs,
            version = entry.version,
            last_modified_by_device_id = entry.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.DIARY_ENTRY,
                entityId = entry.id,
                operation = if (entry.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(entry),
                createdAtEpochMs = entry.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getById(id: String): DiaryTimelineItem? =
        queries.selectDiaryEntryById(id, ::mapDiaryTimelineItem).executeAsOneOrNull()

    override suspend fun getRecent(userId: String, limit: Long): List<DiaryTimelineItem> =
        queries.selectRecentDiaryEntries(userId, limit, ::mapDiaryTimelineItem).executeAsList()

    override suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long) {
        val existing = getById(id)?.entry ?: return
        queries.softDeleteDiaryEntry(
            deleted_at_epoch_ms = deletedAtEpochMs,
            updated_at_epoch_ms = deletedAtEpochMs,
            version = nextVersion,
            last_modified_by_device_id = deviceId,
            id = id,
        )
        outboxRecorder.record(
            entityType = EntityType.DIARY_ENTRY,
            entityId = id,
            operation = SyncOperation.SOFT_DELETE,
            payload = MindWeaveJson.encodeToString(
                existing.copy(
                    deletedAtEpochMs = deletedAtEpochMs,
                    updatedAtEpochMs = deletedAtEpochMs,
                    version = nextVersion,
                    lastModifiedByDeviceId = deviceId,
                ),
            ),
            createdAtEpochMs = deletedAtEpochMs,
        )
    }

    override suspend fun countActive(userId: String): Long =
        queries.countActiveDiaryEntries(userId).executeAsOne()

    private fun mapDiaryTimelineItem(
        id: String,
        userId: String,
        title: String,
        content: String,
        mood: String,
        aiSummary: String?,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
        tagNames: String,
    ): DiaryTimelineItem = DiaryTimelineItem(
        entry = DiaryEntry(
            id = id,
            userId = userId,
            title = title,
            content = content,
            mood = DiaryMood.fromStorage(mood),
            aiSummary = aiSummary,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs,
            version = version,
            lastModifiedByDeviceId = lastModifiedByDeviceId,
        ),
        tags = tagNames.decodeCsv(),
    )
}

class SqlDelightScheduleRepository(
    database: MindWeaveDatabase,
    private val outboxRecorder: SqlOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ScheduleRepository {
    private val queries: ScheduleEventQueries = database.scheduleEventQueries

    override fun observeUpcoming(userId: String): Flow<List<ScheduleEvent>> =
        queries.selectActiveScheduleEvents(userId, ::mapScheduleEvent)
            .asFlow()
            .mapToList(Dispatchers.Default)

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
        queries.upsertScheduleEvent(
            id = event.id,
            user_id = event.userId,
            title = event.title,
            description = event.description,
            start_time_epoch_ms = event.startTimeEpochMs,
            end_time_epoch_ms = event.endTimeEpochMs,
            remind_at_epoch_ms = event.remindAtEpochMs,
            type = event.type.name,
            created_at_epoch_ms = event.createdAtEpochMs,
            updated_at_epoch_ms = event.updatedAtEpochMs,
            deleted_at_epoch_ms = event.deletedAtEpochMs,
            version = event.version,
            last_modified_by_device_id = event.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.SCHEDULE_EVENT,
                entityId = event.id,
                operation = if (event.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(event),
                createdAtEpochMs = event.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getById(id: String): ScheduleEvent? =
        queries.selectScheduleEventById(id, ::mapScheduleEvent).executeAsOneOrNull()

    override suspend fun getUpcoming(userId: String, fromEpochMs: Long, limit: Long): List<ScheduleEvent> =
        queries.selectUpcomingScheduleEvents(userId, fromEpochMs, limit, ::mapScheduleEvent).executeAsList()

    override suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long) {
        val existing = getById(id) ?: return
        queries.softDeleteScheduleEvent(
            deleted_at_epoch_ms = deletedAtEpochMs,
            updated_at_epoch_ms = deletedAtEpochMs,
            version = nextVersion,
            last_modified_by_device_id = deviceId,
            id = id,
        )
        outboxRecorder.record(
            entityType = EntityType.SCHEDULE_EVENT,
            entityId = id,
            operation = SyncOperation.SOFT_DELETE,
            payload = MindWeaveJson.encodeToString(
                existing.copy(
                    deletedAtEpochMs = deletedAtEpochMs,
                    updatedAtEpochMs = deletedAtEpochMs,
                    version = nextVersion,
                    lastModifiedByDeviceId = deviceId,
                ),
            ),
            createdAtEpochMs = deletedAtEpochMs,
        )
    }

    override suspend fun countActive(userId: String): Long =
        queries.countActiveScheduleEvents(userId).executeAsOne()

    private fun mapScheduleEvent(
        id: String,
        userId: String,
        title: String,
        description: String,
        startTimeEpochMs: Long,
        endTimeEpochMs: Long,
        remindAtEpochMs: Long?,
        type: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
    ): ScheduleEvent = ScheduleEvent(
        id = id,
        userId = userId,
        title = title,
        description = description,
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        remindAtEpochMs = remindAtEpochMs,
        type = ScheduleType.fromStorage(type),
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        version = version,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )
}

class SqlDelightTagRepository(
    database: MindWeaveDatabase,
    private val outboxRecorder: SqlOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : TagRepository {
    private val queries: TagQueries = database.tagQueries

    override fun observeTags(userId: String): Flow<List<Tag>> =
        queries.selectActiveTags(userId, ::mapTag)
            .asFlow()
            .mapToList(Dispatchers.Default)

    override suspend fun ensureTags(userId: String, deviceId: String, names: List<String>): List<Tag> {
        val normalized = names.normalizeTags()
        if (normalized.isEmpty()) return emptyList()

        val existing = queries.selectActiveTags(userId, ::mapTag).executeAsList()
            .associateBy { it.name.lowercase() }
            .toMutableMap()

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
        val existingLinks = queries.selectActiveDiaryEntryTagLinksForEntry(entryId, ::mapDiaryEntryTag).executeAsList()
        val existingTags = getTagsForDiary(entryId).associateBy { it.id }
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
                queries.softDeleteDiaryEntryTag(
                    deleted_at_epoch_ms = deletedLink.deletedAtEpochMs,
                    updated_at_epoch_ms = deletedLink.updatedAtEpochMs,
                    version = deletedLink.version,
                    last_modified_by_device_id = deletedLink.lastModifiedByDeviceId,
                    entry_id = deletedLink.entryId,
                    tag_id = deletedLink.tagId,
                )
                outboxRecorder.record(
                    entityType = EntityType.DIARY_ENTRY_TAG,
                    entityId = deletedLink.id,
                    operation = SyncOperation.SOFT_DELETE,
                    payload = MindWeaveJson.encodeToString(deletedLink),
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
        queries.selectActiveTagsForEntry(entryId, ::mapTag).executeAsList()

    override suspend fun getTagById(id: String): Tag? =
        queries.selectTagById(id, ::mapTag).executeAsOneOrNull()

    override suspend fun upsertTag(tag: Tag, trackSync: Boolean) {
        queries.upsertTag(
            id = tag.id,
            user_id = tag.userId,
            name = tag.name,
            created_at_epoch_ms = tag.createdAtEpochMs,
            updated_at_epoch_ms = tag.updatedAtEpochMs,
            deleted_at_epoch_ms = tag.deletedAtEpochMs,
            version = tag.version,
            last_modified_by_device_id = tag.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.TAG,
                entityId = tag.id,
                operation = if (tag.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(tag),
                createdAtEpochMs = tag.updatedAtEpochMs,
            )
        }
    }

    override suspend fun upsertDiaryEntryTag(link: DiaryEntryTag, trackSync: Boolean) {
        queries.upsertDiaryEntryTag(
            id = link.id,
            user_id = link.userId,
            entry_id = link.entryId,
            tag_id = link.tagId,
            created_at_epoch_ms = link.createdAtEpochMs,
            updated_at_epoch_ms = link.updatedAtEpochMs,
            deleted_at_epoch_ms = link.deletedAtEpochMs,
            version = link.version,
            last_modified_by_device_id = link.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.DIARY_ENTRY_TAG,
                entityId = link.id,
                operation = if (link.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(link),
                createdAtEpochMs = link.updatedAtEpochMs,
            )
        }
    }

    private fun mapTag(
        id: String,
        userId: String,
        name: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
    ): Tag = Tag(
        id = id,
        userId = userId,
        name = name,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        version = version,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )

    private fun mapDiaryEntryTag(
        id: String,
        userId: String,
        entryId: String,
        tagId: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
    ): DiaryEntryTag = DiaryEntryTag(
        id = id,
        userId = userId,
        entryId = entryId,
        tagId = tagId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        version = version,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )
}

class SqlDelightChatRepository(
    database: MindWeaveDatabase,
    private val outboxRecorder: SqlOutboxRecorder,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ChatRepository {
    private val queries: ChatQueries = database.chatQueries

    override fun observeSessions(userId: String): Flow<List<ChatSession>> =
        queries.selectActiveChatSessions(userId, ::mapChatSession)
            .asFlow()
            .mapToList(Dispatchers.Default)

    override fun observeConversation(sessionId: String): Flow<List<ChatMessage>> =
        queries.selectMessagesBySession(sessionId, ::mapChatMessage)
            .asFlow()
            .mapToList(Dispatchers.Default)

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
        queries.upsertChatSession(
            id = session.id,
            user_id = session.userId,
            title = session.title,
            created_at_epoch_ms = session.createdAtEpochMs,
            updated_at_epoch_ms = session.updatedAtEpochMs,
            deleted_at_epoch_ms = session.deletedAtEpochMs,
            version = session.version,
            last_modified_by_device_id = session.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.CHAT_SESSION,
                entityId = session.id,
                operation = if (session.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(session),
                createdAtEpochMs = session.updatedAtEpochMs,
            )
        }
    }

    override suspend fun upsertMessage(message: ChatMessage, trackSync: Boolean) {
        queries.upsertChatMessage(
            id = message.id,
            session_id = message.sessionId,
            user_id = message.userId,
            role = message.role.name,
            content = message.content,
            created_at_epoch_ms = message.createdAtEpochMs,
            updated_at_epoch_ms = message.updatedAtEpochMs,
            deleted_at_epoch_ms = message.deletedAtEpochMs,
            version = message.version,
            last_modified_by_device_id = message.lastModifiedByDeviceId,
        )
        if (trackSync) {
            outboxRecorder.record(
                entityType = EntityType.CHAT_MESSAGE,
                entityId = message.id,
                operation = if (message.deletedAtEpochMs == null) SyncOperation.UPSERT else SyncOperation.SOFT_DELETE,
                payload = MindWeaveJson.encodeToString(message),
                createdAtEpochMs = message.updatedAtEpochMs,
            )
        }
    }

    override suspend fun getSessionById(id: String): ChatSession? =
        queries.selectChatSessionById(id, ::mapChatSession).executeAsOneOrNull()

    override suspend fun getMessageById(id: String): ChatMessage? =
        queries.selectChatMessageById(id, ::mapChatMessage).executeAsOneOrNull()

    override suspend fun getRecentMessages(userId: String, limit: Long): List<ChatMessage> =
        queries.selectRecentChatMessages(userId, limit, ::mapChatMessage).executeAsList()

    override suspend fun getConversation(sessionId: String): ChatConversation? {
        val session = getSessionById(sessionId) ?: return null
        val messages = queries.selectMessagesBySession(sessionId, ::mapChatMessage).executeAsList()
        return ChatConversation(session = session, messages = messages)
    }

    private fun mapChatSession(
        id: String,
        userId: String,
        title: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
    ): ChatSession = ChatSession(
        id = id,
        userId = userId,
        title = title,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        version = version,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )

    private fun mapChatMessage(
        id: String,
        sessionId: String,
        userId: String,
        role: String,
        content: String,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        version: Long,
        lastModifiedByDeviceId: String,
    ): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        userId = userId,
        role = ChatRole.fromStorage(role),
        content = content,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        deletedAtEpochMs = deletedAtEpochMs,
        version = version,
        lastModifiedByDeviceId = lastModifiedByDeviceId,
    )
}

class SqlDelightSyncRepository(
    database: MindWeaveDatabase,
) : SyncRepository {
    private val queries: SyncQueries = database.syncQueries

    override fun observeSyncState(): Flow<SyncState> {
        val pendingFlow = queries.countPendingOutboxChanges()
            .asFlow()
            .mapToOne(Dispatchers.Default)
        val seqFlow = queries.selectSyncMetadata(LAST_SYNC_SEQ_KEY)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
        return combine(pendingFlow, seqFlow) { pending, seqValue ->
            SyncState(
                pendingChanges = pending,
                lastSyncSeq = seqValue?.toLongOrNull() ?: 0L,
            )
        }
    }

    override suspend fun enqueue(change: OutboxChange) {
        queries.insertOutboxChange(
            id = change.id,
            entity_type = change.entityType.name,
            entity_id = change.entityId,
            operation = change.operation.name,
            payload = change.payload,
            created_at_epoch_ms = change.createdAtEpochMs,
            retry_count = change.retryCount,
            status = change.status.name,
        )
    }

    override suspend fun pendingChanges(): List<OutboxChange> =
        queries.selectPendingOutboxChanges(::mapOutboxChange).executeAsList()

    override suspend fun markSynced(changeId: String) {
        queries.markOutboxChangeStatus(
            retry_count = 0,
            status = OutboxStatus.SYNCED.name,
            id = changeId,
        )
    }

    override suspend fun markFailed(changeId: String, nextRetryCount: Long) {
        queries.markOutboxChangeStatus(
            retry_count = nextRetryCount,
            status = OutboxStatus.FAILED.name,
            id = changeId,
        )
    }

    override suspend fun getLastSyncSeq(): Long =
        queries.selectSyncMetadata(LAST_SYNC_SEQ_KEY).executeAsOneOrNull()?.toLongOrNull() ?: 0L

    override suspend fun setLastSyncSeq(seq: Long) {
        queries.upsertSyncMetadata(LAST_SYNC_SEQ_KEY, seq.toString())
    }

    override suspend fun recordConflict(conflict: SyncConflictRecord) {
        queries.insertSyncConflict(
            id = conflict.id,
            entity_type = conflict.entityType.name,
            entity_id = conflict.entityId,
            local_payload = conflict.localPayload,
            remote_payload = conflict.remotePayload,
            status = conflict.status.name,
            created_at_epoch_ms = conflict.createdAtEpochMs,
        )
    }

    override suspend fun getConflicts(): List<SyncConflictRecord> =
        queries.selectSyncConflicts { id, entityType, entityId, localPayload, remotePayload, status, createdAtEpochMs ->
            SyncConflictRecord(
                id = id,
                entityType = EntityType.valueOf(entityType),
                entityId = entityId,
                localPayload = localPayload,
                remotePayload = remotePayload,
                status = ConflictStatus.valueOf(status),
                createdAtEpochMs = createdAtEpochMs,
            )
        }.executeAsList()

    private fun mapOutboxChange(
        id: String,
        entityType: String,
        entityId: String,
        operation: String,
        payload: String,
        createdAtEpochMs: Long,
        retryCount: Long,
        status: String,
    ): OutboxChange = OutboxChange(
        id = id,
        entityType = EntityType.valueOf(entityType),
        entityId = entityId,
        operation = SyncOperation.valueOf(operation),
        payload = payload,
        createdAtEpochMs = createdAtEpochMs,
        retryCount = retryCount,
        status = OutboxStatus.valueOf(status),
    )

    companion object {
        const val LAST_SYNC_SEQ_KEY: String = "last_sync_seq"
    }
}

class SqlOutboxRecorder(
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

fun createLocalRepositories(
    database: MindWeaveDatabase,
    session: AppSession,
): LocalRepositories {
    val syncRepository = SqlDelightSyncRepository(database)
    val outboxRecorder = SqlOutboxRecorder(syncRepository)
    val tagRepository = SqlDelightTagRepository(database, outboxRecorder)
    val accountRepository = SqlDelightAccountRepository(database)
    val userPreferencesRepository = SqlDelightUserPreferencesRepository(database)
    val modelPackageRepository = SqlDelightModelPackageRepository(database)
    return LocalRepositories(
        diaryRepository = SqlDelightDiaryRepository(database, tagRepository, outboxRecorder),
        scheduleRepository = SqlDelightScheduleRepository(database, outboxRecorder),
        tagRepository = tagRepository,
        chatRepository = SqlDelightChatRepository(database, outboxRecorder),
        syncRepository = syncRepository,
        accountRepository = accountRepository,
        userPreferencesRepository = userPreferencesRepository,
        modelPackageRepository = modelPackageRepository,
        session = session,
    )
}

private fun String.decodeCsv(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun List<String>.normalizeTags(): List<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
