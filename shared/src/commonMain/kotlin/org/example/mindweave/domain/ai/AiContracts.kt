package org.example.mindweave.domain.ai

import kotlinx.serialization.Serializable
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.ScheduleEvent

@Serializable
enum class AiMode {
    CHAT,
    DAILY_SUMMARY,
    WEEKLY_SUMMARY,
    EMOTION_REFLECTION,
    SCHEDULE_SUGGESTION,
}

@Serializable
data class ChatContext(
    val recentDiaries: List<DiaryTimelineItem>,
    val upcomingEvents: List<ScheduleEvent>,
    val recentMessages: List<ChatMessage>,
    val userPreferences: List<String> = emptyList(),
)

@Serializable
data class AiRequest(
    val mode: AiMode,
    val prompt: String,
    val context: ChatContext,
)

@Serializable
data class AiResponse(
    val text: String,
    val source: String,
    val suggestedActions: List<String> = emptyList(),
)

@Serializable
data class ConversationSummary(
    val headline: String,
    val summary: String,
    val actionItems: List<String>,
    val source: String,
)

@Serializable
data class AiChatRequest(
    val prompt: String,
    val context: ChatContext,
)

@Serializable
data class AiChatResponse(
    val text: String,
    val source: String,
)
