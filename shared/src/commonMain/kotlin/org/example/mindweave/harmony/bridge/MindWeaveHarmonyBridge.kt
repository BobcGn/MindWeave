package org.example.mindweave.harmony.bridge

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.example.mindweave.Platform
import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.app.MindWeaveAppGraph
import org.example.mindweave.app.createMindWeaveAppGraph
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.getPlatform
import org.example.mindweave.platform.PlatformContext

@Serializable
data class HarmonyBridgeInitRequest(
    val databaseName: String = "mindweave-harmony.db",
    val userId: String = "local-user",
    val deviceId: String = "harmony-device",
    val deviceName: String = "MindWeave Harmony Device",
    val aiMode: String = AiOperatingMode.LOCAL_ONLY.storageValue,
    val cloudEnhancementBaseUrl: String = "",
    val localLightweightModelPackageId: String = AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
    val localGenerativeModelPackageId: String = AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
    val modelDownloadPolicy: String = ModelDownloadPolicy.PREBUNDLED.name,
    val enableDemoData: Boolean = true,
)

@Serializable
data class HarmonyBridgeSnapshotRequest(
    val selectedSessionId: String? = null,
)

@Serializable
data class HarmonyBridgeDiaryDraft(
    val title: String = "",
    val content: String,
    val mood: String = DiaryMood.CALM.name,
    val tags: List<String> = emptyList(),
)

@Serializable
data class HarmonyBridgeScheduleDraft(
    val title: String,
    val description: String = "",
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val remindAtEpochMs: Long? = null,
    val type: String = ScheduleType.WORK.name,
)

@Serializable
data class HarmonyBridgeChatRequest(
    val existingSessionId: String? = null,
    val prompt: String,
)

@Serializable
data class HarmonyBridgePreferenceRequest(
    val aiMode: String = AiOperatingMode.LOCAL_ONLY.storageValue,
    val cloudEnhancementBaseUrl: String = "",
    val localLightweightModelPackageId: String = AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
    val localGenerativeModelPackageId: String = AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
    val modelDownloadPolicy: String = ModelDownloadPolicy.PREBUNDLED.name,
)

@Serializable
data class HarmonyBridgeAuthenticateRequest(
    val username: String,
    val password: String,
)

@Serializable
data class HarmonyBridgeCredentialResetRequest(
    val newUsername: String,
    val newPassword: String,
)

@Serializable
data class HarmonyBridgeChangeCredentialsRequest(
    val currentPassword: String,
    val newUsername: String,
    val newPassword: String,
)

@Serializable
data class HarmonyBridgeAccountState(
    val username: String,
    val mustChangeCredentials: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val credentialsUpdatedAtEpochMs: Long,
    val lastLoginAtEpochMs: Long? = null,
)

@Serializable
data class HarmonyBridgePreferenceState(
    val aiMode: String,
    val aiModeLabel: String,
    val cloudEnhancementBaseUrl: String,
    val localLightweightModelPackageId: String,
    val localGenerativeModelPackageId: String,
    val modelDownloadPolicy: String,
    val modelDownloadPolicyLabel: String,
)

@Serializable
data class HarmonyBridgeSnapshot(
    val platformName: String,
    val session: AppSession,
    val account: HarmonyBridgeAccountState? = null,
    val preferences: HarmonyBridgePreferenceState? = null,
    val timeline: List<DiaryTimelineItem>,
    val schedules: List<ScheduleEvent>,
    val chatSessions: List<ChatSession>,
    val conversation: List<ChatMessage>,
    val syncState: SyncState,
)

@Serializable
data class HarmonyBridgeResponse(
    val ok: Boolean,
    val message: String,
    val focusSessionId: String? = null,
    val snapshot: HarmonyBridgeSnapshot? = null,
)

