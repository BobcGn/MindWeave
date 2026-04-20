package org.example.mindweave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.example.mindweave.ai.AiSettings
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
    aiSettings: AiSettings = AiSettings.Disabled,
) {
    val graph = remember(platformContext, aiSettings) {
        createMindWeaveAppGraph(platformContext, aiSettings)
    }
    val facade = graph.facade
    val scope = rememberCoroutineScope()

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

    LaunchedEffect(facade) {
        facade.seedDemoData()
    }
    LaunchedEffect(sessions) {
        if (selectedSessionId == null) {
            selectedSessionId = sessions.firstOrNull()?.id
        }
    }

    MaterialTheme(colorScheme = MindWeaveColors) {
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
                if (compact) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeroPanel(syncState = syncState, statusLine = statusLine)
                        DiaryPanel(
                            title = diaryTitle,
                            onTitleChange = { diaryTitle = it },
                            body = diaryBody,
                            onBodyChange = { diaryBody = it },
                            mood = diaryMood,
                            onMoodChange = { diaryMood = it },
                            tags = diaryTags,
                            onTagsChange = { diaryTags = it },
                            timeline = timeline,
                            onSave = {
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
                            },
                        )
                        SchedulePanel(
                            title = scheduleTitle,
                            onTitleChange = { scheduleTitle = it },
                            description = scheduleDesc,
                            onDescriptionChange = { scheduleDesc = it },
                            type = scheduleType,
                            onTypeChange = { scheduleType = it },
                            startAfterHours = startAfterHours,
                            onStartAfterHoursChange = { startAfterHours = it },
                            durationHours = durationHours,
                            onDurationHoursChange = { durationHours = it },
                            schedules = schedules,
                            onSave = {
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
                            },
                        )
                        AssistantPanel(
                            sessions = sessions,
                            selectedSessionId = selectedSessionId,
                            onSelectSession = { selectedSessionId = it },
                            conversation = conversation,
                            prompt = prompt,
                            onPromptChange = { prompt = it },
                            onSend = {
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
                            },
                            onSync = {
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
                            },
                            syncState = syncState,
                            isBusy = isBusy,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HeroPanel(syncState = syncState, statusLine = statusLine)
                            DiaryPanel(
                                title = diaryTitle,
                                onTitleChange = { diaryTitle = it },
                                body = diaryBody,
                                onBodyChange = { diaryBody = it },
                                mood = diaryMood,
                                onMoodChange = { diaryMood = it },
                                tags = diaryTags,
                                onTagsChange = { diaryTags = it },
                                timeline = timeline,
                                onSave = {
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
                                },
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SchedulePanel(
                                title = scheduleTitle,
                                onTitleChange = { scheduleTitle = it },
                                description = scheduleDesc,
                                onDescriptionChange = { scheduleDesc = it },
                                type = scheduleType,
                                onTypeChange = { scheduleType = it },
                                startAfterHours = startAfterHours,
                                onStartAfterHoursChange = { startAfterHours = it },
                                durationHours = durationHours,
                                onDurationHoursChange = { durationHours = it },
                                schedules = schedules,
                                onSave = {
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
                                },
                            )
                            AssistantPanel(
                                sessions = sessions,
                                selectedSessionId = selectedSessionId,
                                onSelectSession = { selectedSessionId = it },
                                conversation = conversation,
                                prompt = prompt,
                                onPromptChange = { prompt = it },
                                onSend = {
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
                                },
                                onSync = {
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
                                },
                                syncState = syncState,
                                isBusy = isBusy,
                            )
                        }
                    }
                }
            }
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
