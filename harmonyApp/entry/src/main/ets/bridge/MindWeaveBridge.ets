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
  createdAtEpochMs: number;
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
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
}

export interface BridgeChatSession {
  id: string;
  title: string;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
}

export interface BridgeChatMessage {
  id: string;
  sessionId: string;
  role: string;
  content: string;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
}

export interface BridgeAccountState {
  username: string;
  mustChangeCredentials: boolean;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  credentialsUpdatedAtEpochMs: number;
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
  authenticate(request: string): string;
  forceResetCredentials(request: string): string;
  changeCredentials(request: string): string;
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
      deviceName: 'Harmony Phone'
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

  captureDiaryWithDetails(title: string, content: string, mood: string, tags: string[]): BridgeResponse {
    return this.decode(nativeModule.captureDiary(JSON.stringify({
      title,
      content,
      mood,
      tags
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

  captureScheduleWithType(
    title: string,
    description: string,
    startTimeEpochMs: number,
    endTimeEpochMs: number,
    type: string
  ): BridgeResponse {
    return this.decode(nativeModule.captureSchedule(JSON.stringify({
      title,
      description,
      startTimeEpochMs,
      endTimeEpochMs,
      remindAtEpochMs: startTimeEpochMs - 15 * 60 * 1000,
      type
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

  savePreferences(
    aiMode: string,
    cloudEnhancementBaseUrl: string,
    localLightweightModelPackageId: string,
    localGenerativeModelPackageId: string,
    modelDownloadPolicy: string
  ): BridgeResponse {
    return this.decode(nativeModule.savePreferences(JSON.stringify({
      aiMode,
      cloudEnhancementBaseUrl,
      localLightweightModelPackageId,
      localGenerativeModelPackageId,
      modelDownloadPolicy
    })));
  }

  authenticate(username: string, password: string): BridgeResponse {
    return this.decode(nativeModule.authenticate(JSON.stringify({
      username,
      password
    })));
  }

  forceResetCredentials(newUsername: string, newPassword: string): BridgeResponse {
    return this.decode(nativeModule.forceResetCredentials(JSON.stringify({
      newUsername,
      newPassword
    })));
  }

  changeCredentials(currentPassword: string, newUsername: string, newPassword: string): BridgeResponse {
    return this.decode(nativeModule.changeCredentials(JSON.stringify({
      currentPassword,
      newUsername,
      newPassword
    })));
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
