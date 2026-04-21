package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiResponse
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.ai.Classification
import org.example.mindweave.domain.ai.ConversationSummary
import org.example.mindweave.domain.ai.Embedding
import org.example.mindweave.domain.ai.Summary
import org.example.mindweave.domain.model.DiaryMood

interface AiEngine {
    val engineId: String

    suspend fun execute(request: AiRequest): AiResponse
}

class LocalAiEngine(
    private val modelManager: ModelManager,
    private val promptBuilder: PromptBuilder,
) : AiEngine {
    override val engineId: String = "local-ai"

    override suspend fun execute(request: AiRequest): AiResponse =
        execute(request, defaultRouteFor(request.task))

    suspend fun execute(
        request: AiRequest,
        route: AiExecutionRoute,
    ): AiResponse = when (route) {
        AiExecutionRoute.DISABLED -> disabledResponse(request)
        AiExecutionRoute.LOCAL_RULES -> executeRules(request)
        AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL -> executeLightweight(request)
        AiExecutionRoute.LOCAL_GENERATIVE_MODEL -> executeGenerative(request)
        AiExecutionRoute.CLOUD_ENHANCEMENT -> executeLightweight(request)
    }

    private fun defaultRouteFor(task: AiTask): AiExecutionRoute = when (task.tier) {
        org.example.mindweave.domain.ai.AiTaskTier.RULES -> AiExecutionRoute.LOCAL_RULES
        org.example.mindweave.domain.ai.AiTaskTier.LIGHTWEIGHT_MODEL -> AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL
        org.example.mindweave.domain.ai.AiTaskTier.GENERATIVE_MODEL -> AiExecutionRoute.LOCAL_GENERATIVE_MODEL
    }

    private suspend fun executeRules(request: AiRequest): AiResponse = when (request.task) {
        AiTask.SCHEDULE_PRIORITIZATION -> {
            val nextTitle = request.context.upcomingEvents.minByOrNull { it.startTimeEpochMs }?.title ?: "暂无安排"
            AiResponse(
                task = request.task,
                text = "规则引擎建议先处理最早到来的事项：$nextTitle。",
                source = "Local Rules",
                summary = Summary(
                    headline = "规则排序结果",
                    body = "优先关注最临近且描述最明确的日程，避免同时打开过多上下文。",
                    actionItems = listOf("先确认 $nextTitle 的准备工作。"),
                ),
                suggestedActions = listOf("只保留一项最高优先级任务。"),
            )
        }

        AiTask.EMOTION_CLASSIFICATION -> {
            val mood = inferMood(request.prompt, request.context)
            AiResponse(
                task = request.task,
                text = "规则分类结果：${mood.label}",
                source = "Local Rules",
                classification = Classification(
                    label = mood.name,
                    confidence = 0.55,
                    rationale = "基于最近情绪记录与关键词进行规则推断。",
                ),
            )
        }

        AiTask.DIARY_EMBEDDING -> {
            val embedding = buildEmbedding(promptBuilder.buildPrompt(request))
            AiResponse(
                task = request.task,
                text = "规则向量降级已生成 ${embedding.dimension} 维表示。",
                source = "Local Rules",
                embedding = embedding,
            )
        }

        AiTask.DIARY_SUMMARY,
        AiTask.CONVERSATION_SUMMARY,
        AiTask.CHAT_REPLY,
        -> {
            val summary = buildRuleSummary(request)
            AiResponse(
                task = request.task,
                text = summary.body,
                source = "Local Rules",
                summary = summary,
                suggestedActions = summary.actionItems,
            )
        }
    }

    private suspend fun executeLightweight(request: AiRequest): AiResponse = when (request.task) {
        AiTask.EMOTION_CLASSIFICATION -> {
            val mood = inferMood(request.prompt, request.context)
            AiResponse(
                task = request.task,
                text = "本地轻模型判断当前情绪更接近 ${mood.label}。",
                source = "Local Lightweight Model",
                classification = Classification(
                    label = mood.name,
                    confidence = 0.78,
                    rationale = "轻量分类器综合最近日记情绪和输入语义。",
                ),
            )
        }

        AiTask.DIARY_EMBEDDING -> {
            val embedding = buildEmbedding(promptBuilder.buildPrompt(request), scale = 1.7f)
            AiResponse(
                task = request.task,
                text = "本地轻模型已生成 ${embedding.dimension} 维向量。",
                source = "Local Lightweight Model",
                embedding = embedding,
            )
        }

        AiTask.DIARY_SUMMARY,
        AiTask.CONVERSATION_SUMMARY,
        AiTask.CHAT_REPLY,
        AiTask.SCHEDULE_PRIORITIZATION,
        -> {
            val summary = buildLightweightSummary(request)
            AiResponse(
                task = request.task,
                text = summary.body,
                source = "Local Lightweight Model",
                summary = summary,
                suggestedActions = summary.actionItems,
            )
        }
    }

    private suspend fun executeGenerative(request: AiRequest): AiResponse = when (request.task) {
        AiTask.CHAT_REPLY -> {
            val nextStep = request.context.upcomingEvents.firstOrNull()?.title ?: "今天最重要的一件事"
            AiResponse(
                task = request.task,
                text = "我先按本地生成策略梳理了你的上下文。围绕“${request.prompt.take(24)}”，建议先把注意力收敛到「$nextStep」，再决定是否继续扩展。",
                source = "Local Generative Model",
                suggestedActions = listOf(
                    "把 $nextStep 写成一个 15 分钟内可完成的动作。",
                    "如果需要云增强，再手动放开本次请求。",
                ),
            )
        }

        AiTask.DIARY_SUMMARY,
        AiTask.CONVERSATION_SUMMARY,
        -> {
            val summary = Summary(
                headline = "本地生成总结",
                body = "最近的记录显示，你正在从多个并行任务里寻找更稳定的节奏。优先收束任务边界，比继续扩张计划更重要。",
                actionItems = listOf(
                    "只保留一项今天必须完成的事项。",
                    "把最近一条情绪记录补充一个触发原因。",
                ),
                confidence = 0.82,
            )
            AiResponse(
                task = request.task,
                text = summary.body,
                source = "Local Generative Model",
                summary = summary,
                suggestedActions = summary.actionItems,
            )
        }

        AiTask.EMOTION_CLASSIFICATION,
        AiTask.DIARY_EMBEDDING,
        AiTask.SCHEDULE_PRIORITIZATION,
        -> executeLightweight(request)
    }

    private fun buildRuleSummary(request: AiRequest): Summary {
        val diaryCount = request.context.recentDiaries.size
        val scheduleCount = request.context.upcomingEvents.size
        return Summary(
            headline = "本地规则总结",
            body = "当前上下文包含 $diaryCount 条日记和 $scheduleCount 个日程，先处理最临近的安排，再回看情绪波动来源。",
            actionItems = listOf(
                "确认下一件最临近的安排。",
                "删掉一个不必要的次级任务。",
            ),
            confidence = 0.58,
        )
    }

    private fun buildLightweightSummary(request: AiRequest): Summary {
        val promptPreview = request.prompt.ifBlank { request.task.label }.take(24)
        return Summary(
            headline = "本地轻模型总结",
            body = "本地轻模型认为当前主题更适合被压缩成一个单点行动：围绕“$promptPreview”先做最小闭环，再决定是否继续扩展。",
            actionItems = listOf(
                "先写出一个最小闭环动作。",
                "完成后再判断是否需要更深的生成式分析。",
            ),
            confidence = 0.71,
        )
    }

    private fun inferMood(
        prompt: String,
        requestContext: org.example.mindweave.domain.ai.ChatContext,
    ): DiaryMood {
        val latestMood = requestContext.recentDiaries.firstOrNull()?.entry?.mood
        val normalizedPrompt = prompt.lowercase()
        return when {
            "累" in prompt || "压力" in prompt || "heavy" in normalizedPrompt -> DiaryMood.HEAVY
            "感谢" in prompt || "grateful" in normalizedPrompt -> DiaryMood.GRATEFUL
            "兴奋" in prompt || "energ" in normalizedPrompt -> DiaryMood.ENERGIZED
            latestMood != null -> latestMood
            else -> DiaryMood.UNCERTAIN
        }
    }

    private fun buildEmbedding(
        text: String,
        scale: Float = 1f,
    ): Embedding {
        val seed = text.ifBlank { "mindweave" }
        val values = List(8) { index ->
            val char = seed[index % seed.length]
            ((char.code % 31) / 31f) * scale
        }
        return Embedding(values = values)
    }

    private fun disabledResponse(request: AiRequest): AiResponse = AiResponse(
        task = request.task,
        text = "AI 已关闭。",
        source = "AI Disabled",
        summary = Summary(
            headline = "AI 已关闭",
            body = "当前设置不允许执行 AI 任务。",
        ),
    )
}

