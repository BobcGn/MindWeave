package org.example.mindweave.ai

import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ScheduleRepository

class ChatContextAssembler(
    private val diaryRepository: DiaryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val chatRepository: ChatRepository,
) {
    private val delegate = ContextAssembler(
        diaryRetriever = DiaryRetriever(diaryRepository),
        scheduleRetriever = ScheduleRetriever(scheduleRepository),
        chatHistoryRetriever = ChatHistoryRetriever(chatRepository),
    )

    suspend fun build(
        userId: String,
        sessionId: String?,
        userPreferences: List<String> = emptyList(),
    ) = delegate.assemble(
        userId = userId,
        sessionId = sessionId,
        userPreferences = userPreferences,
    )
}
