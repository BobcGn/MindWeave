import promptAction from '@ohos.promptAction';
import {
  BridgeAccountState,
  BridgeChatMessage,
  BridgeChatSession,
  BridgePreferenceState,
  BridgeResponse,
  BridgeScheduleEvent,
  BridgeSnapshot,
  BridgeTimelineItem,
  emptySnapshot,
  mindWeaveBridge
} from '../bridge/MindWeaveBridge';

interface SectionOption {
  id: string;
  shortLabel: string;
  title: string;
  description: string;
}

interface OptionItem {
  value: string;
  label: string;
  description?: string;
}

const SECTION_OPTIONS: SectionOption[] = [
  { id: 'DIARY', shortLabel: '记', title: '日记', description: '记录情绪、标签和 AI 总结' },
  { id: 'SCHEDULE', shortLabel: '程', title: '日程', description: '安排待办与提醒节奏' },
  { id: 'ASSISTANT', shortLabel: 'AI', title: '助手', description: '查看对话并执行同步' },
  { id: 'PERSONAL', shortLabel: '我', title: '个人', description: '管理本地优先 AI 与账户密码' }
];

const DIARY_MOOD_OPTIONS: OptionItem[] = [
  { value: 'CALM', label: '平静' },
  { value: 'ENERGIZED', label: '有劲' },
  { value: 'HEAVY', label: '沉重' },
  { value: 'GRATEFUL', label: '感激' },
  { value: 'UNCERTAIN', label: '摇摆' }
];

const SCHEDULE_TYPE_OPTIONS: OptionItem[] = [
  { value: 'WORK', label: '工作' },
  { value: 'LIFE', label: '生活' },
  { value: 'HEALTH', label: '健康' },
  { value: 'FAMILY', label: '家庭' },
  { value: 'REFLECTION', label: '复盘' }
];

const AI_MODE_OPTIONS: OptionItem[] = [
  { value: 'LOCAL_ONLY', label: '仅本地', description: '所有 AI 任务都优先在端侧完成，不访问云端增强。' },
  { value: 'LOCAL_FIRST_CLOUD_ENHANCEMENT', label: '本地优先', description: '优先尝试本地能力，本地能力不足时允许云端增强。' },
  { value: 'MANUAL_CLOUD_ENHANCEMENT', label: '手动云增强', description: '默认只用本地能力，仅在显式允许时才调用云端增强。' },
  { value: 'DISABLED', label: '完全关闭', description: '关闭所有 AI 相关处理。' }
];

const DOWNLOAD_POLICY_OPTIONS: OptionItem[] = [
  { value: 'PREBUNDLED', label: '预置模型' },
  { value: 'WIFI_ONLY', label: '仅 Wi-Fi' },
  { value: 'BACKGROUND_ALLOWED', label: '允许后台' },
  { value: 'MANUAL_ONLY', label: '手动下载' }
];

@Entry
@Component
struct Index {
  @State private statusMessage: string = '准备初始化 MindWeave Harmony bridge...';
  @State private snapshot: BridgeSnapshot = emptySnapshot();
  @State private selectedSectionId: string = 'DIARY';
  @State private focusSessionId: string = '';

  @State private diaryTitle: string = '';
  @State private diaryContent: string = '';
  @State private diaryMood: string = 'CALM';
  @State private diaryTags: string = '工作, 恢复';

  @State private scheduleTitle: string = '';
  @State private scheduleDescription: string = '';
  @State private scheduleType: string = 'WORK';
  @State private startAfterHours: string = '2';
  @State private durationHours: string = '1';

  @State private chatPrompt: string = '';

  @State private isAuthenticated: boolean = false;
  @State private requiresCredentialReset: boolean = false;

  @State private loginUsername: string = '';
  @State private loginPassword: string = '';
  @State private loginStatusLine: string = '首次使用请使用默认账号 MindWeave 登录。';

  @State private resetUsername: string = '';
  @State private resetPassword: string = '';
  @State private resetConfirmPassword: string = '';
  @State private resetStatusLine: string = '首次登录后必须先修改账号和密码。';

  @State private profileUsername: string = '';
  @State private profileCurrentPassword: string = '';
  @State private profileNewPassword: string = '';
  @State private profileConfirmPassword: string = '';
  @State private personalStatusLine: string = '在这里管理本地优先 AI、云增强开关与账户密码。';

  @State private aiModeDraft: string = 'LOCAL_ONLY';
  @State private cloudEnhancementBaseUrlDraft: string = '';
  @State private lightweightModelPackageDraft: string = 'mindweave-lite-core';
  @State private generativeModelPackageDraft: string = 'mindweave-gen-core';
  @State private modelDownloadPolicyDraft: string = 'PREBUNDLED';

  @State private loadedAccountVersion: number = 0;

  aboutToAppear(): void {
    this.applyResponse(mindWeaveBridge.bootstrap());
  }

