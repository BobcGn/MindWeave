package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiResponse
import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.domain.ai.ConversationSummary
import org.example.mindweave.domain.ai.Summary

class OfflineAiAgent(
    modelManager: ModelManager,
) : AiAgent {
    private val engine = HybridAiEngine(
        settings = AiSettings.LocalOnly(),
        modelManager = modelManager,
        router = AiRouter(modelManager),
        localEngine = LocalAiEngine(
            modelManager = modelManager,
            promptBuilder = PromptBuilder(),
        ),
        cloudEngine = CloudAiEngine(
            gateway = object : CloudEnhancementGateway {
                override suspend fun enhance(
                    task: org.example.mindweave.domain.ai.AiTask,
                    prompt: String,
                    context: ChatContext,
                ): CloudEnhancementPayload = error("Offline AI does not allow cloud enhancement.")
            },
            promptBuilder = PromptBuilder(),
        ),
    )

    override val sourceLabel: String = "Offline AI"

    override suspend fun chat(request: AiRequest): AiResponse =
        engine.execute(request.copy(allowCloudEnhancement = false))

    override suspend fun summarize(context: ChatContext): ConversationSummary {
        val response = engine.execute(
            AiRequest(
                task = org.example.mindweave.domain.ai.AiTask.CONVERSATION_SUMMARY,
                prompt = "请使用仅本地能力做总结。",
                context = context,
                allowCloudEnhancement = false,
            ),
        )
        return response.summary ?: Summary(
            headline = "离线总结",
            body = response.text,
            actionItems = response.suggestedActions,
        )
    }
}
