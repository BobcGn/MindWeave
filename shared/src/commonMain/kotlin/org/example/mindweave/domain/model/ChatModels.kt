package org.example.mindweave.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM;

    companion object {
        fun fromStorage(value: String): ChatRole = entries.firstOrNull { it.name == value } ?: USER
    }
}

@Serializable
data class ChatSession(
    override val id: String,
    override val userId: String,
    val title: String,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class ChatMessage(
    override val id: String,
    val sessionId: String,
    override val userId: String,
    val role: ChatRole,
    val content: String,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class ChatConversation(
    val session: ChatSession,
    val messages: List<ChatMessage>,
)