  private applyResponse(response: BridgeResponse): void {
    this.statusMessage = response.message;
    if (response.snapshot) {
      this.snapshot = response.snapshot;
      if (response.snapshot.account) {
        this.syncAccountState(response.snapshot.account);
      }
      if (response.snapshot.preferences) {
        this.syncPreferenceDrafts(response.snapshot.preferences);
      }
      if (response.snapshot.chatSessions.length > 0 && this.focusSessionId.length === 0) {
        this.focusSessionId = response.snapshot.chatSessions[0].id;
      }
    }
    this.focusSessionId = response.focusSessionId ?? this.focusSessionId;
  }

  private syncAccountState(account: BridgeAccountState): void {
    if (this.loadedAccountVersion === account.updatedAtEpochMs) {
      return;
    }
    if (!this.isAuthenticated) {
      this.loginUsername = account.username;
      this.loginPassword = account.mustChangeCredentials ? 'MindWeave' : '';
      this.loginStatusLine = account.mustChangeCredentials
        ? '请使用默认凭据登录，并立即完成首次改密。'
        : '请输入你已设置的账号和密码登录。';
    }
    this.resetUsername = account.username;
    this.profileUsername = account.username;
    this.loadedAccountVersion = account.updatedAtEpochMs;
  }

  private syncPreferenceDrafts(preferences: BridgePreferenceState): void {
    this.aiModeDraft = preferences.aiMode;
    this.cloudEnhancementBaseUrlDraft = preferences.cloudEnhancementBaseUrl;
    this.lightweightModelPackageDraft = preferences.localLightweightModelPackageId;
    this.generativeModelPackageDraft = preferences.localGenerativeModelPackageId;
    this.modelDownloadPolicyDraft = preferences.modelDownloadPolicy;
  }

  private safeToast(message: string): void {
    try {
      promptAction.showToast({ message });
    } catch (error) {
      console.error(`Failed to show toast: ${message}`);
    }
  }

  private refresh(selectedSessionId?: string): void {
    this.applyResponse(mindWeaveBridge.getSnapshot(selectedSessionId));
  }

  private parseTags(raw: string): string[] {
    const tags: string[] = [];
    raw.split(',').forEach((tag: string) => {
      const trimmed = tag.trim();
      if (trimmed.length > 0) {
        tags.push(trimmed);
      }
    });
    return tags;
  }

  private parseHourOffset(value: string, fallback: number): number {
    const parsed = Number(value);
    if (Number.isNaN(parsed) || parsed <= 0) {
      return fallback;
    }
    return parsed;
  }

  private loginUser(): void {
    if (this.loginUsername.trim().length === 0 || this.loginPassword.length === 0) {
      this.loginStatusLine = '请输入账号和密码。';
      return;
    }
    const response = mindWeaveBridge.authenticate(this.loginUsername.trim(), this.loginPassword);
    this.applyResponse(response);
    if (!response.ok) {
      this.loginStatusLine = response.message;
      return;
    }
    const shouldReset = response.snapshot?.account?.mustChangeCredentials === true;
    this.isAuthenticated = true;
    this.requiresCredentialReset = shouldReset;
    this.resetPassword = '';
    this.resetConfirmPassword = '';
    this.loginStatusLine = response.message;
    if (shouldReset) {
      this.resetStatusLine = '首次登录后必须先修改账号和密码。';
    }
  }

  private completeCredentialReset(): void {
    if (this.resetUsername.trim().length === 0) {
      this.resetStatusLine = '新账号不能为空。';
      return;
    }
    if (this.resetPassword.length === 0) {
      this.resetStatusLine = '新密码不能为空。';
      return;
    }
    if (this.resetPassword !== this.resetConfirmPassword) {
      this.resetStatusLine = '两次输入的新密码不一致。';
      return;
    }
    if (this.resetUsername.trim() === 'MindWeave') {
      this.resetStatusLine = '初始账号必须修改。';
      return;
    }
    if (this.resetPassword === 'MindWeave') {
      this.resetStatusLine = '初始密码必须修改。';
      return;
    }

    const response = mindWeaveBridge.forceResetCredentials(this.resetUsername.trim(), this.resetPassword);
    this.applyResponse(response);
    if (!response.ok) {
      this.resetStatusLine = response.message;
      return;
    }

    this.requiresCredentialReset = false;
    this.selectedSectionId = 'PERSONAL';
    this.loginUsername = this.resetUsername.trim();
    this.loginPassword = '';
    this.profileCurrentPassword = '';
    this.profileNewPassword = '';
    this.profileConfirmPassword = '';
    this.personalStatusLine = '已完成首次账号配置。接下来可以检查本地优先 AI 与云增强策略。';
    this.loginStatusLine = '默认凭据已停用，后续请使用新账号和密码登录。';
  }

  private submitDiary(): void {
    if (this.diaryContent.trim().length === 0) {
      this.safeToast('先写下一段正文。');
      return;
    }
    this.applyResponse(
      mindWeaveBridge.captureDiaryWithDetails(
        this.diaryTitle,
        this.diaryContent,
        this.diaryMood,
        this.parseTags(this.diaryTags)
      )
    );
    this.diaryTitle = '';
    this.diaryContent = '';
    this.diaryMood = 'CALM';
    this.diaryTags = '工作, 恢复';
  }

