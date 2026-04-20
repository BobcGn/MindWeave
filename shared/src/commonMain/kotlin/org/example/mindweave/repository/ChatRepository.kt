package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.ChatConversation
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession

interface ChatRepository {
    fun observeSessions(userId: String): Flow<List<ChatSession>>

    fun observeConversation(sessionId: String): Flow<List<ChatMessage>>

    suspend fun createSession(userId: String, deviceId: String, title: String): ChatSession

    suspend fun appendMessage(
        sessionId: String,
        userId: String,
        deviceId: String,
        role: ChatRole,
        content: String,
    ): ChatMessage

    suspend fun upsertSession(session: ChatSession, trackSync: Boolean = true)

    suspend fun upsertMessage(message: ChatMessage, trackSync: Boolean = true)

    suspend fun getSessionById(id: String): ChatSession?

    suspend fun getMessageById(id: String): ChatMessage?

    suspend fun getRecentMessages(userId: String, limit: Long): List<ChatMessage>

    suspend fun getConversation(sessionId: String): ChatConversation?
}
