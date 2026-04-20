package org.example.mindweave.ai

import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.util.currentEpochMillis

class ChatContextAssembler(
    private val diaryRepository: DiaryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val chatRepository: ChatRepository,
) {
    suspend fun build(userId: String, sessionId: String?): ChatContext =
        ChatContext(
            recentDiaries = diaryRepository.getRecent(userId, limit = 5),
            upcomingEvents = scheduleRepository.getUpcoming(
                userId = userId,
                fromEpochMs = currentEpochMillis(),
                limit = 5,
            ),
            recentMessages = if (sessionId == null) {
                chatRepository.getRecentMessages(userId, limit = 8)
            } else {
                chatRepository.getConversation(sessionId)?.messages?.takeLast(8).orEmpty()
            },
        )
}
