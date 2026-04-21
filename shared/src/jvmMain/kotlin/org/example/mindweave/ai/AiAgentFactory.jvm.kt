package org.example.mindweave.ai

import org.example.mindweave.network.createMindWeaveHttpClient

actual fun createAiAgent(
    settings: AiSettings,
    modelManager: ModelManager,
): AiAgent {
    val promptBuilder = PromptBuilder()
    val localEngine = LocalAiEngine(
        modelManager = modelManager,
        promptBuilder = promptBuilder,
    )
    val cloudEngine = CloudAiEngine(
        gateway = KtorCloudEnhancementGateway(
            baseUrl = settings.cloudEnhancementBaseUrlOrNull(),
            client = createMindWeaveHttpClient(),
        ),
        promptBuilder = promptBuilder,
    )
    return LocalFirstAiAgent(
        engine = HybridAiEngine(
            settings = settings,
            modelManager = modelManager,
            router = AiRouter(modelManager),
            localEngine = localEngine,
            cloudEngine = cloudEngine,
        ),
    )
}
