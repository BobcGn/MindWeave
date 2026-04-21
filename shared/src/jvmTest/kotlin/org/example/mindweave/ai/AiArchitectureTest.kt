package org.example.mindweave.ai

import kotlinx.coroutines.test.runTest
import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.ai.ChatContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiArchitectureTest {
    @Test
    fun routerShouldPreferRulesForRuleFirstTask() = runTest {
        val router = AiRouter(FakeModelManager())

        val decision = router.decide(
            request = request(task = AiTask.SCHEDULE_PRIORITIZATION),
            settings = AiSettings.LocalOnly(),
        )

        assertEquals(AiExecutionRoute.LOCAL_RULES, decision.primaryRoute)
        assertEquals(null, decision.fallbackRoute)
        assertFalse(decision.cloudPermitted)
    }

    @Test
    fun routerShouldKeepLocalFirstWhenGenerativeModelExists() = runTest {
        val router = AiRouter(
            FakeModelManager(
                readyPackages = setOf(
                    AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
                    AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
                ),
            ),
        )

        val decision = router.decide(
            request = request(task = AiTask.CHAT_REPLY),
            settings = AiSettings.LocalFirstCloudEnhancement(
                cloudEnhancementBaseUrl = "http://localhost:8080",
            ),
        )

        assertEquals(AiExecutionRoute.LOCAL_GENERATIVE_MODEL, decision.primaryRoute)
        assertEquals(AiExecutionRoute.CLOUD_ENHANCEMENT, decision.fallbackRoute)
        assertTrue(decision.cloudPermitted)
    }

    @Test
    fun manualCloudEnhancementShouldStayLocalWhenNotExplicitlyAllowed() = runTest {
        val router = AiRouter(FakeModelManager())

        val decision = router.decide(
            request = request(
                task = AiTask.CHAT_REPLY,
                allowCloudEnhancement = false,
            ),
            settings = AiSettings.ManualCloudEnhancement(
                cloudEnhancementBaseUrl = "http://localhost:8080",
            ),
        )

        assertEquals(AiExecutionRoute.LOCAL_RULES, decision.primaryRoute)
        assertFalse(decision.cloudPermitted)
    }

    @Test
    fun hybridEngineShouldNotCallCloudWhenLocalGenerativeModelIsReady() = runTest {
        val cloudGateway = RecordingCloudGateway()
        val modelManager = FakeModelManager(
            readyPackages = setOf(
                AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
                AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
            ),
        )
        val engine = HybridAiEngine(
            settings = AiSettings.LocalFirstCloudEnhancement(
                cloudEnhancementBaseUrl = "http://localhost:8080",
            ),
            modelManager = modelManager,
            router = AiRouter(modelManager),
            localEngine = LocalAiEngine(modelManager, PromptBuilder()),
            cloudEngine = CloudAiEngine(cloudGateway, PromptBuilder()),
        )

        val response = engine.execute(request(task = AiTask.CHAT_REPLY))

        assertEquals("Local Generative Model", response.source)
        assertEquals(0, cloudGateway.calls)
    }

    @Test
    fun hybridEngineShouldDegradeToLocalRulesWhenCloudFails() = runTest {
        val modelManager = FakeModelManager()
        val engine = HybridAiEngine(
            settings = AiSettings.LocalFirstCloudEnhancement(
                cloudEnhancementBaseUrl = "http://localhost:8080",
            ),
            modelManager = modelManager,
            router = AiRouter(modelManager),
            localEngine = LocalAiEngine(modelManager, PromptBuilder()),
            cloudEngine = CloudAiEngine(
                gateway = object : CloudEnhancementGateway {
                    override suspend fun enhance(
                        task: AiTask,
                        prompt: String,
                        context: ChatContext,
                    ): CloudEnhancementPayload = error("cloud unavailable")
                },
                promptBuilder = PromptBuilder(),
            ),
        )

        val response = engine.execute(request(task = AiTask.CHAT_REPLY))

        assertEquals("Local Rules", response.source)
        assertTrue(response.text.isNotBlank())
    }

    private fun request(
        task: AiTask,
        allowCloudEnhancement: Boolean = false,
    ) = AiRequest(
        task = task,
        prompt = "帮我整理一下今天的重点。",
        context = ChatContext(
            recentDiaries = emptyList(),
            upcomingEvents = emptyList(),
            recentMessages = emptyList(),
            assembledAtEpochMs = 1L,
        ),
        allowCloudEnhancement = allowCloudEnhancement,
    )
}

private class FakeModelManager(
    private val readyPackages: Set<String> = emptySet(),
) : ModelManager {
    override suspend fun ensureDefaultPackages(settings: AiSettings) = Unit

    override suspend fun listPackages(): List<ModelPackage> = emptyList()

    override suspend fun getPackage(packageId: String): ModelPackage? = null

    override suspend fun hasReadyPackage(packageId: String): Boolean = packageId in readyPackages

    override suspend fun upsertPackage(modelPackage: ModelPackage) = Unit
}

private class RecordingCloudGateway : CloudEnhancementGateway {
    var calls: Int = 0
        private set

    override suspend fun enhance(
        task: AiTask,
        prompt: String,
        context: ChatContext,
    ): CloudEnhancementPayload {
        calls += 1
        return CloudEnhancementPayload(
            text = "cloud response",
            source = "Cloud Enhancement",
        )
    }
}
