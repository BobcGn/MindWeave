package org.example.mindweave.ai

import ai.koog.agents.core.agent.AIAgent as KoogAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import org.example.mindweave.domain.ai.AiMode
import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiResponse
import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.domain.ai.ConversationSummary

class KoogAiAgent(
    private val settings: AiSettings.OpenAI,
    private val fallback: AiAgent = OfflineAiAgent(),
) : AiAgent {
    override val sourceLabel: String = "Koog + OpenAI"

    private val agent by lazy {
        KoogAgent(
            promptExecutor = simpleOpenAIExecutor(settings.apiKey),
            llmModel = OpenAIModels.Chat.GPT4o,
            systemPrompt = """
                你是“思织”的 AI 助手。
                你服务于一个本地优先、隐私优先的日记与生活整理应用。
                你的输出应克制、具体、结构清晰，不要空泛安慰。
            """.trimIndent(),
        )
    }

    override suspend fun chat(request: AiRequest): AiResponse = runCatching {
        val result = agent.run(buildPrompt(request))
        AiResponse(
            text = result.trim(),
            source = sourceLabel,
        )
    }.getOrElse {
        fallback.chat(request)
    }

    override suspend fun summarize(context: ChatContext): ConversationSummary = runCatching {
        val prompt = buildSummaryPrompt(context)
        val result = agent.run(prompt)
        ConversationSummary(
            headline = "AI 汇总",
            summary = result.trim(),
            actionItems = emptyList(),
            source = sourceLabel,
        )
    }.getOrElse {
        fallback.summarize(context)
    }

    private fun buildPrompt(request: AiRequest): String = buildString {
        appendLine("模式：${request.mode.name}")
        appendLine("用户问题：${request.prompt}")
        appendLine("最近日记：")
        request.context.recentDiaries.forEach {
            appendLine("- ${it.entry.title} / ${it.entry.mood.label} / 标签=${it.tags.joinToString("、")}")
        }
        appendLine("即将到来的日程：")
        request.context.upcomingEvents.forEach {
            appendLine("- ${it.title} / ${it.type.label}")
        }
        appendLine("最近对话：")
        request.context.recentMessages.forEach {
            appendLine("- ${it.role.name}: ${it.content}")
        }
        if (request.mode == AiMode.CHAT) {
            appendLine("请直接给出回答，并在结尾给出 1~3 个可执行建议。")
        }
    }

    private fun buildSummaryPrompt(context: ChatContext): String = buildString {
        appendLine("请基于以下上下文，为用户生成一段今日总结。")
        context.recentDiaries.forEach {
            appendLine("日记：${it.entry.title} / ${it.entry.content}")
        }
        context.upcomingEvents.forEach {
            appendLine("日程：${it.title} ${it.description}")
        }
        appendLine("请给出一句标题、一段总结和两条行动建议。")
    }
}