  private submitSchedule(): void {
    if (this.scheduleTitle.trim().length === 0) {
      this.safeToast('先写下日程标题。');
      return;
    }
    const startTime = Date.now() + this.parseHourOffset(this.startAfterHours, 2) * 60 * 60 * 1000;
    const endTime = startTime + this.parseHourOffset(this.durationHours, 1) * 60 * 60 * 1000;
    this.applyResponse(
      mindWeaveBridge.captureScheduleWithType(
        this.scheduleTitle,
        this.scheduleDescription,
        startTime,
        endTime,
        this.scheduleType
      )
    );
    this.scheduleTitle = '';
    this.scheduleDescription = '';
    this.scheduleType = 'WORK';
    this.startAfterHours = '2';
    this.durationHours = '1';
  }

  private submitChat(): void {
    if (this.chatPrompt.trim().length === 0) {
      this.safeToast('先写下你想和 AI 讨论的内容。');
      return;
    }
    this.applyResponse(mindWeaveBridge.sendChatMessage(this.focusSessionId, this.chatPrompt));
    this.chatPrompt = '';
  }

  private savePreferences(): void {
    if (this.lightweightModelPackageDraft.trim().length === 0) {
      this.personalStatusLine = '本地轻模型包 ID 不能为空。';
      return;
    }
    if (this.generativeModelPackageDraft.trim().length === 0) {
      this.personalStatusLine = '本地生成模型包 ID 不能为空。';
      return;
    }

    const response = mindWeaveBridge.savePreferences(
      this.aiModeDraft,
      this.cloudEnhancementBaseUrlDraft.trim(),
      this.lightweightModelPackageDraft.trim(),
      this.generativeModelPackageDraft.trim(),
      this.modelDownloadPolicyDraft
    );
    this.applyResponse(response);
    if (!response.ok) {
      this.personalStatusLine = response.message;
      return;
    }
    if (this.aiModeDraft === 'DISABLED') {
      this.personalStatusLine = 'AI 已关闭。主流程仍然保持本地可用。';
    } else if (
      this.aiModeDraft !== 'LOCAL_ONLY' &&
      this.cloudEnhancementBaseUrlDraft.trim().length === 0
    ) {
      this.personalStatusLine = '已保存 AI 策略。当前未配置云增强端点，系统会继续只使用本地能力。';
    } else {
      this.personalStatusLine = '已保存 AI 策略。系统将按本地优先架构执行。';
    }
  }

  private changeCredentials(): void {
    if (this.profileCurrentPassword.length === 0) {
      this.personalStatusLine = '请输入当前密码。';
      return;
    }
    if (this.profileUsername.trim().length === 0) {
      this.personalStatusLine = '账号不能为空。';
      return;
    }
    if (this.profileNewPassword.length === 0) {
      this.personalStatusLine = '新密码不能为空。';
      return;
    }
    if (this.profileNewPassword !== this.profileConfirmPassword) {
      this.personalStatusLine = '两次输入的新密码不一致。';
      return;
    }
    if (this.profileUsername.trim() === 'MindWeave') {
      this.personalStatusLine = '默认账号不能继续使用，请更换为个人账号。';
      return;
    }
    if (this.profileNewPassword === 'MindWeave') {
      this.personalStatusLine = '默认密码不能继续使用，请设置新密码。';
      return;
    }

    const response = mindWeaveBridge.changeCredentials(
      this.profileCurrentPassword,
      this.profileUsername.trim(),
      this.profileNewPassword
    );
    this.applyResponse(response);
    if (!response.ok) {
      this.personalStatusLine = response.message;
      return;
    }

    this.loginUsername = this.profileUsername.trim();
    this.profileCurrentPassword = '';
    this.profileNewPassword = '';
    this.profileConfirmPassword = '';
    this.personalStatusLine = response.message;
  }

  private signOut(): void {
    this.isAuthenticated = false;
    this.requiresCredentialReset = false;
    this.loginPassword = '';
    this.resetPassword = '';
    this.resetConfirmPassword = '';
    this.selectedSectionId = 'DIARY';
    this.loginStatusLine = '已退出登录。';
  }

  private bridgeModeLabel(): string {
    return this.statusMessage.indexOf('demo bridge') >= 0 ? '演示桥接' : '共享桥接';
  }

  private selectedSection(): SectionOption {
    let matched: SectionOption = SECTION_OPTIONS[0];
    SECTION_OPTIONS.forEach((option: SectionOption) => {
      if (option.id === this.selectedSectionId) {
        matched = option;
      }
    });
    return matched;
  }

  private aiModeDescription(): string {
    let description = '所有 AI 任务都优先在端侧完成，不访问云端增强。';
    AI_MODE_OPTIONS.forEach((option: OptionItem) => {
      if (option.value === this.aiModeDraft && option.description) {
        description = option.description;
      }
    });
    return description;
  }