class MindWeaveHarmonyBridgeController(
    private val graph: MindWeaveAppGraph,
    private val platform: Platform = getPlatform(),
) {
    suspend fun bootstrap(initRequest: HarmonyBridgeInitRequest): HarmonyBridgeResponse {
        graph.accountRepository.ensureDefaultAccount(graph.session.userId)
        graph.userPreferencesRepository.ensureDefaultPreferences(graph.session.userId, initRequest.toAiSettings())
        if (initRequest.enableDemoData) {
            graph.facade.seedDemoData()
        }
        return snapshot(
            request = HarmonyBridgeSnapshotRequest(),
            message = "Harmony bridge 已初始化。",
        )
    }

    suspend fun snapshot(
        request: HarmonyBridgeSnapshotRequest = HarmonyBridgeSnapshotRequest(),
        message: String = "已刷新鸿蒙端快照。",
    ): HarmonyBridgeResponse {
        val chatSessions = graph.facade.observeSessions().first()
        val focusSessionId = request.selectedSessionId ?: chatSessions.firstOrNull()?.id
        return HarmonyBridgeResponse(
            ok = true,
            message = message,
            focusSessionId = focusSessionId,
            snapshot = HarmonyBridgeSnapshot(
                platformName = platform.name,
                session = graph.session,
                account = graph.accountRepository.observeAccount(graph.session.userId).first()?.let { account ->
                    HarmonyBridgeAccountState(
                        username = account.username,
                        mustChangeCredentials = account.mustChangeCredentials,
                        createdAtEpochMs = account.createdAtEpochMs,
                        updatedAtEpochMs = account.updatedAtEpochMs,
                        credentialsUpdatedAtEpochMs = account.credentialsUpdatedAtEpochMs,
                        lastLoginAtEpochMs = account.lastLoginAtEpochMs,
                    )
                },
                preferences = graph.userPreferencesRepository.observePreferences(graph.session.userId).first()?.let { preferences ->
                    HarmonyBridgePreferenceState(
                        aiMode = preferences.aiMode.storageValue,
                        aiModeLabel = preferences.aiMode.label,
                        cloudEnhancementBaseUrl = preferences.cloudEnhancementBaseUrl,
                        localLightweightModelPackageId = preferences.localLightweightModelPackageId,
                        localGenerativeModelPackageId = preferences.localGenerativeModelPackageId,
                        modelDownloadPolicy = preferences.modelDownloadPolicy.name,
                        modelDownloadPolicyLabel = preferences.modelDownloadPolicy.label,
                    )
                },
                timeline = graph.facade.observeTimeline().first(),
                schedules = graph.facade.observeSchedules().first(),
                chatSessions = chatSessions,
                conversation = focusSessionId?.let { sessionId ->
                    graph.facade.observeConversation(sessionId).first()
                }.orEmpty(),
                syncState = graph.facade.observeSyncState().first(),
            ),
        )
    }

    suspend fun captureDiary(request: HarmonyBridgeDiaryDraft): HarmonyBridgeResponse {
        graph.facade.captureDiary(
            DiaryDraft(
                title = request.title,
                content = request.content,
                mood = DiaryMood.fromStorage(request.mood),
                tags = request.tags.map(String::trim).filter(String::isNotBlank),
            ),
        )
        return snapshot(message = "鸿蒙端日记已写入本地 SQLite。")
    }

    suspend fun captureSchedule(request: HarmonyBridgeScheduleDraft): HarmonyBridgeResponse {
        graph.facade.captureSchedule(
            ScheduleDraft(
                title = request.title,
                description = request.description,
                startTimeEpochMs = request.startTimeEpochMs,
                endTimeEpochMs = request.endTimeEpochMs,
                remindAtEpochMs = request.remindAtEpochMs,
                type = ScheduleType.fromStorage(request.type),
            ),
        )
        return snapshot(message = "鸿蒙端日程已写入本地 SQLite。")
    }

    suspend fun sendChatMessage(request: HarmonyBridgeChatRequest): HarmonyBridgeResponse {
        val sessionId = graph.facade.sendChatMessage(
            existingSessionId = request.existingSessionId,
            prompt = request.prompt,
        )
        return snapshot(
            request = HarmonyBridgeSnapshotRequest(selectedSessionId = sessionId),
            message = "鸿蒙端会话已完成本地写入。",
        )
    }

    suspend fun savePreferences(request: HarmonyBridgePreferenceRequest): HarmonyBridgeResponse {
        graph.userPreferencesRepository.savePreferences(
            userId = graph.session.userId,
            aiMode = AiOperatingMode.fromStorage(request.aiMode),
            cloudEnhancementBaseUrl = request.cloudEnhancementBaseUrl,
            localLightweightModelPackageId = request.localLightweightModelPackageId,
            localGenerativeModelPackageId = request.localGenerativeModelPackageId,
            modelDownloadPolicy = ModelDownloadPolicy.fromStorage(request.modelDownloadPolicy),
        )
        return snapshot(message = "鸿蒙端 AI 配置已更新。")
    }

    suspend fun authenticate(request: HarmonyBridgeAuthenticateRequest): HarmonyBridgeResponse {
        val account = graph.accountRepository.authenticate(
            username = request.username,
            password = request.password,
        )
        return if (account == null) {
            snapshot(message = "账号或密码错误。").copy(ok = false, message = "账号或密码错误。")
        } else {
            snapshot(
                message = if (account.mustChangeCredentials) {
                    "首次使用请先修改账号和密码。"
                } else {
                    "登录成功。"
                },
            )
        }
    }

    suspend fun forceResetCredentials(request: HarmonyBridgeCredentialResetRequest): HarmonyBridgeResponse {
        val error = graph.accountRepository.forceResetCredentials(
            userId = graph.session.userId,
            newUsername = request.newUsername,
            newPassword = request.newPassword,
        )
        return if (error != null) {
            snapshot(message = error).copy(ok = false, message = error)
        } else {
            snapshot(message = "默认凭据已停用，后续请使用新账号和密码登录。")
        }
    }

    suspend fun changeCredentials(request: HarmonyBridgeChangeCredentialsRequest): HarmonyBridgeResponse {
        val error = graph.accountRepository.changeCredentials(
            userId = graph.session.userId,
            currentPassword = request.currentPassword,
            newUsername = request.newUsername,
            newPassword = request.newPassword,
        )
        return if (error != null) {
            snapshot(message = error).copy(ok = false, message = error)
        } else {
            snapshot(message = "账户信息已更新。下次登录请使用新凭据。")
        }
    }

    suspend fun runSync(): HarmonyBridgeResponse {
        val result = graph.facade.runSync()
        val message = if (result == null) {
            "当前未配置远端同步，保持本地优先运行。"
        } else {
            "同步完成：push ${result.pushed}，pull ${result.pulled}，cursor=${result.latestSeq}。"
        }
        return snapshot(message = message)
    }
}

