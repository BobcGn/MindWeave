package org.example.mindweave.ai

actual fun createAiAgent(
    settings: AiSettings,
    modelManager: ModelManager,
): AiAgent {
    val promptBuilder = PromptBuilder()
    val localEngine = LocalAiEngine(
        modelManager = modelManager,
        promptBuilder = promptBuilder,
    )
    val compatibleSettings = settings.toHarmonyBridgeSettings()
    return LocalFirstAiAgent(
        engine = HybridAiEngine(
            settings = compatibleSettings,
            modelManager = modelManager,
            router = AiRouter(modelManager),
            localEngine = localEngine,
            cloudEngine = CloudAiEngine(
                gateway = object : CloudEnhancementGateway {
                    override suspend fun enhance(
                        task: org.example.mindweave.domain.ai.AiTask,
                        prompt: String,
                        context: org.example.mindweave.domain.ai.ChatContext,
                    ): CloudEnhancementPayload = error("Harmony bridge 当前未接入云增强。")
                },
                promptBuilder = promptBuilder,
            ),
        ),
    )
}

private fun AiSettings.toHarmonyBridgeSettings(): AiSettings = when (this) {
    AiSettings.Disabled -> AiSettings.Disabled
    is AiSettings.LocalOnly -> this
    is AiSettings.LocalFirstCloudEnhancement -> AiSettings.LocalOnly(
        lightweightModelPackageId = lightweightModelPackageId,
        generativeModelPackageId = generativeModelPackageId,
        downloadPolicy = downloadPolicy,
    )
    is AiSettings.ManualCloudEnhancement -> AiSettings.LocalOnly(
        lightweightModelPackageId = lightweightModelPackageId,
        generativeModelPackageId = generativeModelPackageId,
        downloadPolicy = downloadPolicy,
    )
}