  private currentCloudEndpointLabel(): string {
    const endpoint = this.snapshot.preferences?.cloudEnhancementBaseUrl ?? '';
    return endpoint.length > 0 ? endpoint : '未配置';
  }

  private diaryMoodLabel(value: string): string {
    let label = value;
    DIARY_MOOD_OPTIONS.forEach((option: OptionItem) => {
      if (option.value === value) {
        label = option.label;
      }
    });
    return label;
  }

  private scheduleTypeLabel(value: string): string {
    let label = value;
    SCHEDULE_TYPE_OPTIONS.forEach((option: OptionItem) => {
      if (option.value === value) {
        label = option.label;
      }
    });
    return label;
  }

  private chatRoleLabel(value: string): string {
    if (value === 'ASSISTANT') {
      return 'AI 助手';
    }
    if (value === 'SYSTEM') {
      return '系统';
    }
    return '你';
  }

  private trimPreview(value: string, maxLength: number): string {
    return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
  }

  private formatMoment(epochMs: number): string {
    const date = new Date(epochMs);
    const month = this.pad2(date.getMonth() + 1);
    const day = this.pad2(date.getDate());
    const hours = this.pad2(date.getHours());
    const minutes = this.pad2(date.getMinutes());
    return `${month}-${day} ${hours}:${minutes}`;
  }

  private formatMomentOrPlaceholder(epochMs: number | undefined, placeholder: string): string {
    if (epochMs === undefined || epochMs <= 0) {
      return placeholder;
    }
    return this.formatMoment(epochMs);
  }

  private pad2(value: number): string {
    return value < 10 ? `0${value}` : `${value}`;
  }

  @Builder
  private authFrame(title: string, description: string): void {
    Column({ space: 14 }) {
      Text(title)
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor('#2C2520');
      Text(description)
        .fontSize(15)
        .fontColor('#6B5A4D');
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start);
  }

