package org.example.mindweave.domain.ai

import kotlinx.serialization.Serializable
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.ScheduleEvent

@Serializable
enum class AiTaskTier {
    RULES,
    LIGHTWEIGHT_MODEL,
    GENERATIVE_MODEL,
}

@Serializable
enum class AiTask(
    val tier: AiTaskTier,
    val cloudEnhancementEligible: Boolean,
    val label: String,
) {
    CHAT_REPLY(
        tier = AiTaskTier.GENERATIVE_MODEL,
        cloudEnhancementEligible = true,
        label = "对话回复",
    ),
    DIARY_SUMMARY(
        tier = AiTaskTier.GENERATIVE_MODEL,
        cloudEnhancementEligible = true,
        label = "日记总结",
    ),
    CONVERSATION_SUMMARY(
        tier = AiTaskTier.GENERATIVE_MODEL,
        cloudEnhancementEligible = true,
        label = "会话总结",
    ),
    EMOTION_CLASSIFICATION(
        tier = AiTaskTier.LIGHTWEIGHT_MODEL,
        cloudEnhancementEligible = false,
        label = "情绪分类",
    ),
    DIARY_EMBEDDING(
        tier = AiTaskTier.LIGHTWEIGHT_MODEL,
        cloudEnhancementEligible = false,
        label = "日记向量化",
    ),
    SCHEDULE_PRIORITIZATION(
        tier = AiTaskTier.RULES,
        cloudEnhancementEligible = false,
        label = "日程优先级整理",
    ),
}

@Serializable
data class ChatContext(
    val recentDiaries: List<DiaryTimelineItem>,
    val upcomingEvents: List<ScheduleEvent>,
    val recentMessages: List<ChatMessage>,
    val userPreferences: List<String> = emptyList(),
    val assembledAtEpochMs: Long,
)

@Serializable
data class Summary(
    val headline: String,
    val body: String,
    val actionItems: List<String> = emptyList(),
    val confidence: Double? = null,
)

@Serializable
data class Classification(
    val label: String,
    val confidence: Double,
    val rationale: String? = null,
)

@Serializable
data class Embedding(
    val values: List<Float>,
    val dimension: Int = values.size,
)

typealias ConversationSummary = Summary

@Serializable
data class AiRequest(
    val task: AiTask,
    val prompt: String,
    val context: ChatContext,
    val allowCloudEnhancement: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class AiResponse(
    val task: AiTask,
    val text: String,
    val source: String,
    val summary: Summary? = null,
    val classification: Classification? = null,
    val embedding: Embedding? = null,
    val suggestedActions: List<String> = emptyList(),
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
