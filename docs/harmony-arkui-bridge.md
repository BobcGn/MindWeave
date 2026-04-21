# Harmony ArkUI Bridge

## 目标

参考 `docs/Ruler.md` 与 `KuiklyUI跨平台实践分析.md`，鸿蒙侧采用：

- `shared` 继续承载业务、数据库、同步、AI 抽象
- `harmonyApp` 仅承载 ArkUI 页面、NAPI 模块、C++ 薄桥接
- `settings.2.0.ohos.gradle.kts` 隔离 Harmony 专用 Kotlin/Native profile

## 结构

```text
shared/
  src/commonMain/.../harmony/bridge/MindWeaveHarmonyBridge.kt
  src/ohosArm64Main/.../MindWeaveHarmonyExports.kt
  build.2.0.ohos.gradle.kts

harmonyApp/
  entry/src/main/ets/bridge/MindWeaveBridge.ets
  entry/src/main/ets/pages/Index.ets
  entry/src/main/cpp/napi_init.cpp
  entry/src/main/cpp/thirdparty/mindweave/mindweave_bridge.h
```

## 调用链

```text
ArkUI Index.ets
  -> MindWeaveBridge.ets
    -> libmindweave_bridge.so (NAPI / C++)
      -> libmindweave.so (Kotlin/Native shared)
        -> shared repositories / SQLDelight / AI abstraction
```

桥接层只做三件事：

1. ArkTS 字符串请求转发给 NAPI
2. C++ 调 Kotlin/Native 导出的 `C ABI`
3. Kotlin/Native 返回稳定 JSON 快照给 ArkUI

## 导出接口

Kotlin/Native 当前导出：

- `mindweave_bridge_bootstrap`
- `mindweave_bridge_get_snapshot`
- `mindweave_bridge_capture_diary`
- `mindweave_bridge_capture_schedule`
- `mindweave_bridge_send_chat_message`
- `mindweave_bridge_run_sync`
- `mindweave_bridge_save_preferences`

这些接口都返回统一 JSON：

```json
{
  "ok": true,
  "message": "鸿蒙端日记已写入本地 SQLite。",
  "focusSessionId": "chat-session-id",
  "snapshot": {}
}
```

## 构建

1. Harmony 链路现在只接受真实 Kotlin/Native bridge。先发布 `libmindweave.so`：

```bash
./2.0_ohos_mindweave_build.sh Debug kba
```

2. 也可以直接走 Gradle：

```bash
./gradlew -c settings.2.0.ohos.gradle.kts -Pmindweave.ohos.toolchain=kba publishDebugBinariesToHarmonyApp
```

3. 当真实 OHOS target 可用时，任务会把 `libmindweave.so` 同步到：

```text
harmonyApp/entry/src/main/libs/arm64-v8a/libmindweave.so
```

4. Gradle 还会写入：

```text
harmonyApp/entry/src/main/libs/arm64-v8a/mindweave_bridge_mode.txt
```

`CMakeLists.txt` 会校验这个标记必须为 `kotlin`，否则直接失败，不再静默回落到 demo。

5. 然后在 DevEco Studio 打开 `harmonyApp`，构建 `entry` 模块。

6. 需要排查当前 profile 状态时，执行：

```bash
./gradlew -c settings.2.0.ohos.gradle.kts harmonyBuildDoctor
```

## 当前边界

- Harmony profile 独立于默认 `settings.gradle.kts`，不会影响 Android / iOS 主链路。
- 默认 `standard` toolchain 如果不具备 `ohosArm64`，发布任务会直接失败，不再回落到 demo bridge。
- `kba` toolchain 通过 `-Pmindweave.ohos.toolchain=kba` 或脚本第二参数启用，依赖 Tencent KBA 仓库或本地 Maven。
- 即使 `kba` target 可见，仍然需要仓库里存在带 `ohos_arm64` 变体的 SQLDelight runtime/native-driver；否则 `publishDebugBinariesToHarmonyApp` 会在编译期失败。
- Harmony 侧 AI 默认保持本地优先；云增强 HTTP engine 还没有接入真实 OHOS 网络实现。
- ArkUI 默认只初始化真实本地账户、SQLite 与 AI 偏好，不再自动灌入演示数据。
