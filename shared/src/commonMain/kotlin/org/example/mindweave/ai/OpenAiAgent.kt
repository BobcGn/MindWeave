package org.example.mindweave.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.example.mindweave.domain.ai.AiChatRequest
import org.example.mindweave.domain.ai.AiChatResponse
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.ai.ChatContext

class KtorCloudEnhancementGateway(
    private val baseUrl: String?,
    private val client: HttpClient,
) : CloudEnhancementGateway {
    override suspend fun enhance(
        task: AiTask,
        prompt: String,
        context: ChatContext,
    ): CloudEnhancementPayload {
        val resolvedBaseUrl = baseUrl?.trim()?.ifBlank { null }
            ?: error("Cloud enhancement endpoint is not configured.")
        val response = client.post("${resolvedBaseUrl.trimEnd('/')}/ai/chat") {
            setBody(
                AiChatRequest(
                    prompt = buildString {
                        appendLine("任务：${task.label}")
                        append(prompt)
                    },
                    context = context,
                ),
            )
        }.body<AiChatResponse>()
        return CloudEnhancementPayload(
            text = response.text,
            source = response.source,
        )
    }
}
