declare const nativeMindWeaveBridge: {
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
};

export default nativeMindWeaveBridge;
