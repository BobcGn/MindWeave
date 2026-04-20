package org.example.mindweave

import org.example.mindweave.ai.AiSettings

fun desktopAiSettings(): AiSettings {
    val apiKey = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
    return if (apiKey.isBlank()) {
        AiSettings.Disabled
    } else {
        AiSettings.OpenAI(apiKey)
    }
}