  @Builder
  private loadingPanel(): void {
    Column({ space: 18 }) {
      this.authFrame(
        '正在准备本地账户与配置',
        '首次启动会自动创建默认账户、AI 策略与演示数据。'
      );
      Text('MindWeave 会先完成本地初始化，再进入登录流程。')
        .fontSize(14)
        .fontColor('#8C4A37');
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .padding(24)
    .backgroundColor('#F4EDE4');
  }

  @Builder
  private loginPanel(): void {
    Column({ space: 14 }) {
      this.authFrame(
        '登录',
        this.snapshot.account?.mustChangeCredentials === true
          ? '首次使用默认账号与密码均为 MindWeave，登录后必须修改。'
          : '请输入你已经设置过的账号和密码登录。默认凭据只用于首次初始化。'
      );

      TextInput({ text: this.loginUsername, placeholder: '账号' })
        .backgroundColor('#FFFDFB')
        .onChange((value: string) => { this.loginUsername = value; });
      TextInput({ text: this.loginPassword, placeholder: '密码' })
        .backgroundColor('#FFFDFB')
        .type(InputType.Password)
        .onChange((value: string) => { this.loginPassword = value; });

      Button('进入 MindWeave')
        .width('100%')
        .backgroundColor('#8C4A37')
        .fontColor('#FFF7F2')
        .onClick(() => this.loginUser());

      if (this.snapshot.account) {
        Text(
          this.snapshot.account.mustChangeCredentials
            ? `当前初始化账号：${this.snapshot.account.username}`
            : `当前本地账号：${this.snapshot.account.username}`
        )
          .fontSize(12)
          .fontColor('#55726E');
      }

      Text(this.loginStatusLine)
        .fontSize(14)
        .fontColor('#8C4A37');
    }
    .padding(24)
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#F4EDE4');
  }

  @Builder
  private credentialResetPanel(): void {
    Column({ space: 14 }) {
      this.authFrame(
        '首次改密',
        '为了后续正常登录，请立即替换初始账号与密码。'
      );

      TextInput({ text: this.resetUsername, placeholder: '新账号' })
        .backgroundColor('#FFFDFB')
        .onChange((value: string) => { this.resetUsername = value; });
      TextInput({ text: this.resetPassword, placeholder: '新密码' })
        .backgroundColor('#FFFDFB')
        .type(InputType.Password)
        .onChange((value: string) => { this.resetPassword = value; });
      TextInput({ text: this.resetConfirmPassword, placeholder: '确认新密码' })
        .backgroundColor('#FFFDFB')
        .type(InputType.Password)
        .onChange((value: string) => { this.resetConfirmPassword = value; });

      Button('保存并进入')
        .width('100%')
        .backgroundColor('#8C4A37')
        .fontColor('#FFF7F2')
        .onClick(() => this.completeCredentialReset());

      Text(this.resetStatusLine)
        .fontSize(14)
        .fontColor('#8C4A37');
    }
    .padding(24)
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#F4EDE4');
  }

  @Builder
  private heroPanel(): void {
    Column({ space: 14 }) {
      Column({ space: 8 }) {
        Text('思织')
          .fontSize(30)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        Text('MindWeave / 半离线、隐私优先、支持多设备同步的个人 AI 日记系统。')
          .fontSize(14)
          .fontColor('#6B5A4D');
      }
      .alignItems(HorizontalAlign.Start);

      Scroll() {
        Row({ space: 8 }) {
          Text(this.bridgeModeLabel())
            .fontSize(12)
            .fontColor('#FFF7F2')
            .backgroundColor(this.bridgeModeLabel() === '演示桥接' ? '#B26A3F' : '#55726E')
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .borderRadius(16);
          Text(`设备：${this.snapshot.session.deviceName}`)
            .fontSize(12)
            .fontColor('#6B5A4D')
            .backgroundColor('#F6E7DA')
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .borderRadius(16);
          Text(`账号：${this.snapshot.account?.username ?? '准备中'}`)
            .fontSize(12)
            .fontColor('#6B5A4D')
            .backgroundColor('#F6E7DA')
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .borderRadius(16);
        }
      }
      .scrollBar(BarState.Off)
      .width('100%');

      Text(this.statusMessage)
        .fontSize(14)
        .fontColor('#8C4A37');

      Row({ space: 10 }) {
        Button('刷新')
          .backgroundColor('#8C4A37')
          .fontColor('#FFF7F2')
          .onClick(() => this.refresh(this.focusSessionId));
        Button('同步')
          .backgroundColor('#55726E')
          .fontColor('#FFF7F2')
          .onClick(() => this.applyResponse(mindWeaveBridge.runSync()));
      }

      Row({ space: 12 }) {
        Column({ space: 4 }) {
          Text('待同步')
            .fontSize(12)
            .fontColor('#8B7766');
          Text(`${this.snapshot.syncState.pendingChanges}`)
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor('#2C2520');
        }
        .padding(14)
        .width('31%')
        .backgroundColor('#FFF7F2')
        .borderRadius(20)
        .alignItems(HorizontalAlign.Start);

        Column({ space: 4 }) {
          Text('同步游标')
            .fontSize(12)
            .fontColor('#8B7766');
          Text(`${this.snapshot.syncState.lastSyncSeq}`)
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor('#2C2520');
        }
        .padding(14)
        .width('31%')
        .backgroundColor('#F6FFFD')
        .borderRadius(20)
        .alignItems(HorizontalAlign.Start);

        Column({ space: 4 }) {
          Text('数据源')
            .fontSize(12)
            .fontColor('#8B7766');
          Text('SQLite')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor('#2C2520');
        }
        .padding(14)
        .width('31%')
        .backgroundColor('#FFFDFB')
        .borderRadius(20)
        .alignItems(HorizontalAlign.Start);
      }
    }
    .padding(18)
    .backgroundColor('#EFE1D3')
    .borderRadius(28)
    .alignItems(HorizontalAlign.Start);
  }

  @Builder
  private sectionHeader(): void {
    Row({ space: 14 }) {
      Text(this.selectedSection().shortLabel)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor('#2C2520')
        .backgroundColor('#FFFDFB')
        .padding({ left: 14, right: 14, top: 9, bottom: 9 })
        .borderRadius(18);

      Column({ space: 2 }) {
        Text(this.selectedSection().title)
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        Text(this.selectedSection().description)
          .fontSize(14)
          .fontColor('#6B5A4D');
      }
      .alignItems(HorizontalAlign.Start);
    }
    .width('100%')
    .padding(18)
    .backgroundColor('#E9DCCF')
    .borderRadius(24);
  }

  @Builder
  private compactNavigationBar(): void {
    Row({ space: 10 }) {
      ForEach(SECTION_OPTIONS, (option: SectionOption) => {
        Column({ space: 4 }) {
          Text(option.shortLabel)
            .fontSize(14)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.selectedSectionId === option.id ? '#FFF7F2' : '#2C2520')
            .backgroundColor(this.selectedSectionId === option.id ? '#8C4A37' : '#FFFDFB')
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .borderRadius(16);
          Text(option.title)
            .fontSize(12)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.selectedSectionId === option.id ? '#8C4A37' : '#6B5A4D');
        }
        .layoutWeight(1)
        .padding({ top: 10, bottom: 10 })
        .backgroundColor(this.selectedSectionId === option.id ? '#F1DDD0' : '#FFF7F2')
        .borderRadius(22)
        .onClick(() => { this.selectedSectionId = option.id; });
      }, (option: SectionOption): string => option.id);
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .backgroundColor('#FFF7F2')
    .border({
      width: 1,
      color: '#E8D8C7'
    });
  }

