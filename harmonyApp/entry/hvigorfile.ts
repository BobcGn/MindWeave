declare function require(moduleName: string): {
  hapTasks: unknown;
};

const { hapTasks } = require('@ohos/hvigor-ohos-plugin');

export default {
  system: hapTasks,
  plugins: []
};