data class CloudEnhancementPayload(
    val text: String,
    val source: String,
)

interface CloudEnhancementGateway {
    suspend fun enhance(
        task: AiTask,
        prompt: String,
        context: org.example.mindweave.domain.ai.ChatContext,
    ): CloudEnhancementPayload
}

class CloudAiEngine(
    private val gateway: CloudEnhancementGateway,
    private val promptBuilder: PromptBuilder,
) : AiEngine {
    override val engineId: String = "cloud-ai"

    override suspend fun execute(request: AiRequest): AiResponse {
        val payload = gateway.enhance(
            task = request.task,
            prompt = promptBuilder.buildPrompt(request),
            context = request.context,
        )
        val summary = when (request.task) {
            AiTask.DIARY_SUMMARY,
            AiTask.CONVERSATION_SUMMARY,
            -> Summary(
                headline = "云增强总结",
                body = payload.text,
                actionItems = emptyList(),
                confidence = 0.88,
            )

            else -> null
        }
        return AiResponse(
            task = request.task,
            text = payload.text,
            source = payload.source,
            summary = summary,
        )
    }
}

class HybridAiEngine(
    private val settings: AiSettings,
    private val modelManager: ModelManager,
    private val router: AiRouter,
    private val localEngine: LocalAiEngine,
    private val cloudEngine: CloudAiEngine,
) : AiEngine {
    override val engineId: String = "hybrid-ai"

    override suspend fun execute(request: AiRequest): AiResponse {
        modelManager.ensureDefaultPackages(settings)
        val decision = router.decide(request, settings)
        return executeWithDecision(decision, request)
    }

    private suspend fun executeWithDecision(
        decision: RouteDecision,
        request: AiRequest,
    ): AiResponse = runCatching {
        when (decision.primaryRoute) {
            AiExecutionRoute.DISABLED -> localEngine.execute(request, AiExecutionRoute.DISABLED)
            AiExecutionRoute.LOCAL_RULES,
            AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL,
            AiExecutionRoute.LOCAL_GENERATIVE_MODEL,
            -> localEngine.execute(request, decision.primaryRoute)

            AiExecutionRoute.CLOUD_ENHANCEMENT -> cloudEngine.execute(request)
        }
    }.getOrElse {
        val fallback = decision.fallbackRoute
        if (fallback == null) {
            localEngine.execute(request, AiExecutionRoute.LOCAL_RULES)
        } else {
            when (fallback) {
                AiExecutionRoute.CLOUD_ENHANCEMENT -> runCatching {
                    cloudEngine.execute(request)
                }.getOrElse {
                    localEngine.execute(request, AiExecutionRoute.LOCAL_RULES)
                }

                AiExecutionRoute.DISABLED,
                AiExecutionRoute.LOCAL_RULES,
                AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL,
                AiExecutionRoute.LOCAL_GENERATIVE_MODEL,
                -> localEngine.execute(request, fallback)
            }
        }
    }
}

class LocalFirstAiAgent(
    private val engine: HybridAiEngine,
) : AiAgent {
    override val sourceLabel: String = "Local-first AI"

    override suspend fun chat(request: AiRequest): AiResponse =
        engine.execute(request.copy(task = AiTask.CHAT_REPLY))

    override suspend fun summarize(context: org.example.mindweave.domain.ai.ChatContext): ConversationSummary {
        val response = engine.execute(
            AiRequest(
                task = AiTask.CONVERSATION_SUMMARY,
                prompt = "请总结最近状态",
                context = context,
            ),
        )
        return response.summary ?: Summary(
            headline = "本地总结",
            body = response.text,
            actionItems = response.suggestedActions,
        )
    }
}
