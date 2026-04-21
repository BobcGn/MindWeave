package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.util.currentEpochMillis

class DiaryRetriever(
    private val diaryRepository: DiaryRepository,
    private val limit: Long = 5,
) {
    suspend fun retrieve(userId: String) = diaryRepository.getRecent(userId, limit)
}

class ScheduleRetriever(
    private val scheduleRepository: ScheduleRepository,
    private val limit: Long = 5,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) {
    suspend fun retrieve(userId: String) = scheduleRepository.getUpcoming(
        userId = userId,
        fromEpochMs = nowProvider(),
        limit = limit,
    )
}

class ChatHistoryRetriever(
    private val chatRepository: ChatRepository,
    private val limit: Long = 8,
) {
    suspend fun retrieve(userId: String, sessionId: String?) = if (sessionId == null) {
        chatRepository.getRecentMessages(userId, limit)
    } else {
        chatRepository.getConversation(sessionId)?.messages?.takeLast(limit.toInt()).orEmpty()
    }
}

class ContextAssembler(
    private val diaryRetriever: DiaryRetriever,
    private val scheduleRetriever: ScheduleRetriever,
    private val chatHistoryRetriever: ChatHistoryRetriever,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) {
    suspend fun assemble(
        userId: String,
        sessionId: String?,
        userPreferences: List<String> = emptyList(),
    ): ChatContext = ChatContext(
        recentDiaries = diaryRetriever.retrieve(userId),
        upcomingEvents = scheduleRetriever.retrieve(userId),
        recentMessages = chatHistoryRetriever.retrieve(userId, sessionId),
        userPreferences = userPreferences,
        assembledAtEpochMs = nowProvider(),
    )
}

class PromptBuilder {
    fun buildPrompt(request: AiRequest): String = buildString {
        appendLine("任务：${request.task.label}")
        if (request.prompt.isNotBlank()) {
            appendLine("用户输入：${request.prompt}")
        }
        if (request.context.userPreferences.isNotEmpty()) {
            appendLine("用户偏好：${request.context.userPreferences.joinToString("；")}")
        }
        appendLine("最近日记：")
        request.context.recentDiaries.forEach { diary ->
            appendLine("- ${diary.entry.title} / ${diary.entry.mood.label} / ${diary.entry.content.take(80)}")
        }
        appendLine("即将到来的日程：")
        request.context.upcomingEvents.forEach { event ->
            appendLine("- ${event.title} / ${event.type.label} / ${event.description.take(60)}")
        }
        appendLine("最近对话：")
        request.context.recentMessages.forEach { message ->
            appendLine("- ${message.role.name}: ${message.content.take(80)}")
        }
        appendLine(taskTailHint(request.task))
    }

    private fun taskTailHint(task: AiTask): String = when (task) {
        AiTask.CHAT_REPLY -> "请给出简洁、可执行、隐私优先的回复。"
        AiTask.DIARY_SUMMARY -> "请输出标题、总结和 1~3 条行动建议。"
        AiTask.CONVERSATION_SUMMARY -> "请总结趋势，并给出可执行的下一步。"
        AiTask.EMOTION_CLASSIFICATION -> "请判断当前主要情绪标签。"
        AiTask.DIARY_EMBEDDING -> "请把文本归约为语义表示。"
        AiTask.SCHEDULE_PRIORITIZATION -> "请按时间与负荷给出排序建议。"
    }
}
