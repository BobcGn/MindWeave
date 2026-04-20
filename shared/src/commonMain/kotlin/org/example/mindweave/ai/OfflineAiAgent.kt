package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiResponse
import org.example.mindweave.domain.ai.ChatContext
import org.example.mindweave.domain.ai.ConversationSummary
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.util.currentEpochMillis

class OfflineAiAgent(
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : AiAgent {
    override val sourceLabel: String = "Offline AI"

    override suspend fun chat(request: AiRequest): AiResponse {
        val firstUpcoming = request.context.upcomingEvents.firstOrNull()?.title
        val moodHint = request.context.recentDiaries.firstOrNull()?.entry?.mood ?: DiaryMood.UNCERTAIN
        val actions = buildList {
            if (firstUpcoming != null) add("先确认即将到来的「$firstUpcoming」是否需要准备。")
            add(defaultActionFor(moodHint))
        }
        return AiResponse(
            text = buildString {
                append("我先基于你本地的日记、日程和最近对话做了整理。")
                append("你现在最值得关注的主题是「")
                append(request.prompt.take(24))
                append("」。")
                append("如果你愿意，我们可以先把它拆成一个今天能完成的小动作。")
            },
            source = sourceLabel,
            suggestedActions = actions,
        )
    }

    override suspend fun summarize(context: ChatContext): ConversationSummary {
        val headline = when (context.recentDiaries.firstOrNull()?.entry?.mood) {
            DiaryMood.CALM -> "这段时间正在回到稳态"
            DiaryMood.ENERGIZED -> "这几天的推进感值得保留"
            DiaryMood.HEAVY -> "最近负荷偏高，需要主动减压"
            DiaryMood.GRATEFUL -> "最近出现了可以反复取用的微小恢复点"
            DiaryMood.UNCERTAIN, null -> "这段时间仍在寻找更清晰的节奏"
        }
        return ConversationSummary(
            headline = headline,
            summary = "最近 ${context.recentDiaries.size} 条日记、${context.upcomingEvents.size} 个待办日程和 ${context.recentMessages.size} 条对话一起指向：你更需要稳定节奏，而不是继续堆任务。",
            actionItems = listOf(
                "把明天最关键的一件事单独列出来。",
                "给最近一条沉重情绪记录加一个标签，方便后续回顾。",
            ),
            source = sourceLabel,
        )
    }

    private fun defaultActionFor(mood: DiaryMood): String = when (mood) {
        DiaryMood.CALM -> "保留今天最有效的那个习惯动作。"
        DiaryMood.ENERGIZED -> "利用当前势能，推进最难的一步。"
        DiaryMood.HEAVY -> "缩小目标，只处理一个最小动作。"
        DiaryMood.GRATEFUL -> "把让你恢复的瞬间记下来，作为下次低落时的抓手。"
        DiaryMood.UNCERTAIN -> "先写出你最担心的三件事，再删掉两件。"
    }
}
