declare function require(moduleName: string): {
  appTasks: unknown;
};

const { appTasks } = require('@ohos/hvigor-ohos-plugin');

export default {
  system: appTasks,
  plugins: []
};
