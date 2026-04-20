package org.example.mindweave.ai

sealed interface AiSettings {
    data object Disabled : AiSettings

    data class OpenAI(
        val apiKey: String,
    ) : AiSettings
}