fun createMindWeaveHarmonyBridgeController(
    platformContext: PlatformContext,
    initRequest: HarmonyBridgeInitRequest,
): MindWeaveHarmonyBridgeController {
    val session = AppSession(
        userId = initRequest.userId,
        deviceId = initRequest.deviceId,
        deviceName = initRequest.deviceName,
    )
    val graph = createMindWeaveAppGraph(
        platformContext = platformContext,
        aiSettings = initRequest.toAiSettings(),
        session = session,
    )
    return MindWeaveHarmonyBridgeController(graph)
}

private fun HarmonyBridgeInitRequest.toAiSettings(): AiSettings =
    buildAiSettings(
        aiMode = aiMode,
        cloudEnhancementBaseUrl = cloudEnhancementBaseUrl,
        lightweightModelPackageId = localLightweightModelPackageId,
        generativeModelPackageId = localGenerativeModelPackageId,
        modelDownloadPolicy = modelDownloadPolicy,
    )

private fun buildAiSettings(
    aiMode: String,
    cloudEnhancementBaseUrl: String,
    lightweightModelPackageId: String,
    generativeModelPackageId: String,
    modelDownloadPolicy: String,
): AiSettings {
    val mode = AiOperatingMode.fromStorage(aiMode)
    val downloadPolicyValue = ModelDownloadPolicy.fromStorage(modelDownloadPolicy)
    return when (mode) {
        AiOperatingMode.DISABLED -> AiSettings.Disabled
        AiOperatingMode.LOCAL_ONLY -> AiSettings.LocalOnly(
            lightweightModelPackageId = lightweightModelPackageId,
            generativeModelPackageId = generativeModelPackageId,
            downloadPolicy = downloadPolicyValue,
        )
        AiOperatingMode.LOCAL_FIRST_CLOUD_ENHANCEMENT -> AiSettings.LocalFirstCloudEnhancement(
            cloudEnhancementBaseUrl = cloudEnhancementBaseUrl,
            lightweightModelPackageId = lightweightModelPackageId,
            generativeModelPackageId = generativeModelPackageId,
            downloadPolicy = downloadPolicyValue,
        )
        AiOperatingMode.MANUAL_CLOUD_ENHANCEMENT -> AiSettings.ManualCloudEnhancement(
            cloudEnhancementBaseUrl = cloudEnhancementBaseUrl,
            lightweightModelPackageId = lightweightModelPackageId,
            generativeModelPackageId = generativeModelPackageId,
            downloadPolicy = downloadPolicyValue,
        )
    }
}
