import promptAction from '@ohos.promptAction';
import {
  BridgeChatSession,
  BridgeResponse,
  BridgeSnapshot,
  BridgeTimelineItem,
  emptySnapshot,
  mindWeaveBridge
} from '../bridge/MindWeaveBridge';

@Entry
@Component
struct Index {
  @State private statusMessage: string = '准备初始化 MindWeave Harmony bridge...';
  @State private snapshot: BridgeSnapshot = emptySnapshot();
  @State private focusSessionId: string = '';
  @State private diaryTitle: string = '';
  @State private diaryContent: string = '';
  @State private scheduleTitle: string = '';
  @State private scheduleDescription: string = '';
  @State private chatPrompt: string = '';

  aboutToAppear(): void {
    this.applyResponse(mindWeaveBridge.bootstrap());
  }

  private applyResponse(response: BridgeResponse): void {
    this.statusMessage = response.message;
    if (response.snapshot) {
      this.snapshot = response.snapshot;
    }
    this.focusSessionId = response.focusSessionId ?? this.focusSessionId;
  }

  private refresh(selectedSessionId?: string): void {
    this.applyResponse(mindWeaveBridge.getSnapshot(selectedSessionId));
  }

  private submitDiary(): void {
    if (this.diaryContent.trim().length === 0) {
      promptAction.showToast({ message: '请先输入日记正文。' });
      return;
    }
    this.applyResponse(mindWeaveBridge.captureDiary(this.diaryTitle, this.diaryContent));
    this.diaryTitle = '';
    this.diaryContent = '';
  }

  private submitSchedule(): void {
    if (this.scheduleTitle.trim().length === 0) {
      promptAction.showToast({ message: '请先输入日程标题。' });
      return;
    }
    const startTime = Date.now() + 2 * 60 * 60 * 1000;
    const endTime = startTime + 60 * 60 * 1000;
    this.applyResponse(
      mindWeaveBridge.captureSchedule(
        this.scheduleTitle,
        this.scheduleDescription,
        startTime,
        endTime
      )
    );
    this.scheduleTitle = '';
    this.scheduleDescription = '';
  }

  private submitChat(): void {
    if (this.chatPrompt.trim().length === 0) {
      promptAction.showToast({ message: '请先输入聊天内容。' });
      return;
    }
    this.applyResponse(mindWeaveBridge.sendChatMessage(this.focusSessionId, this.chatPrompt));
    this.chatPrompt = '';
  }

