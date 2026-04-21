package org.example.mindweave.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.example.mindweave.data.local.createLocalRepositories
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.util.currentEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextAssemblerTest {
    @Test
    fun contextAssemblerShouldCollectLocalDiaryScheduleAndChatState() = runTest {
        val session = AppSession("user-ctx", "device-a", "Device A")
        val repositories = createLocalRepositories(
            database = createDatabase(),
            session = session,
        )

        repositories.diaryRepository.createDraft(
            userId = session.userId,
            deviceId = session.deviceId,
            draft = DiaryDraft(
                title = "ctx-title",
                content = "ctx-body",
                mood = DiaryMood.CALM,
                tags = listOf("ctx"),
            ),
        )
        repositories.scheduleRepository.createDraft(
            userId = session.userId,
            deviceId = session.deviceId,
            draft = ScheduleDraft(
                title = "ctx-event",
                description = "ctx-desc",
                startTimeEpochMs = currentEpochMillis() + 1_000L,
                endTimeEpochMs = currentEpochMillis() + 3_000L,
                remindAtEpochMs = null,
                type = ScheduleType.WORK,
            ),
        )
        val sessionId = repositories.chatRepository.createSession(session.userId, session.deviceId, "ctx-session").id
        repositories.chatRepository.appendMessage(
            sessionId = sessionId,
            userId = session.userId,
            deviceId = session.deviceId,
            role = ChatRole.USER,
            content = "ctx-message",
        )

        val assembler = ContextAssembler(
            diaryRetriever = DiaryRetriever(repositories.diaryRepository),
            scheduleRetriever = ScheduleRetriever(repositories.scheduleRepository),
            chatHistoryRetriever = ChatHistoryRetriever(repositories.chatRepository),
            nowProvider = { 99L },
        )

        val context = assembler.assemble(
            userId = session.userId,
            sessionId = sessionId,
            userPreferences = listOf("AI 模式：仅本地"),
        )

        assertEquals(1, context.recentDiaries.size)
        assertEquals(1, context.upcomingEvents.size)
        assertEquals(1, context.recentMessages.size)
        assertEquals(99L, context.assembledAtEpochMs)
        assertEquals(listOf("AI 模式：仅本地"), context.userPreferences)
    }

    @Test
    fun promptBuilderShouldIncludeTaskContextAndPreferences() {
        val prompt = PromptBuilder().buildPrompt(
            AiRequest(
                task = AiTask.DIARY_SUMMARY,
                prompt = "请帮我总结",
                context = org.example.mindweave.domain.ai.ChatContext(
                    recentDiaries = emptyList(),
                    upcomingEvents = emptyList(),
                    recentMessages = emptyList(),
                    userPreferences = listOf("AI 模式：本地优先"),
                    assembledAtEpochMs = 5L,
                ),
            ),
        )

        assertTrue(prompt.contains("任务：日记总结"))
        assertTrue(prompt.contains("用户输入：请帮我总结"))
        assertTrue(prompt.contains("AI 模式：本地优先"))
    }

    private fun createDatabase(): MindWeaveDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MindWeaveDatabase.Schema.create(driver)
        return MindWeaveDatabase(driver)
    }
}
