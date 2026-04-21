package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiResponse
import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.domain.ai.ConversationSummary

interface AiAgent {
    val sourceLabel: String

    suspend fun chat(request: AiRequest): AiResponse

    suspend fun summarize(context: ChatContext): ConversationSummary
}

expect fun createAiAgent(
    settings: AiSettings,
    modelManager: ModelManager,
): AiAgent
