package org.example.mindweave.sync

import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.ConflictStatus
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.SyncConflictRecord
import org.example.mindweave.domain.model.SyncableEntity
import org.example.mindweave.domain.model.Tag
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.repository.SyncRepository
import org.example.mindweave.repository.TagRepository
import org.example.mindweave.util.IdGenerator
import org.example.mindweave.util.currentEpochMillis
import org.example.mindweave.util.decodeChatMessage
import org.example.mindweave.util.decodeChatSession
import org.example.mindweave.util.decodeDiaryEntry
import org.example.mindweave.util.decodeDiaryEntryTag
import org.example.mindweave.util.decodeScheduleEvent
import org.example.mindweave.util.decodeTag
import org.example.mindweave.util.encodeSyncableEntity

class LocalChangeApplier(
    private val diaryRepository: DiaryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val tagRepository: TagRepository,
    private val chatRepository: ChatRepository,
    private val syncRepository: SyncRepository,
    private val resolver: LwwConflictResolver = LwwConflictResolver(),
    private val nowProvider: () -> Long = ::currentEpochMillis,
) {
    suspend fun apply(change: RemoteChangeEnvelope) {
        when (change.entityType) {
            EntityType.DIARY_ENTRY -> {
                val remote = decodeDiaryEntry(change.payload)
                applyEntity(
                    local = diaryRepository.getById(remote.id)?.entry,
                    remote = remote,
                    remotePayload = change.payload,
                ) { diaryRepository.upsert(remote, trackSync = false) }
            }

            EntityType.SCHEDULE_EVENT -> {
                val remote = decodeScheduleEvent(change.payload)
                applyEntity(
                    local = scheduleRepository.getById(remote.id),
                    remote = remote,
                    remotePayload = change.payload,
                ) { scheduleRepository.upsert(remote, trackSync = false) }
            }

            EntityType.TAG -> {
                val remote = decodeTag(change.payload)
                applyEntity(
                    local = tagRepository.getTagById(remote.id),
                    remote = remote,
                    remotePayload = change.payload,
                ) { tagRepository.upsertTag(remote, trackSync = false) }
            }

            EntityType.DIARY_ENTRY_TAG -> {
                val remote = decodeDiaryEntryTag(change.payload)
                tagRepository.upsertDiaryEntryTag(remote, trackSync = false)
            }

            EntityType.CHAT_SESSION -> {
                val remote = decodeChatSession(change.payload)
                applyEntity(
                    local = chatRepository.getSessionById(remote.id),
                    remote = remote,
                    remotePayload = change.payload,
                ) { chatRepository.upsertSession(remote, trackSync = false) }
            }

            EntityType.CHAT_MESSAGE -> {
                val remote = decodeChatMessage(change.payload)
                applyEntity(
                    local = chatRepository.getMessageById(remote.id),
                    remote = remote,
                    remotePayload = change.payload,
                ) { chatRepository.upsertMessage(remote, trackSync = false) }
            }
        }
    }

    private suspend fun <T : SyncableEntity> applyEntity(
        local: T?,
        remote: T,
        remotePayload: String,
        applyRemote: suspend () -> Unit,
    ) {
        if (resolver.shouldApplyRemote(local, remote)) {
            applyRemote()
            return
        }

        val conflict = SyncConflictRecord(
            id = IdGenerator.next("conflict", nowProvider()),
            entityType = remote.toEntityType(),
            entityId = remote.id,
            localPayload = local?.let(::encodeSyncableEntity) ?: "",
            remotePayload = remotePayload,
            status = ConflictStatus.DETECTED,
            createdAtEpochMs = nowProvider(),
        )
        syncRepository.recordConflict(conflict)
    }
}

private fun SyncableEntity.toEntityType(): EntityType = when (this) {
    is DiaryEntry -> EntityType.DIARY_ENTRY
    is ScheduleEvent -> EntityType.SCHEDULE_EVENT
    is Tag -> EntityType.TAG
    is DiaryEntryTag -> EntityType.DIARY_ENTRY_TAG
    is ChatSession -> EntityType.CHAT_SESSION
    is ChatMessage -> EntityType.CHAT_MESSAGE
    else -> error("Unsupported entity type ${this::class.simpleName}")
}
