import UIAbility from '@ohos.app.ability.UIAbility';
import window from '@ohos.window';
import { bridgeRuntimeConfig } from '../bridge/BridgeRuntimeConfig';

export default class EntryAbility extends UIAbility {
  onWindowStageCreate(windowStage: window.WindowStage): void {
    bridgeRuntimeConfig.databaseName = `${this.context.filesDir}/mindweave-harmony.db`;
    windowStage.loadContent('pages/Index', (error) => {
      if (error.code !== 0) {
        console.error(`Failed to load page content: ${JSON.stringify(error)}`);
      }
    });
  }
}
