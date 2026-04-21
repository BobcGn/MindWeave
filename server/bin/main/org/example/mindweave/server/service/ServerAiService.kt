package org.example.mindweave.server.service

import org.example.mindweave.domain.ai.AiChatRequest
import org.example.mindweave.domain.ai.AiChatResponse

class ServerAiService {
    fun chat(request: AiChatRequest): AiChatResponse {
        val diaryCount = request.context.recentDiaries.size
        val scheduleCount = request.context.upcomingEvents.size
        return AiChatResponse(
            text = "服务端 AI 编排骨架已联通。当前上下文包含 $diaryCount 条日记、$scheduleCount 个日程。围绕「${request.prompt.take(24)}」的下一步，建议先收敛成一个今天能执行的动作。",
            source = "server-stub",
        )
    }
}
