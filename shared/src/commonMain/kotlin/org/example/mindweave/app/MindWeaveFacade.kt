package org.example.mindweave.app

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.ai.AiAgent
import org.example.mindweave.ai.ChatContextAssembler
import org.example.mindweave.domain.ai.AiMode
import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.repository.SyncRepository
import org.example.mindweave.repository.TagRepository
import org.example.mindweave.sync.SyncManager
import org.example.mindweave.util.currentEpochMillis

class MindWeaveFacade(
    val session: AppSession,
    private val diaryRepository: DiaryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val tagRepository: TagRepository,
    private val chatRepository: ChatRepository,
    private val syncRepository: SyncRepository,
    private val aiAgent: AiAgent,
    private val contextAssembler: ChatContextAssembler,
    private val syncManager: SyncManager?,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) {
    fun observeTimeline(): Flow<List<DiaryTimelineItem>> = diaryRepository.observeTimeline(session.userId)

    fun observeSchedules(): Flow<List<ScheduleEvent>> = scheduleRepository.observeUpcoming(session.userId)

    fun observeSessions(): Flow<List<ChatSession>> = chatRepository.observeSessions(session.userId)

    fun observeConversation(sessionId: String): Flow<List<ChatMessage>> = chatRepository.observeConversation(sessionId)

    fun observeSyncState(): Flow<SyncState> = syncRepository.observeSyncState()

    suspend fun captureDiary(draft: DiaryDraft): DiaryTimelineItem {
        val entry = diaryRepository.createDraft(session.userId, session.deviceId, draft)
        val context = contextAssembler.build(session.userId, sessionId = null)
        val summary = aiAgent.summarize(context)
        val enriched = entry.copy(
            aiSummary = summary.summary,
            updatedAtEpochMs = nowProvider(),
            version = entry.version + 1,
            lastModifiedByDeviceId = session.deviceId,
        )
        diaryRepository.upsert(enriched, trackSync = true)
        return diaryRepository.getById(entry.id) ?: DiaryTimelineItem(enriched, draft.tags)
    }

    suspend fun captureSchedule(draft: ScheduleDraft): ScheduleEvent =
        scheduleRepository.createDraft(session.userId, session.deviceId, draft)

    suspend fun sendChatMessage(existingSessionId: String?, prompt: String): String {
        val sessionId = existingSessionId ?: chatRepository.createSession(
            userId = session.userId,
            deviceId = session.deviceId,
            title = prompt.take(16),
        ).id

        chatRepository.appendMessage(
            sessionId = sessionId,
            userId = session.userId,
            deviceId = session.deviceId,
            role = ChatRole.USER,
            content = prompt,
        )

        val context = contextAssembler.build(session.userId, sessionId)
        val response = aiAgent.chat(
            AiRequest(
                mode = AiMode.CHAT,
                prompt = prompt,
                context = context,
            ),
        )
        chatRepository.appendMessage(
            sessionId = sessionId,
            userId = session.userId,
            deviceId = session.deviceId,
            role = ChatRole.ASSISTANT,
            content = response.text,
        )
        return sessionId
    }

    suspend fun runSync() = syncManager?.synchronize(session)

    suspend fun seedDemoData() {
        if (diaryRepository.countActive(session.userId) == 0L) {
            captureDiary(
                DiaryDraft(
                    title = "重新找回节奏的一天",
                    content = "上午开会时状态有点散，午后把通知都关掉以后，终于把核心任务收拢清楚。晚上散步时，觉得今天没有想象中那么糟。",
                    mood = DiaryMood.CALM,
                    tags = listOf("工作", "专注", "散步"),
                ),
            )
            captureDiary(
                DiaryDraft(
                    title = "压力开始顶上来了",
                    content = "今天待办很多，看到消息提示就紧张。真正卡住我的不是任务本身，而是不知道先从哪一件开始。",
                    mood = DiaryMood.HEAVY,
                    tags = listOf("压力", "排序", "边界"),
                ),
            )
        }
        if (scheduleRepository.countActive(session.userId) == 0L) {
            val now = nowProvider()
            captureSchedule(
                ScheduleDraft(
                    title = "晚上散步 30 分钟",
                    description = "给大脑降噪，不带工作消息。",
                    startTimeEpochMs = now + 2 * 60 * 60 * 1000,
                    endTimeEpochMs = now + 150 * 60 * 1000,
                    remindAtEpochMs = now + 90 * 60 * 1000,
                    type = ScheduleType.HEALTH,
                ),
            )
            captureSchedule(
                ScheduleDraft(
                    title = "梳理本周最重要的一件事",
                    description = "只写一个优先级，不做完整计划。",
                    startTimeEpochMs = now + 24 * 60 * 60 * 1000,
                    endTimeEpochMs = now + 25 * 60 * 60 * 1000,
                    remindAtEpochMs = now + 23 * 60 * 60 * 1000,
                    type = ScheduleType.REFLECTION,
                ),
            )
        }
        if (chatRepository.getRecentMessages(session.userId, 1).isEmpty()) {
            sendChatMessage(existingSessionId = null, prompt = "帮我把这周的状态先梳理一下。")
        }
    }
}
