package org.example.mindweave

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.app.createMindWeaveAppGraph
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.domain.model.UserAccount
import org.example.mindweave.domain.model.UserPreferences
import org.example.mindweave.platform.PlatformContext
import org.example.mindweave.util.currentEpochMillis
import org.example.mindweave.util.formatMoment

private val MindWeaveColors = lightColorScheme(
    primary = Color(0xFF8C4A37),
    onPrimary = Color(0xFFFFF7F2),
    secondary = Color(0xFF55726E),
    onSecondary = Color(0xFFF6FFFD),
    background = Color(0xFFF4EDE4),
    onBackground = Color(0xFF2C2520),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF2C2520),
    surfaceVariant = Color(0xFFE8D8C7),
    onSurfaceVariant = Color(0xFF4C4139),
    outline = Color(0xFFB59C86),
)

@Composable
fun App(
    platformContext: PlatformContext,
    aiSettings: AiSettings = AiSettings.LocalOnly(),
) {
    val graph = remember(platformContext, aiSettings) {
        createMindWeaveAppGraph(platformContext, aiSettings)
    }
    val facade = graph.facade
    val scope = rememberCoroutineScope()
    val account by graph.accountRepository.observeAccount(graph.session.userId).collectAsState(initial = null)
    val preferences by graph.userPreferencesRepository.observePreferences(graph.session.userId).collectAsState(initial = null)

    val timeline by facade.observeTimeline().collectAsState(initial = emptyList())
    val schedules by facade.observeSchedules().collectAsState(initial = emptyList())
    val sessions by facade.observeSessions().collectAsState(initial = emptyList())
    val syncState by facade.observeSyncState().collectAsState(initial = SyncState(0, 0))

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    val conversationFlow = remember(selectedSessionId) {
        selectedSessionId?.let { facade.observeConversation(it) } ?: flowOf<List<ChatMessage>>(emptyList())
    }
    val conversation by conversationFlow.collectAsState(initial = emptyList())

    var diaryTitle by remember { mutableStateOf("") }
    var diaryBody by remember { mutableStateOf("") }
    var diaryMood by remember { mutableStateOf(DiaryMood.CALM) }
    var diaryTags by remember { mutableStateOf("工作, 恢复") }

    var scheduleTitle by remember { mutableStateOf("") }
    var scheduleDesc by remember { mutableStateOf("") }
    var scheduleType by remember { mutableStateOf(ScheduleType.WORK) }
    var startAfterHours by remember { mutableStateOf("2") }
    var durationHours by remember { mutableStateOf("1") }

    var prompt by remember { mutableStateOf("") }
    var statusLine by remember { mutableStateOf("本地 SQLite 为事实数据源，远端同步只做协调。") }
    var isBusy by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var personalBusy by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(AppSection.DIARY) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var requiresCredentialReset by remember { mutableStateOf(false) }

    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginStatusLine by remember { mutableStateOf("首次使用请使用默认账号 MindWeave 登录。") }

    var resetUsername by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var resetConfirmPassword by remember { mutableStateOf("") }
    var resetStatusLine by remember { mutableStateOf("首次登录后必须先修改账号和密码。") }

    var profileUsername by remember { mutableStateOf("") }
    var profileCurrentPassword by remember { mutableStateOf("") }
    var profileNewPassword by remember { mutableStateOf("") }
    var profileConfirmPassword by remember { mutableStateOf("") }
    var aiModeDraft by remember { mutableStateOf(AiOperatingMode.LOCAL_ONLY) }
    var cloudEnhancementBaseUrlDraft by remember { mutableStateOf("") }
    var lightweightModelPackageDraft by remember { mutableStateOf(AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID) }
    var generativeModelPackageDraft by remember { mutableStateOf(AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID) }
    var modelDownloadPolicyDraft by remember { mutableStateOf(ModelDownloadPolicy.PREBUNDLED) }
    var personalStatusLine by remember { mutableStateOf("在这里管理本地优先 AI、云增强开关与账户密码。") }

    var loadedAccountVersion by remember { mutableStateOf<Long?>(null) }
    var loadedPreferencesVersion by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(facade) {
        facade.seedDemoData()
    }
    LaunchedEffect(graph, aiSettings) {
        graph.accountRepository.ensureDefaultAccount(graph.session.userId)
        graph.userPreferencesRepository.ensureDefaultPreferences(graph.session.userId, aiSettings)
    }
    LaunchedEffect(sessions) {
        if (selectedSessionId == null) {
            selectedSessionId = sessions.firstOrNull()?.id
        }
    }
    LaunchedEffect(account?.updatedAtEpochMs, account?.mustChangeCredentials) {
        val currentAccount = account ?: return@LaunchedEffect
        if (loadedAccountVersion == currentAccount.updatedAtEpochMs) return@LaunchedEffect

        if (!isAuthenticated) {
            loginUsername = currentAccount.username
            if (currentAccount.mustChangeCredentials) {
                loginPassword = DEFAULT_BOOTSTRAP_PASSWORD
            }
            loginStatusLine = if (currentAccount.mustChangeCredentials) {
                "请使用默认凭据登录，并立即完成首次改密。"
            } else {
                "请输入你已设置的账号和密码登录。"
            }
        }
        resetUsername = currentAccount.username
        profileUsername = currentAccount.username
        loadedAccountVersion = currentAccount.updatedAtEpochMs
    }
    LaunchedEffect(preferences?.updatedAtEpochMs) {
        val currentPreferences = preferences ?: return@LaunchedEffect
        if (loadedPreferencesVersion == currentPreferences.updatedAtEpochMs) return@LaunchedEffect

        aiModeDraft = currentPreferences.aiMode
        cloudEnhancementBaseUrlDraft = currentPreferences.cloudEnhancementBaseUrl
        lightweightModelPackageDraft = currentPreferences.localLightweightModelPackageId
        generativeModelPackageDraft = currentPreferences.localGenerativeModelPackageId
        modelDownloadPolicyDraft = currentPreferences.modelDownloadPolicy
        loadedPreferencesVersion = currentPreferences.updatedAtEpochMs
    }

    MaterialTheme(colorScheme = MindWeaveColors) {
        val loginUser: () -> Unit = {
            if (loginUsername.isBlank() || loginPassword.isBlank()) {
                loginStatusLine = "请输入账号和密码。"
            } else {
                authBusy = true
                scope.launch {
                    val authenticatedAccount = graph.accountRepository.authenticate(
                        userId = graph.session.userId,
                        username = loginUsername,
                        password = loginPassword,
                    )
                    authBusy = false
                    if (authenticatedAccount == null) {
                        loginStatusLine = "账号或密码错误。"
                    } else {
                        isAuthenticated = true
                        requiresCredentialReset = authenticatedAccount.mustChangeCredentials
                        resetUsername = authenticatedAccount.username
                        resetPassword = ""
                        resetConfirmPassword = ""
                        loginStatusLine = if (authenticatedAccount.mustChangeCredentials) {
                            "首次使用请先修改账号和密码。"
                        } else {
                            "登录成功。"
                        }
                    }
                }
            }
        }
        val completeCredentialReset: () -> Unit = {
            when {
                resetUsername.isBlank() -> resetStatusLine = "新账号不能为空。"
                resetPassword.isBlank() -> resetStatusLine = "新密码不能为空。"
                resetPassword != resetConfirmPassword -> resetStatusLine = "两次输入的新密码不一致。"
                resetUsername.trim() == DEFAULT_BOOTSTRAP_USERNAME -> resetStatusLine = "初始账号必须修改。"
                resetPassword == DEFAULT_BOOTSTRAP_PASSWORD -> resetStatusLine = "初始密码必须修改。"
                else -> {
                    authBusy = true
                    scope.launch {
                        val error = graph.accountRepository.forceResetCredentials(
                            userId = graph.session.userId,
                            newUsername = resetUsername,
                            newPassword = resetPassword,
                        )
                        authBusy = false
                        if (error != null) {
                            resetStatusLine = error
                        } else {
                            requiresCredentialReset = false
                            selectedSection = AppSection.PERSONAL
                            loginStatusLine = "默认凭据已停用，后续请使用新账号和密码登录。"
                            personalStatusLine = "已完成首次账号配置。接下来可以检查本地优先 AI 与云增强策略。"
                            profileCurrentPassword = ""
                            profileNewPassword = ""
                            profileConfirmPassword = ""
                        }
                    }
                }
            }
        }
        val saveDiary: () -> Unit = {
            if (diaryBody.isBlank()) {
                statusLine = "先写下一段正文。"
            } else {
                isBusy = true
                scope.launch {
                    val saved = facade.captureDiary(
                        DiaryDraft(
                            title = diaryTitle,
                            content = diaryBody,
                            mood = diaryMood,
                            tags = diaryTags.split(","),
                        ),
                    )
                    diaryTitle = ""
                    diaryBody = ""
                    diaryMood = DiaryMood.CALM
                    diaryTags = "工作, 恢复"
                    isBusy = false
                    statusLine = "已保存日记并生成 AI 总结：${saved.entry.aiSummary ?: "无"}"
                }
            }
        }
        val saveSchedule: () -> Unit = {
            if (scheduleTitle.isBlank()) {
                statusLine = "先写下日程标题。"
            } else {
                isBusy = true
                scope.launch {
                    val now = currentEpochMillis()
                    val start = now + startAfterHours.toLongOrNull().orDefault(2) * 60 * 60 * 1000
                    val end = start + durationHours.toLongOrNull().orDefault(1) * 60 * 60 * 1000
                    facade.captureSchedule(
                        ScheduleDraft(
                            title = scheduleTitle,
                            description = scheduleDesc,
                            startTimeEpochMs = start,
                            endTimeEpochMs = end,
                            remindAtEpochMs = start - 15 * 60 * 1000,
                            type = scheduleType,
                        ),
                    )
                    scheduleTitle = ""
                    scheduleDesc = ""
                    startAfterHours = "2"
                    durationHours = "1"
                    isBusy = false
                    statusLine = "日程已写入本地数据库。"
                }
            }
        }
        val sendPrompt: () -> Unit = {
            if (prompt.isBlank()) {
                statusLine = "先写下你想和 AI 讨论的内容。"
            } else {
                isBusy = true
                scope.launch {
                    selectedSessionId = facade.sendChatMessage(selectedSessionId, prompt)
                    prompt = ""
                    isBusy = false
                    statusLine = "AI 回复已缓存到本地聊天记录。"
                }
            }
        }
        val syncNow: () -> Unit = {
            isBusy = true
            scope.launch {
                val result = facade.runSync()
                isBusy = false
                statusLine = if (result == null) {
                    "当前未配置远端同步。"
                } else {
                    "同步完成：push ${result.pushed} 条，pull ${result.pulled} 条，cursor=${result.latestSeq}"
                }
            }
        }
        val savePreferences: () -> Unit = {
            if (lightweightModelPackageDraft.isBlank()) {
                personalStatusLine = "本地轻模型包 ID 不能为空。"
            } else if (generativeModelPackageDraft.isBlank()) {
                personalStatusLine = "本地生成模型包 ID 不能为空。"
            } else {
                personalBusy = true
                scope.launch {
                    graph.userPreferencesRepository.savePreferences(
                        userId = graph.session.userId,
                        aiMode = aiModeDraft,
                        cloudEnhancementBaseUrl = cloudEnhancementBaseUrlDraft,
                        localLightweightModelPackageId = lightweightModelPackageDraft,
                        localGenerativeModelPackageId = generativeModelPackageDraft,
                        modelDownloadPolicy = modelDownloadPolicyDraft,
                    )
                    personalBusy = false
                    personalStatusLine = if (aiModeDraft == AiOperatingMode.DISABLED) {
                        "AI 已关闭。主流程仍然保持本地可用。"
                    } else if (
                        aiModeDraft != AiOperatingMode.LOCAL_ONLY &&
                        cloudEnhancementBaseUrlDraft.isBlank()
                    ) {
                        "已保存 AI 策略。当前未配置云增强端点，系统会继续只使用本地能力。"
                    } else {
                        "已保存 AI 策略。系统将按本地优先架构执行。"
                    }
                }
            }
        }
        val changeCredentials: () -> Unit = {
            when {
                profileCurrentPassword.isBlank() -> personalStatusLine = "请输入当前密码。"
                profileUsername.isBlank() -> personalStatusLine = "账号不能为空。"
                profileNewPassword.isBlank() -> personalStatusLine = "新密码不能为空。"
                profileNewPassword != profileConfirmPassword -> personalStatusLine = "两次输入的新密码不一致。"
                profileUsername.trim() == DEFAULT_BOOTSTRAP_USERNAME -> personalStatusLine = "默认账号不能继续使用，请更换为个人账号。"
                profileNewPassword == DEFAULT_BOOTSTRAP_PASSWORD -> personalStatusLine = "默认密码不能继续使用，请设置新密码。"
                else -> {
                    personalBusy = true
                    scope.launch {
                        val error = graph.accountRepository.changeCredentials(
                            userId = graph.session.userId,
                            currentPassword = profileCurrentPassword,
                            newUsername = profileUsername,
                            newPassword = profileNewPassword,
                        )
                        personalBusy = false
                        if (error != null) {
                            personalStatusLine = error
                        } else {
                            loginUsername = profileUsername.trim()
                            profileCurrentPassword = ""
                            profileNewPassword = ""
                            profileConfirmPassword = ""
                            personalStatusLine = "账户信息已更新。下次登录请使用新凭据。"
                        }
                    }
                }
            }
        }
        val signOut: () -> Unit = {
            isAuthenticated = false
            requiresCredentialReset = false
            authBusy = false
            loginPassword = ""
            resetPassword = ""
            resetConfirmPassword = ""
            selectedSection = AppSection.DIARY
            loginStatusLine = "已退出登录。"
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeContentPadding()
                    .padding(20.dp),
            ) {
                val compact = maxWidth < 1040.dp
                when {
                    account == null || preferences == null -> CenteredPanel {
                        Text("正在准备本地账户与配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "首次启动会自动创建默认账户与个人配置。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    !isAuthenticated -> LoginPanel(
                        username = loginUsername,
                        onUsernameChange = { loginUsername = it },
                        password = loginPassword,
                        onPasswordChange = { loginPassword = it },
                        statusLine = loginStatusLine,
                        isBusy = authBusy,
                        onLogin = loginUser,
                        account = account,
                    )
                    requiresCredentialReset -> CredentialResetPanel(
                        username = resetUsername,
                        onUsernameChange = { resetUsername = it },
                        password = resetPassword,
                        onPasswordChange = { resetPassword = it },
                        confirmPassword = resetConfirmPassword,
                        onConfirmPasswordChange = { resetConfirmPassword = it },
                        statusLine = resetStatusLine,
                        isBusy = authBusy,
                        onSubmit = completeCredentialReset,
                    )
                    compact -> CompactAppLayout(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        syncState = syncState,
                        statusLine = statusLine,
                        content = {
                            AppSectionContent(
                                section = selectedSection,
                                diaryTitle = diaryTitle,
                                onDiaryTitleChange = { diaryTitle = it },
                                diaryBody = diaryBody,
                                onDiaryBodyChange = { diaryBody = it },
                                diaryMood = diaryMood,
                                onDiaryMoodChange = { diaryMood = it },
                                diaryTags = diaryTags,
                                onDiaryTagsChange = { diaryTags = it },
                                timeline = timeline,
                                onSaveDiary = saveDiary,
                                scheduleTitle = scheduleTitle,
                                onScheduleTitleChange = { scheduleTitle = it },
                                scheduleDesc = scheduleDesc,
                                onScheduleDescChange = { scheduleDesc = it },
                                scheduleType = scheduleType,
                                onScheduleTypeChange = { scheduleType = it },
                                startAfterHours = startAfterHours,
                                onStartAfterHoursChange = { startAfterHours = it },
                                durationHours = durationHours,
                                onDurationHoursChange = { durationHours = it },
                                schedules = schedules,
                                onSaveSchedule = saveSchedule,
                                sessions = sessions,
                                selectedSessionId = selectedSessionId,
                                onSelectSession = { selectedSessionId = it },
                                conversation = conversation,
                                prompt = prompt,
                                onPromptChange = { prompt = it },
                                onSendPrompt = sendPrompt,
                                onSync = syncNow,
                                assistantSyncState = syncState,
                                assistantBusy = isBusy,
                                account = account,
                                preferences = preferences,
                                profileUsername = profileUsername,
                                onProfileUsernameChange = { profileUsername = it },
                                profileCurrentPassword = profileCurrentPassword,
                                onProfileCurrentPasswordChange = { profileCurrentPassword = it },
                                profileNewPassword = profileNewPassword,
                                onProfileNewPasswordChange = { profileNewPassword = it },
                                profileConfirmPassword = profileConfirmPassword,
                                onProfileConfirmPasswordChange = { profileConfirmPassword = it },
                                aiModeDraft = aiModeDraft,
                                onAiModeDraftChange = { aiModeDraft = it },
                                cloudEnhancementBaseUrlDraft = cloudEnhancementBaseUrlDraft,
                                onCloudEnhancementBaseUrlDraftChange = { cloudEnhancementBaseUrlDraft = it },
                                lightweightModelPackageDraft = lightweightModelPackageDraft,
                                onLightweightModelPackageDraftChange = { lightweightModelPackageDraft = it },
                                generativeModelPackageDraft = generativeModelPackageDraft,
                                onGenerativeModelPackageDraftChange = { generativeModelPackageDraft = it },
                                modelDownloadPolicyDraft = modelDownloadPolicyDraft,
                                onModelDownloadPolicyDraftChange = { modelDownloadPolicyDraft = it },
                                personalStatusLine = personalStatusLine,
                                onSavePreferences = savePreferences,
                                onChangeCredentials = changeCredentials,
                                onSignOut = signOut,
                                personalBusy = personalBusy,
                            )
                        },
                    )
                    else -> ExpandedAppLayout(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        syncState = syncState,
                        statusLine = statusLine,
                        content = {
                            AppSectionContent(
                                section = selectedSection,
                                diaryTitle = diaryTitle,
                                onDiaryTitleChange = { diaryTitle = it },
                                diaryBody = diaryBody,
                                onDiaryBodyChange = { diaryBody = it },
                                diaryMood = diaryMood,
                                onDiaryMoodChange = { diaryMood = it },
                                diaryTags = diaryTags,
                                onDiaryTagsChange = { diaryTags = it },
                                timeline = timeline,
                                onSaveDiary = saveDiary,
                                scheduleTitle = scheduleTitle,
                                onScheduleTitleChange = { scheduleTitle = it },
                                scheduleDesc = scheduleDesc,
                                onScheduleDescChange = { scheduleDesc = it },
                                scheduleType = scheduleType,
                                onScheduleTypeChange = { scheduleType = it },
                                startAfterHours = startAfterHours,
                                onStartAfterHoursChange = { startAfterHours = it },
                                durationHours = durationHours,
                                onDurationHoursChange = { durationHours = it },
                                schedules = schedules,
                                onSaveSchedule = saveSchedule,
                                sessions = sessions,
                                selectedSessionId = selectedSessionId,
                                onSelectSession = { selectedSessionId = it },
                                conversation = conversation,
                                prompt = prompt,
                                onPromptChange = { prompt = it },
                                onSendPrompt = sendPrompt,
                                onSync = syncNow,
                                assistantSyncState = syncState,
                                assistantBusy = isBusy,
                                account = account,
                                preferences = preferences,
                                profileUsername = profileUsername,
                                onProfileUsernameChange = { profileUsername = it },
                                profileCurrentPassword = profileCurrentPassword,
                                onProfileCurrentPasswordChange = { profileCurrentPassword = it },
                                profileNewPassword = profileNewPassword,
                                onProfileNewPasswordChange = { profileNewPassword = it },
                                profileConfirmPassword = profileConfirmPassword,
                                onProfileConfirmPasswordChange = { profileConfirmPassword = it },
                                aiModeDraft = aiModeDraft,
                                onAiModeDraftChange = { aiModeDraft = it },
                                cloudEnhancementBaseUrlDraft = cloudEnhancementBaseUrlDraft,
                                onCloudEnhancementBaseUrlDraftChange = { cloudEnhancementBaseUrlDraft = it },
                                lightweightModelPackageDraft = lightweightModelPackageDraft,
                                onLightweightModelPackageDraftChange = { lightweightModelPackageDraft = it },
                                generativeModelPackageDraft = generativeModelPackageDraft,
                                onGenerativeModelPackageDraftChange = { generativeModelPackageDraft = it },
                                modelDownloadPolicyDraft = modelDownloadPolicyDraft,
                                onModelDownloadPolicyDraftChange = { modelDownloadPolicyDraft = it },
                                personalStatusLine = personalStatusLine,
                                onSavePreferences = savePreferences,
                                onChangeCredentials = changeCredentials,
                                onSignOut = signOut,
                                personalBusy = personalBusy,
                            )
                        },
                    )
                }
            }
        }
    }
}

private enum class AppSection(
    val shortLabel: String,
    val title: String,
    val description: String,
) {
    DIARY(
        shortLabel = "记",
        title = "日记",
        description = "记录情绪、标签和 AI 总结",
    ),
    SCHEDULE(
        shortLabel = "程",
        title = "日程",
        description = "安排待办与提醒节奏",
    ),
    ASSISTANT(
        shortLabel = "AI",
        title = "助手",
        description = "查看对话并执行同步",
    ),
    PERSONAL(
        shortLabel = "我",
        title = "个人",
        description = "管理本地优先 AI 与账户密码",
    ),
}

@Composable
private fun CompactAppLayout(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    syncState: SyncState,
    statusLine: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = section == selectedSection,
                        onClick = { onSectionSelected(section) },
                        icon = {
                            Text(
                                text = section.shortLabel,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        label = { Text(section.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroPanel(syncState = syncState, statusLine = statusLine)
            content()
        }
    }
}

@Composable
private fun ExpandedAppLayout(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    syncState: SyncState,
    statusLine: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroPanel(syncState = syncState, statusLine = statusLine)
            DesktopSectionNav(
                selectedSection = selectedSection,
                onSectionSelected = onSectionSelected,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(selectedSection)
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val contentWidth = if (maxWidth > 960.dp) 860.dp else maxWidth
                Column(
                    modifier = Modifier.widthIn(max = contentWidth),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DesktopSectionNav(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
) {
    Panel {
        Text("导航", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppSection.entries.forEach { section ->
                val selected = section == selectedSection
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSectionSelected(section) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Text(
                                text = section.shortLabel,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(section.title, fontWeight = FontWeight.Medium)
                            Text(
                                section.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: AppSection) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = section.shortLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(
                    section.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppSectionContent(
    section: AppSection,
    diaryTitle: String,
    onDiaryTitleChange: (String) -> Unit,
    diaryBody: String,
    onDiaryBodyChange: (String) -> Unit,
    diaryMood: DiaryMood,
    onDiaryMoodChange: (DiaryMood) -> Unit,
    diaryTags: String,
    onDiaryTagsChange: (String) -> Unit,
    timeline: List<DiaryTimelineItem>,
    onSaveDiary: () -> Unit,
    scheduleTitle: String,
    onScheduleTitleChange: (String) -> Unit,
    scheduleDesc: String,
    onScheduleDescChange: (String) -> Unit,
    scheduleType: ScheduleType,
    onScheduleTypeChange: (ScheduleType) -> Unit,
    startAfterHours: String,
    onStartAfterHoursChange: (String) -> Unit,
    durationHours: String,
    onDurationHoursChange: (String) -> Unit,
    schedules: List<ScheduleEvent>,
    onSaveSchedule: () -> Unit,
    sessions: List<org.example.mindweave.domain.model.ChatSession>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    conversation: List<ChatMessage>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSync: () -> Unit,
    assistantSyncState: SyncState,
    assistantBusy: Boolean,
    account: UserAccount?,
    preferences: UserPreferences?,
    profileUsername: String,
    onProfileUsernameChange: (String) -> Unit,
    profileCurrentPassword: String,
    onProfileCurrentPasswordChange: (String) -> Unit,
    profileNewPassword: String,
    onProfileNewPasswordChange: (String) -> Unit,
    profileConfirmPassword: String,
    onProfileConfirmPasswordChange: (String) -> Unit,
    aiModeDraft: AiOperatingMode,
    onAiModeDraftChange: (AiOperatingMode) -> Unit,
    cloudEnhancementBaseUrlDraft: String,
    onCloudEnhancementBaseUrlDraftChange: (String) -> Unit,
    lightweightModelPackageDraft: String,
    onLightweightModelPackageDraftChange: (String) -> Unit,
    generativeModelPackageDraft: String,
    onGenerativeModelPackageDraftChange: (String) -> Unit,
    modelDownloadPolicyDraft: ModelDownloadPolicy,
    onModelDownloadPolicyDraftChange: (ModelDownloadPolicy) -> Unit,
    personalStatusLine: String,
    onSavePreferences: () -> Unit,
    onChangeCredentials: () -> Unit,
    onSignOut: () -> Unit,
    personalBusy: Boolean,
) {
    when (section) {
        AppSection.DIARY -> DiaryPanel(
            title = diaryTitle,
            onTitleChange = onDiaryTitleChange,
            body = diaryBody,
            onBodyChange = onDiaryBodyChange,
            mood = diaryMood,
            onMoodChange = onDiaryMoodChange,
            tags = diaryTags,
            onTagsChange = onDiaryTagsChange,
            timeline = timeline,
            onSave = onSaveDiary,
        )
        AppSection.SCHEDULE -> SchedulePanel(
            title = scheduleTitle,
            onTitleChange = onScheduleTitleChange,
            description = scheduleDesc,
            onDescriptionChange = onScheduleDescChange,
            type = scheduleType,
            onTypeChange = onScheduleTypeChange,
            startAfterHours = startAfterHours,
            onStartAfterHoursChange = onStartAfterHoursChange,
            durationHours = durationHours,
            onDurationHoursChange = onDurationHoursChange,
            schedules = schedules,
            onSave = onSaveSchedule,
        )
        AppSection.ASSISTANT -> AssistantPanel(
            sessions = sessions,
            selectedSessionId = selectedSessionId,
            onSelectSession = onSelectSession,
            conversation = conversation,
            prompt = prompt,
            onPromptChange = onPromptChange,
            onSend = onSendPrompt,
            onSync = onSync,
            syncState = assistantSyncState,
            isBusy = assistantBusy,
        )
        AppSection.PERSONAL -> PersonalPanel(
            account = account,
            preferences = preferences,
            profileUsername = profileUsername,
            onProfileUsernameChange = onProfileUsernameChange,
            profileCurrentPassword = profileCurrentPassword,
            onProfileCurrentPasswordChange = onProfileCurrentPasswordChange,
            profileNewPassword = profileNewPassword,
            onProfileNewPasswordChange = onProfileNewPasswordChange,
            profileConfirmPassword = profileConfirmPassword,
            onProfileConfirmPasswordChange = onProfileConfirmPasswordChange,
            aiModeDraft = aiModeDraft,
            onAiModeDraftChange = onAiModeDraftChange,
            cloudEnhancementBaseUrlDraft = cloudEnhancementBaseUrlDraft,
            onCloudEnhancementBaseUrlDraftChange = onCloudEnhancementBaseUrlDraftChange,
            lightweightModelPackageDraft = lightweightModelPackageDraft,
            onLightweightModelPackageDraftChange = onLightweightModelPackageDraftChange,
            generativeModelPackageDraft = generativeModelPackageDraft,
            onGenerativeModelPackageDraftChange = onGenerativeModelPackageDraftChange,
            modelDownloadPolicyDraft = modelDownloadPolicyDraft,
            onModelDownloadPolicyDraftChange = onModelDownloadPolicyDraftChange,
            statusLine = personalStatusLine,
            onSavePreferences = onSavePreferences,
            onChangeCredentials = onChangeCredentials,
            onSignOut = onSignOut,
            isBusy = personalBusy,
        )
    }
}

@Composable
private fun CenteredPanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 540.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LoginPanel(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    statusLine: String,
    isBusy: Boolean,
    onLogin: () -> Unit,
    account: UserAccount?,
) {
    val isBootstrapAccount = account?.mustChangeCredentials == true
    CenteredPanel {
        Text("登录", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            if (isBootstrapAccount) {
                "首次使用默认账号与密码均为 MindWeave，登录后必须修改。"
            } else {
                "请输入你已经设置过的账号和密码登录。默认凭据只用于首次初始化。"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("密码") },
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
        ) {
            Text(if (isBusy) "登录中..." else "进入 MindWeave")
        }
        Spacer(Modifier.height(12.dp))
        if (account != null) {
            Text(
                if (isBootstrapAccount) {
                    "当前初始化账号：${account.username}"
                } else {
                    "当前本地账号：${account.username}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CredentialResetPanel(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    statusLine: String,
    isBusy: Boolean,
    onSubmit: () -> Unit,
) {
    CenteredPanel {
        Text("首次改密", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "为了后续正常登录，请立即替换初始账号与密码。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新账号") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("新密码") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("确认新密码") },
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
        ) {
            Text(if (isBusy) "保存中..." else "保存并进入")
        }
        Spacer(Modifier.height(12.dp))
        Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalPanel(
    account: UserAccount?,
    preferences: UserPreferences?,
    profileUsername: String,
    onProfileUsernameChange: (String) -> Unit,
    profileCurrentPassword: String,
    onProfileCurrentPasswordChange: (String) -> Unit,
    profileNewPassword: String,
    onProfileNewPasswordChange: (String) -> Unit,
    profileConfirmPassword: String,
    onProfileConfirmPasswordChange: (String) -> Unit,
    aiModeDraft: AiOperatingMode,
    onAiModeDraftChange: (AiOperatingMode) -> Unit,
    cloudEnhancementBaseUrlDraft: String,
    onCloudEnhancementBaseUrlDraftChange: (String) -> Unit,
    lightweightModelPackageDraft: String,
    onLightweightModelPackageDraftChange: (String) -> Unit,
    generativeModelPackageDraft: String,
    onGenerativeModelPackageDraftChange: (String) -> Unit,
    modelDownloadPolicyDraft: ModelDownloadPolicy,
    onModelDownloadPolicyDraftChange: (ModelDownloadPolicy) -> Unit,
    statusLine: String,
    onSavePreferences: () -> Unit,
    onChangeCredentials: () -> Unit,
    onSignOut: () -> Unit,
    isBusy: Boolean,
) {
    val currentAccount = account
    val currentPreferences = preferences
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            Text("本地优先 AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(
                if (currentPreferences == null) {
                    "正在加载 AI 设置。"
                } else {
                    "客户端只使用本地规则、本地轻模型、本地生成模型和可选云增强，不再直接保存或使用外部模型 API key。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(12.dp))
            Text("AI 模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiOperatingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = aiModeDraft == mode,
                        onClick = { onAiModeDraftChange(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                aiModeDraft.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cloudEnhancementBaseUrlDraft,
                onValueChange = onCloudEnhancementBaseUrlDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("云增强服务端点") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = lightweightModelPackageDraft,
                onValueChange = onLightweightModelPackageDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("本地轻模型包 ID") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = generativeModelPackageDraft,
                onValueChange = onGenerativeModelPackageDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("本地生成模型包 ID") },
            )
            Spacer(Modifier.height(12.dp))
            Text("模型下载策略", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelDownloadPolicy.entries.forEach { policy ->
                    FilterChip(
                        selected = modelDownloadPolicyDraft == policy,
                        onClick = { onModelDownloadPolicyDraftChange(policy) },
                        label = { Text(policy.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSavePreferences,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
            ) {
                Text(if (isBusy) "保存中..." else "保存 AI 策略")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "当前模式：${currentPreferences?.aiMode?.label ?: AiOperatingMode.LOCAL_ONLY.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "当前云增强端点：${currentPreferences?.cloudEnhancementBaseUrl?.ifBlank { "未配置" } ?: "未配置"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "轻模型：${currentPreferences?.localLightweightModelPackageId ?: AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "生成模型：${currentPreferences?.localGenerativeModelPackageId ?: AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "下载策略：${currentPreferences?.modelDownloadPolicy?.label ?: ModelDownloadPolicy.PREBUNDLED.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Panel {
            Text("账户密码", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(
                "默认凭据只允许首次初始化使用，后续不能再改回 MindWeave / MindWeave。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(12.dp))
            LabeledValue(
                label = "当前账号",
                value = currentAccount?.username ?: "未初始化",
            )
            LabeledValue(
                label = "凭据状态",
                value = if (currentAccount?.mustChangeCredentials == true) "待修改" else "已启用",
            )
            LabeledValue(
                label = "账号创建时间",
                value = currentAccount?.createdAtEpochMs?.let(::formatMoment) ?: "未知",
            )
            LabeledValue(
                label = "凭据最近更新时间",
                value = currentAccount?.credentialsUpdatedAtEpochMs?.let(::formatMoment) ?: "未知",
            )
            LabeledValue(
                label = "最近登录时间",
                value = currentAccount?.lastLoginAtEpochMs?.let(::formatMoment) ?: "尚未记录",
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = profileUsername,
                onValueChange = onProfileUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("新账号") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = profileCurrentPassword,
                onValueChange = onProfileCurrentPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("当前密码") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = profileNewPassword,
                onValueChange = onProfileNewPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("新密码") },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = profileConfirmPassword,
                onValueChange = onProfileConfirmPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("确认新密码") },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onChangeCredentials,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
            ) {
                Text(if (isBusy) "保存中..." else "更新账户信息")
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
            ) {
                Text("退出登录")
            }
        }
        Panel {
            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HeroPanel(
    syncState: SyncState,
    statusLine: String,
) {
    Panel {
        Text("思织", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "半离线、隐私优先、支持多设备同步的个人 AI 日记系统。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBadge("待同步", syncState.pendingChanges.toString())
            MetricBadge("同步游标", syncState.lastSyncSeq.toString())
            MetricBadge("数据源", "SQLite")
        }
        Spacer(Modifier.height(12.dp))
        Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryPanel(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    mood: DiaryMood,
    onMoodChange: (DiaryMood) -> Unit,
    tags: String,
    onTagsChange: (String) -> Unit,
    timeline: List<DiaryTimelineItem>,
    onSave: () -> Unit,
) {
    Panel {
        Text("日记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            modifier = Modifier.fillMaxWidth().height(150.dp),
            label = { Text("正文") },
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiaryMood.entries.forEach {
                FilterChip(
                    selected = it == mood,
                    onClick = { onMoodChange(it) },
                    label = { Text(it.label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标签") },
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存日记")
        }
        Spacer(Modifier.height(16.dp))
        Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            timeline.take(4).forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.entry.title, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatMoment(item.entry.createdAtEpochMs)} · ${item.entry.mood.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(item.entry.content.take(90) + if (item.entry.content.length > 90) "..." else "")
                        if (item.entry.aiSummary != null) {
                            Text(
                                "AI 总结：${item.entry.aiSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedulePanel(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    type: ScheduleType,
    onTypeChange: (ScheduleType) -> Unit,
    startAfterHours: String,
    onStartAfterHoursChange: (String) -> Unit,
    durationHours: String,
    onDurationHoursChange: (String) -> Unit,
    schedules: List<ScheduleEvent>,
    onSave: () -> Unit,
) {
    Panel {
        Text("日程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题") },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("说明") },
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleType.entries.forEach {
                FilterChip(
                    selected = it == type,
                    onClick = { onTypeChange(it) },
                    label = { Text(it.label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = startAfterHours,
                onValueChange = onStartAfterHoursChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("几小时后开始") },
            )
            OutlinedTextField(
                value = durationHours,
                onValueChange = onDurationHoursChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("持续小时") },
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存日程")
        }
        Spacer(Modifier.height(16.dp))
        Text("接下来", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            schedules.take(5).forEach { event ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.title, fontWeight = FontWeight.Medium)
                        Text(
                            "${event.type.label} · ${formatMoment(event.startTimeEpochMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        if (event.description.isNotBlank()) {
                            Text(event.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantPanel(
    sessions: List<org.example.mindweave.domain.model.ChatSession>,
    selectedSessionId: String?,
    onSelectSession: (String) -> Unit,
    conversation: List<ChatMessage>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onSync: () -> Unit,
    syncState: SyncState,
    isBusy: Boolean,
) {
    Panel {
        Text("AI 对话与同步", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSync, modifier = Modifier.weight(1f)) {
                Text(if (isBusy) "处理中..." else "立即同步")
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Outbox ${syncState.pendingChanges}", style = MaterialTheme.typography.titleMedium)
                    Text("Cursor ${syncState.lastSyncSeq}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("会话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sessions.take(3).forEach { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.id == selectedSessionId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    onClick = { onSelectSession(session.id) },
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(session.title, fontWeight = FontWeight.Medium)
                        Text(
                            formatMoment(session.updatedAtEpochMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conversation.isEmpty()) {
                    Text("还没有对话记录。你可以直接发起一个问题。")
                } else {
                    conversation.takeLast(6).forEach { message ->
                        Text(
                            text = "${message.role.prettyLabel()}: ${message.content}",
                            color = if (message.role == ChatRole.ASSISTANT) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("向 AI 说点什么") },
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            Text("发送并写入本地对话")
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetricBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun Long?.orDefault(default: Long): Long = this ?: default

private fun ChatRole.prettyLabel(): String = when (this) {
    ChatRole.USER -> "你"
    ChatRole.ASSISTANT -> "思织 AI"
    ChatRole.SYSTEM -> "系统"
}