  @Builder
  private sectionTitle(title: string): void {
    Text(title)
      .fontSize(20)
      .fontWeight(FontWeight.Bold)
      .fontColor('#2C2520');
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        Column({ space: 8 }) {
          Text('MindWeave / HarmonyOS')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor('#2C2520');
          Text(`平台：${this.snapshot.platformName} · 设备：${this.snapshot.session.deviceName}`)
            .fontSize(14)
            .fontColor('#6B5A4D');
          Text(this.statusMessage)
            .fontSize(14)
            .fontColor('#8C4A37');
        }
        .alignItems(HorizontalAlign.Start);

        Row({ space: 12 }) {
          Button('刷新')
            .backgroundColor('#8C4A37')
            .onClick(() => this.refresh(this.focusSessionId));
          Button('同步')
            .backgroundColor('#55726E')
            .onClick(() => this.applyResponse(mindWeaveBridge.runSync()));
        }

        Column({ space: 10 }) {
          this.sectionTitle('共享状态')
          Text(`账号：${this.snapshot.account?.username ?? 'MindWeave'}`)
          Text(`AI 模式：${this.snapshot.preferences?.aiModeLabel ?? '仅本地'}`)
          Text(`待同步：${this.snapshot.syncState.pendingChanges} · Cursor：${this.snapshot.syncState.lastSyncSeq}`)
        }
        .padding(16)
        .backgroundColor('#FFF7F2')
        .borderRadius(18)
        .alignItems(HorizontalAlign.Start);

        Column({ space: 10 }) {
          this.sectionTitle('写日记')
          TextInput({ text: this.diaryTitle, placeholder: '标题（可选）' })
            .onChange((value: string) => { this.diaryTitle = value; });
          TextArea({ text: this.diaryContent, placeholder: '记录今天的状态、情绪或想法' })
            .height(120)
            .onChange((value: string) => { this.diaryContent = value; });
          Button('保存日记')
            .backgroundColor('#8C4A37')
            .onClick(() => this.submitDiary());
        }
        .padding(16)
        .backgroundColor('#FFF7F2')
        .borderRadius(18);

        Column({ space: 10 }) {
          this.sectionTitle('写日程')
          TextInput({ text: this.scheduleTitle, placeholder: '日程标题' })
            .onChange((value: string) => { this.scheduleTitle = value; });
          TextInput({ text: this.scheduleDescription, placeholder: '描述' })
            .onChange((value: string) => { this.scheduleDescription = value; });
          Button('保存日程')
            .backgroundColor('#55726E')
            .onClick(() => this.submitSchedule());
        }
        .padding(16)
        .backgroundColor('#F6FFFD')
        .borderRadius(18);

        Column({ space: 10 }) {
          this.sectionTitle('聊天')
          Scroll({ scrollBar: BarState.Off }) {
            Row({ space: 8 }) {
              ForEach(this.snapshot.chatSessions, (session: BridgeChatSession) => {
                Button(session.title)
                  .fontSize(12)
                  .backgroundColor(this.focusSessionId === session.id ? '#D59E6B' : '#E8D8C7')
                  .fontColor('#2C2520')
                  .onClick(() => this.refresh(session.id));
              }, (session: BridgeChatSession) => session.id);
            }
          }
          TextArea({ text: this.chatPrompt, placeholder: '让 AI 帮你梳理当下的重点' })
            .height(100)
            .onChange((value: string) => { this.chatPrompt = value; });
          Button('发送')
            .backgroundColor('#8C4A37')
            .onClick(() => this.submitChat());
          ForEach(this.snapshot.conversation, (message) => {
            Column({ space: 4 }) {
              Text(message.role)
                .fontSize(12)
                .fontColor('#6B5A4D');
              Text(message.content)
                .fontSize(14)
                .fontColor('#2C2520');
            }
            .padding(12)
            .width('100%')
            .backgroundColor(message.role === 'ASSISTANT' ? '#FFF7F2' : '#F4EDE4')
            .borderRadius(14)
            .alignItems(HorizontalAlign.Start);
          }, (message) => message.id);
        }
        .padding(16)
        .backgroundColor('#FFFDFB')
        .borderRadius(18)
        .alignItems(HorizontalAlign.Start);

        Column({ space: 10 }) {
          this.sectionTitle('最近日记')
          ForEach(this.snapshot.timeline, (item: BridgeTimelineItem) => {
            Column({ space: 6 }) {
              Text(item.entry.title.length > 0 ? item.entry.title : '未命名日记')
                .fontSize(16)
                .fontWeight(FontWeight.Medium)
                .fontColor('#2C2520');
              Text(item.entry.content)
                .fontSize(14)
                .fontColor('#4C4139');
              if (item.entry.aiSummary) {
                Text(`AI：${item.entry.aiSummary}`)
                  .fontSize(13)
                  .fontColor('#8C4A37');
              }
            }
            .padding(14)
            .width('100%')
            .backgroundColor('#FFF7F2')
            .borderRadius(16)
            .alignItems(HorizontalAlign.Start);
          }, (item: BridgeTimelineItem) => item.entry.id);
        }
        .alignItems(HorizontalAlign.Start);
      }
      .width('100%')
      .padding(20)
      .alignItems(HorizontalAlign.Start);
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F4EDE4');
  }
}
