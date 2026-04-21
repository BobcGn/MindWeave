import promptAction from '@ohos.promptAction';
import nativeMindWeaveBridge from 'libmindweave_bridge.so';

export interface BridgeSession {
  userId: string;
  deviceId: string;
  deviceName: string;
}

export interface BridgeSyncState {
  pendingChanges: number;
  lastSyncSeq: number;
}

export interface BridgeDiaryEntry {
  id: string;
  title: string;
  content: string;
  mood: string;
  aiSummary?: string;
  updatedAtEpochMs: number;
}

export interface BridgeTimelineItem {
  entry: BridgeDiaryEntry;
  tags: string[];
}

export interface BridgeScheduleEvent {
  id: string;
  title: string;
  description: string;
  startTimeEpochMs: number;
  endTimeEpochMs: number;
  type: string;
}

export interface BridgeChatSession {
  id: string;
  title: string;
  updatedAtEpochMs: number;
}

export interface BridgeChatMessage {
  id: string;
  sessionId: string;
  role: string;
  content: string;
  updatedAtEpochMs: number;
}

export interface BridgeAccountState {
  username: string;
  mustChangeCredentials: boolean;
  lastLoginAtEpochMs?: number;
}

export interface BridgePreferenceState {
  aiMode: string;
  aiModeLabel: string;
  cloudEnhancementBaseUrl: string;
  localLightweightModelPackageId: string;
  localGenerativeModelPackageId: string;
  modelDownloadPolicy: string;
  modelDownloadPolicyLabel: string;
}

export interface BridgeSnapshot {
  platformName: string;
  session: BridgeSession;
  account?: BridgeAccountState;
  preferences?: BridgePreferenceState;
  timeline: BridgeTimelineItem[];
  schedules: BridgeScheduleEvent[];
  chatSessions: BridgeChatSession[];
  conversation: BridgeChatMessage[];
  syncState: BridgeSyncState;
}

export interface BridgeResponse {
  ok: boolean;
  message: string;
  focusSessionId?: string;
  snapshot?: BridgeSnapshot;
}

interface NativeBridgeModule {
  bootstrap(request: string): string;
  getSnapshot(request: string): string;
  captureDiary(request: string): string;
  captureSchedule(request: string): string;
  sendChatMessage(request: string): string;
  runSync(): string;
  savePreferences(request: string): string;
}

const nativeModule = nativeMindWeaveBridge as NativeBridgeModule;

export function emptySnapshot(): BridgeSnapshot {
  return {
    platformName: 'HarmonyOS',
    session: {
      userId: 'local-user',
      deviceId: 'harmony-device',
      deviceName: 'MindWeave Harmony Device'
    },
    timeline: [],
    schedules: [],
    chatSessions: [],
    conversation: [],
    syncState: {
      pendingChanges: 0,
      lastSyncSeq: 0
    }
  };
}

export class MindWeaveBridge {
  bootstrap(): BridgeResponse {
    return this.decode(nativeModule.bootstrap(JSON.stringify({
      databaseName: 'mindweave-harmony.db',
      deviceId: 'harmony-phone',
      deviceName: 'Harmony Phone',
      enableDemoData: true
    })));
  }

  getSnapshot(selectedSessionId?: string): BridgeResponse {
    return this.decode(nativeModule.getSnapshot(JSON.stringify({
      selectedSessionId: selectedSessionId ?? null
    })));
  }

  captureDiary(title: string, content: string): BridgeResponse {
    return this.decode(nativeModule.captureDiary(JSON.stringify({
      title,
      content,
      mood: 'CALM',
      tags: ['HarmonyOS', 'ArkUI']
    })));
  }

  captureSchedule(title: string, description: string, startTimeEpochMs: number, endTimeEpochMs: number): BridgeResponse {
    return this.decode(nativeModule.captureSchedule(JSON.stringify({
      title,
      description,
      startTimeEpochMs,
      endTimeEpochMs,
      remindAtEpochMs: startTimeEpochMs - 15 * 60 * 1000,
      type: 'WORK'
    })));
  }

  sendChatMessage(existingSessionId: string | undefined, prompt: string): BridgeResponse {
    return this.decode(nativeModule.sendChatMessage(JSON.stringify({
      existingSessionId: existingSessionId ?? null,
      prompt
    })));
  }

  runSync(): BridgeResponse {
    return this.decode(nativeModule.runSync());
  }

  private decode(raw: string): BridgeResponse {
    const response = JSON.parse(raw) as BridgeResponse;
    if (!response.ok) {
      promptAction.showToast({
        message: response.message
      });
    }
    return response;
  }
}

export const mindWeaveBridge = new MindWeaveBridge();
