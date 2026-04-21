package org.example.mindweave.ai

import org.example.mindweave.domain.ai.AiRequest
import org.example.mindweave.domain.ai.AiTask
import org.example.mindweave.domain.ai.AiTaskTier

enum class AiExecutionRoute {
    DISABLED,
    LOCAL_RULES,
    LOCAL_LIGHTWEIGHT_MODEL,
    LOCAL_GENERATIVE_MODEL,
    CLOUD_ENHANCEMENT,
}

data class TaskPolicy(
    val task: AiTask,
    val preferredTier: AiTaskTier,
    val allowCloudEnhancement: Boolean,
)

data class RouteDecision(
    val task: AiTask,
    val policy: TaskPolicy,
    val primaryRoute: AiExecutionRoute,
    val fallbackRoute: AiExecutionRoute?,
    val cloudPermitted: Boolean,
    val reason: String,
)

class AiRouter(
    private val modelManager: ModelManager,
) {
    suspend fun decide(
        request: AiRequest,
        settings: AiSettings,
    ): RouteDecision {
        val policy = policyFor(request.task)
        if (settings == AiSettings.Disabled) {
            return RouteDecision(
                task = request.task,
                policy = policy,
                primaryRoute = AiExecutionRoute.DISABLED,
                fallbackRoute = null,
                cloudPermitted = false,
                reason = "AI 已在设置中关闭。",
            )
        }

        val cloudPermitted = when (settings) {
            AiSettings.Disabled -> false
            is AiSettings.LocalOnly -> false
            is AiSettings.LocalFirstCloudEnhancement ->
                policy.allowCloudEnhancement && settings.cloudEnhancementBaseUrl.isNotBlank()
            is AiSettings.ManualCloudEnhancement ->
                request.allowCloudEnhancement &&
                    policy.allowCloudEnhancement &&
                    settings.cloudEnhancementBaseUrl.isNotBlank()
        }

        val hasLightweight = modelManager.hasReadyPackage(settings.lightweightModelPackageId())
        val hasGenerative = modelManager.hasReadyPackage(settings.generativeModelPackageId())

        return when (policy.preferredTier) {
            AiTaskTier.RULES -> RouteDecision(
                task = request.task,
                policy = policy,
                primaryRoute = AiExecutionRoute.LOCAL_RULES,
                fallbackRoute = null,
                cloudPermitted = false,
                reason = "该任务按规则引擎优先处理。",
            )

            AiTaskTier.LIGHTWEIGHT_MODEL -> when {
                hasLightweight -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL,
                    fallbackRoute = AiExecutionRoute.LOCAL_RULES,
                    cloudPermitted = cloudPermitted,
                    reason = "本地轻模型可用，优先使用端侧轻模型。",
                )

                cloudPermitted -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.CLOUD_ENHANCEMENT,
                    fallbackRoute = AiExecutionRoute.LOCAL_RULES,
                    cloudPermitted = true,
                    reason = "本地轻模型不可用，转到云端增强并保留规则降级。",
                )

                else -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.LOCAL_RULES,
                    fallbackRoute = null,
                    cloudPermitted = false,
                    reason = "本地轻模型不可用，回退到规则引擎。",
                )
            }

            AiTaskTier.GENERATIVE_MODEL -> when {
                hasGenerative -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.LOCAL_GENERATIVE_MODEL,
                    fallbackRoute = when {
                        cloudPermitted -> AiExecutionRoute.CLOUD_ENHANCEMENT
                        hasLightweight -> AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL
                        else -> AiExecutionRoute.LOCAL_RULES
                    },
                    cloudPermitted = cloudPermitted,
                    reason = "本地生成模型可用，优先走端侧生成能力。",
                )

                hasLightweight -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.LOCAL_LIGHTWEIGHT_MODEL,
                    fallbackRoute = if (cloudPermitted) {
                        AiExecutionRoute.CLOUD_ENHANCEMENT
                    } else {
                        AiExecutionRoute.LOCAL_RULES
                    },
                    cloudPermitted = cloudPermitted,
                    reason = "本地生成模型不可用，先退到本地轻模型，保持本地优先。",
                )

                cloudPermitted -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.CLOUD_ENHANCEMENT,
                    fallbackRoute = AiExecutionRoute.LOCAL_RULES,
                    cloudPermitted = true,
                    reason = "本地生成与轻模型都不可用，使用云端增强。",
                )

                else -> RouteDecision(
                    task = request.task,
                    policy = policy,
                    primaryRoute = AiExecutionRoute.LOCAL_RULES,
                    fallbackRoute = null,
                    cloudPermitted = false,
                    reason = "缺少本地模型且不允许云增强，回退到规则引擎。",
                )
            }
        }
    }

    private fun policyFor(task: AiTask): TaskPolicy = when (task) {
        AiTask.CHAT_REPLY -> TaskPolicy(task, AiTaskTier.GENERATIVE_MODEL, allowCloudEnhancement = true)
        AiTask.DIARY_SUMMARY -> TaskPolicy(task, AiTaskTier.GENERATIVE_MODEL, allowCloudEnhancement = true)
        AiTask.CONVERSATION_SUMMARY -> TaskPolicy(task, AiTaskTier.GENERATIVE_MODEL, allowCloudEnhancement = true)
        AiTask.EMOTION_CLASSIFICATION -> TaskPolicy(task, AiTaskTier.LIGHTWEIGHT_MODEL, allowCloudEnhancement = false)
        AiTask.DIARY_EMBEDDING -> TaskPolicy(task, AiTaskTier.LIGHTWEIGHT_MODEL, allowCloudEnhancement = false)
        AiTask.SCHEDULE_PRIORITIZATION -> TaskPolicy(task, AiTaskTier.RULES, allowCloudEnhancement = false)
    }
}
