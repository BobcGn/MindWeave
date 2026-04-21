package org.example.mindweave

import org.example.mindweave.ai.AiSettings

fun desktopAiSettings(): AiSettings {
    val mode = System.getenv("MINDWEAVE_AI_MODE")?.trim()?.uppercase()
    val cloudBaseUrl = System.getenv("MINDWEAVE_AI_SERVER_BASE_URL")?.trim().orEmpty()
    return when (mode) {
        "DISABLED" -> AiSettings.Disabled
        "LOCAL_FIRST_CLOUD_ENHANCEMENT" -> AiSettings.LocalFirstCloudEnhancement(
            cloudEnhancementBaseUrl = cloudBaseUrl,
        )
        "MANUAL_CLOUD_ENHANCEMENT" -> AiSettings.ManualCloudEnhancement(
            cloudEnhancementBaseUrl = cloudBaseUrl,
        )
        else -> AiSettings.LocalOnly()
    }
}