  @Builder
  private diarySection(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('日记')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        TextInput({ text: this.diaryTitle, placeholder: '标题' })
          .backgroundColor('#FFFDFB')
          .onChange((value: string) => { this.diaryTitle = value; });
        TextArea({ text: this.diaryContent, placeholder: '正文' })
          .height(140)
          .backgroundColor('#FFFDFB')
          .onChange((value: string) => { this.diaryContent = value; });

        Text('情绪')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#4C4139');
        Scroll() {
          Row({ space: 8 }) {
            ForEach(DIARY_MOOD_OPTIONS, (option: OptionItem) => {
              Button(option.label)
                .fontSize(12)
                .fontColor(this.diaryMood === option.value ? '#FFF7F2' : '#2C2520')
                .backgroundColor(this.diaryMood === option.value ? '#8C4A37' : '#E8D8C7')
                .onClick(() => { this.diaryMood = option.value; });
            }, (option: OptionItem): string => option.value);
          }
        }
        .scrollBar(BarState.Off)
        .width('100%');

        TextInput({ text: this.diaryTags, placeholder: '标签' })
          .backgroundColor('#FFFDFB')
          .onChange((value: string) => { this.diaryTags = value; });

        Button('保存日记')
          .backgroundColor('#8C4A37')
          .fontColor('#FFF7F2')
          .onClick(() => this.submitDiary());
      }
      .padding(18)
      .backgroundColor('#FFF7F2')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column({ space: 12 }) {
        Text('最近记录')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        if (this.snapshot.timeline.length === 0) {
          Text('还没有日记记录。')
            .fontSize(14)
            .fontColor('#6B5A4D');
        }
        ForEach(this.snapshot.timeline.slice(0, 4), (item: BridgeTimelineItem) => {
          Column({ space: 8 }) {
            Text(item.entry.title.length > 0 ? item.entry.title : '未命名日记')
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor('#2C2520');
            Text(`${this.formatMoment(item.entry.createdAtEpochMs)} · ${this.diaryMoodLabel(item.entry.mood)}`)
              .fontSize(12)
              .fontColor('#8B7766');
            Text(this.trimPreview(item.entry.content, 90))
              .fontSize(14)
              .fontColor('#4C4139');
            if (item.entry.aiSummary) {
              Text(`AI 总结：${item.entry.aiSummary}`)
                .fontSize(13)
                .fontColor('#8C4A37');
            }
          }
          .padding(16)
          .width('100%')
          .backgroundColor('#FFFDFB')
          .borderRadius(20)
          .alignItems(HorizontalAlign.Start);
        }, (item: BridgeTimelineItem): string => item.entry.id);
      }
      .padding(18)
      .backgroundColor('#FFF7F2')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);
    }
  }

  @Builder
  private scheduleSection(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('日程')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        TextInput({ text: this.scheduleTitle, placeholder: '标题' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.scheduleTitle = value; });
        TextInput({ text: this.scheduleDescription, placeholder: '说明' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.scheduleDescription = value; });

        Text('类型')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#4C4139');
        Scroll() {
          Row({ space: 8 }) {
            ForEach(SCHEDULE_TYPE_OPTIONS, (option: OptionItem) => {
              Button(option.label)
                .fontSize(12)
                .fontColor(this.scheduleType === option.value ? '#FFF7F2' : '#2C2520')
                .backgroundColor(this.scheduleType === option.value ? '#55726E' : '#D7ECE7')
                .onClick(() => { this.scheduleType = option.value; });
            }, (option: OptionItem): string => option.value);
          }
        }
        .scrollBar(BarState.Off)
        .width('100%');

        Row({ space: 10 }) {
          TextInput({ text: this.startAfterHours, placeholder: '几小时后开始' })
            .width('48%')
            .backgroundColor('#FFFFFF')
            .onChange((value: string) => { this.startAfterHours = value; });
          TextInput({ text: this.durationHours, placeholder: '持续小时' })
            .width('48%')
            .backgroundColor('#FFFFFF')
            .onChange((value: string) => { this.durationHours = value; });
        }

        Button('保存日程')
          .backgroundColor('#55726E')
          .fontColor('#FFF7F2')
          .onClick(() => this.submitSchedule());
      }
      .padding(18)
      .backgroundColor('#F6FFFD')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column({ space: 12 }) {
        Text('接下来')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        if (this.snapshot.schedules.length === 0) {
          Text('还没有后续日程。')
            .fontSize(14)
            .fontColor('#6B5A4D');
        }
        ForEach(this.snapshot.schedules.slice(0, 5), (item: BridgeScheduleEvent) => {
          Column({ space: 8 }) {
            Text(item.title)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor('#2C2520');
            Text(`${this.scheduleTypeLabel(item.type)} · ${this.formatMoment(item.startTimeEpochMs)}`)
              .fontSize(12)
              .fontColor('#55726E');
            if (item.description.length > 0) {
              Text(item.description)
                .fontSize(14)
                .fontColor('#4C4139');
            }
          }
          .padding(16)
          .width('100%')
          .backgroundColor('#FFFFFF')
          .borderRadius(20)
          .alignItems(HorizontalAlign.Start);
        }, (item: BridgeScheduleEvent): string => item.id);
      }
      .padding(18)
      .backgroundColor('#F6FFFD')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);
    }
  }

  @Builder
  private assistantSection(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('AI 对话与同步')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        Row({ space: 10 }) {
          Button('立即同步')
            .width('48%')
            .backgroundColor('#55726E')
            .fontColor('#FFF7F2')
            .onClick(() => this.applyResponse(mindWeaveBridge.runSync()));

          Column({ space: 4 }) {
            Text(`Outbox ${this.snapshot.syncState.pendingChanges}`)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor('#2C2520');
            Text(`Cursor ${this.snapshot.syncState.lastSyncSeq}`)
              .fontSize(12)
              .fontColor('#6B5A4D');
          }
          .width('48%')
          .padding(12)
          .backgroundColor('#F4EDE4')
          .borderRadius(18)
          .alignItems(HorizontalAlign.Start);
        }
      }
      .padding(18)
      .backgroundColor('#FFFDFB')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column({ space: 12 }) {
        Text('会话')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        if (this.snapshot.chatSessions.length === 0) {
          Text('还没有会话记录。')
            .fontSize(14)
            .fontColor('#6B5A4D');
        }
        ForEach(this.snapshot.chatSessions.slice(0, 3), (session: BridgeChatSession) => {
          Column({ space: 4 }) {
            Text(session.title)
              .fontSize(15)
              .fontWeight(FontWeight.Medium)
              .fontColor('#2C2520');
            Text(this.formatMoment(session.updatedAtEpochMs))
              .fontSize(12)
              .fontColor('#6B5A4D');
          }
          .width('100%')
          .padding(14)
          .backgroundColor(this.focusSessionId === session.id ? '#F1DDD0' : '#FFF7F2')
          .borderRadius(18)
          .onClick(() => this.refresh(session.id));
        }, (session: BridgeChatSession): string => session.id);
      }
      .padding(18)
      .backgroundColor('#FFFDFB')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column({ space: 12 }) {
        if (this.snapshot.conversation.length === 0) {
          Text('还没有对话记录。你可以直接发起一个问题。')
            .fontSize(14)
            .fontColor('#6B5A4D');
        }
        ForEach(this.snapshot.conversation.slice(Math.max(this.snapshot.conversation.length - 6, 0)), (message: BridgeChatMessage) => {
          Column({ space: 6 }) {
            Text(this.chatRoleLabel(message.role))
              .fontSize(12)
              .fontColor('#8B7766');
            Text(message.content)
              .fontSize(14)
              .fontColor(message.role === 'ASSISTANT' ? '#8C4A37' : '#2C2520');
          }
          .padding(14)
          .width('100%')
          .backgroundColor('#F4EDE4')
          .borderRadius(18)
          .alignItems(HorizontalAlign.Start);
        }, (message: BridgeChatMessage): string => message.id);

        TextArea({ text: this.chatPrompt, placeholder: '向 AI 说点什么' })
          .height(120)
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.chatPrompt = value; });
        Button('发送并写入本地对话')
          .backgroundColor('#8C4A37')
          .fontColor('#FFF7F2')
          .onClick(() => this.submitChat());
      }
      .padding(18)
      .backgroundColor('#FFFDFB')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);
    }
  }

  @Builder
  private personalSection(): void {
    Column({ space: 16 }) {
      Column({ space: 12 }) {
        Text('本地优先 AI')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        Text('客户端只使用本地规则、本地轻模型、本地生成模型和可选云增强，不再直接保存或使用外部模型 API key。')
          .fontSize(14)
          .fontColor('#55726E');

        Text('AI 模式')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#4C4139');
        Scroll() {
          Row({ space: 8 }) {
            ForEach(AI_MODE_OPTIONS, (option: OptionItem) => {
              Button(option.label)
                .fontSize(12)
                .fontColor(this.aiModeDraft === option.value ? '#FFF7F2' : '#2C2520')
                .backgroundColor(this.aiModeDraft === option.value ? '#8C4A37' : '#E8D8C7')
                .onClick(() => { this.aiModeDraft = option.value; });
            }, (option: OptionItem): string => option.value);
          }
        }
        .scrollBar(BarState.Off)
        .width('100%');

        Text(this.aiModeDescription())
          .fontSize(12)
          .fontColor('#6B5A4D');

        TextInput({ text: this.cloudEnhancementBaseUrlDraft, placeholder: '云增强服务端点' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.cloudEnhancementBaseUrlDraft = value; });
        TextInput({ text: this.lightweightModelPackageDraft, placeholder: '本地轻模型包 ID' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.lightweightModelPackageDraft = value; });
        TextInput({ text: this.generativeModelPackageDraft, placeholder: '本地生成模型包 ID' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.generativeModelPackageDraft = value; });

        Text('模型下载策略')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#4C4139');
        Scroll() {
          Row({ space: 8 }) {
            ForEach(DOWNLOAD_POLICY_OPTIONS, (option: OptionItem) => {
              Button(option.label)
                .fontSize(12)
                .fontColor(this.modelDownloadPolicyDraft === option.value ? '#FFF7F2' : '#2C2520')
                .backgroundColor(this.modelDownloadPolicyDraft === option.value ? '#55726E' : '#D7ECE7')
                .onClick(() => { this.modelDownloadPolicyDraft = option.value; });
            }, (option: OptionItem): string => option.value);
          }
        }
        .scrollBar(BarState.Off)
        .width('100%');

        Button('保存 AI 策略')
          .width('100%')
          .backgroundColor('#55726E')
          .fontColor('#FFF7F2')
          .onClick(() => this.savePreferences());

        Text(`当前模式：${this.snapshot.preferences?.aiModeLabel ?? '仅本地'}`)
          .fontSize(12)
          .fontColor('#55726E');
        Text(`当前云增强端点：${this.currentCloudEndpointLabel()}`)
          .fontSize(12)
          .fontColor('#55726E');
        Text(`轻模型：${this.snapshot.preferences?.localLightweightModelPackageId ?? 'mindweave-lite-core'}`)
          .fontSize(12)
          .fontColor('#55726E');
        Text(`生成模型：${this.snapshot.preferences?.localGenerativeModelPackageId ?? 'mindweave-gen-core'}`)
          .fontSize(12)
          .fontColor('#55726E');
        Text(`下载策略：${this.snapshot.preferences?.modelDownloadPolicyLabel ?? '预置模型'}`)
          .fontSize(12)
          .fontColor('#55726E');
      }
      .padding(18)
      .backgroundColor('#F6FFFD')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column({ space: 12 }) {
        Text('账户密码')
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor('#2C2520');
        Text('默认凭据只允许首次初始化使用，后续不能再改回 MindWeave / MindWeave。')
          .fontSize(14)
          .fontColor('#55726E');

        Text(`当前账号：${this.snapshot.account?.username ?? '未初始化'}`)
          .fontSize(13)
          .fontColor('#4C4139');
        Text(`凭据状态：${this.snapshot.account?.mustChangeCredentials === true ? '待修改' : '已启用'}`)
          .fontSize(13)
          .fontColor('#4C4139');
        Text(`账号创建时间：${this.formatMomentOrPlaceholder(this.snapshot.account?.createdAtEpochMs, '未知')}`)
          .fontSize(13)
          .fontColor('#4C4139');
        Text(`凭据最近更新时间：${this.formatMomentOrPlaceholder(this.snapshot.account?.credentialsUpdatedAtEpochMs, '未知')}`)
          .fontSize(13)
          .fontColor('#4C4139');
        Text(`最近登录时间：${this.formatMomentOrPlaceholder(this.snapshot.account?.lastLoginAtEpochMs, '尚未记录')}`)
          .fontSize(13)
          .fontColor('#4C4139');

        TextInput({ text: this.profileUsername, placeholder: '新账号' })
          .backgroundColor('#FFFFFF')
          .onChange((value: string) => { this.profileUsername = value; });
        TextInput({ text: this.profileCurrentPassword, placeholder: '当前密码' })
          .backgroundColor('#FFFFFF')
          .type(InputType.Password)
          .onChange((value: string) => { this.profileCurrentPassword = value; });
        TextInput({ text: this.profileNewPassword, placeholder: '新密码' })
          .backgroundColor('#FFFFFF')
          .type(InputType.Password)
          .onChange((value: string) => { this.profileNewPassword = value; });
        TextInput({ text: this.profileConfirmPassword, placeholder: '确认新密码' })
          .backgroundColor('#FFFFFF')
          .type(InputType.Password)
          .onChange((value: string) => { this.profileConfirmPassword = value; });

        Button('更新账户信息')
          .width('100%')
          .backgroundColor('#8C4A37')
          .fontColor('#FFF7F2')
          .onClick(() => this.changeCredentials());
        Button('退出登录')
          .width('100%')
          .backgroundColor('#D7ECE7')
          .fontColor('#2C2520')
          .onClick(() => this.signOut());
      }
      .padding(18)
      .backgroundColor('#FFF7F2')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);

      Column() {
        Text(this.personalStatusLine)
          .fontSize(14)
          .fontColor('#8C4A37');
      }
      .width('100%')
      .padding(18)
      .backgroundColor('#FFFDFB')
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start);
    }
  }

  @Builder
  private appShell(): void {
    Column({ space: 0 }) {
      Scroll() {
        Column({ space: 18 }) {
          this.heroPanel()
          this.sectionHeader()

          if (this.selectedSectionId === 'DIARY') {
            this.diarySection()
          } else if (this.selectedSectionId === 'SCHEDULE') {
            this.scheduleSection()
          } else if (this.selectedSectionId === 'ASSISTANT') {
            this.assistantSection()
          } else {
            this.personalSection()
          }
        }
        .width('100%')
        .padding(18)
        .alignItems(HorizontalAlign.Start);
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .backgroundColor('#F4EDE4');

      this.compactNavigationBar()
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F4EDE4');
  }

  build() {
    if (!this.snapshot.account || !this.snapshot.preferences) {
      this.loadingPanel()
    } else if (!this.isAuthenticated) {
      this.loginPanel()
    } else if (this.requiresCredentialReset) {
      this.credentialResetPanel()
    } else {
      this.appShell()
    }
  }
}
