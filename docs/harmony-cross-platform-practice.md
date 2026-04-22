# 鸿蒙跨平台桥接实践手册

## 文档目的

本文基于本仓库鸿蒙桥接相关改动的完整演进，抽象出一套可复用的方法论，供未来跨平台项目落地 HarmonyOS 端时参考。重点不是复述某个项目的页面逻辑，而是沉淀哪些边界应该稳定、哪些能力应该替换、哪些构建约束必须保留。

适用场景：

- 共享业务层来自 KMP、Rust、C++ Core 或其他跨平台内核
- 鸿蒙侧 UI 使用 ArkUI/ArkTS
- 中间需要经过 NAPI、C ABI、Kotlin/Native 或其他 Native 导出层
- 平台能力暂不完整，无法直接复用 Android/iOS 的数据库、序列化、网络栈

## 改动复盘后的核心结论

1. 鸿蒙桥接最稳定的边界不是对象映射，而是字符串协议。`ArkTS -> NAPI -> Native Core` 之间统一用 JSON 请求和 JSON 响应，能显著降低 ABI、类型系统和工具链差异带来的摩擦。
2. 共享层应继续承载业务规则，鸿蒙工程只承载 UI、能力注入和最薄的一层转发。桥接层不应该重新实现业务。
3. 桥接返回值应尽量收敛为统一响应结构，而不是每个接口各自定义一套碎片化返回。统一的 `ok/message/snapshot/focusId` 对调试、灰度和前端状态同步都更友好。
4. “能编译的占位壳层”和“能工作的真实桥接”必须被明确区分。壳层可以帮助 ArkUI/HAP 构建，但不能继续伪装成真实业务成功路径。
5. 当鸿蒙侧缺少稳定的 SQLite、序列化或 HTTP 能力时，应优先替换基础设施实现，而不是污染业务层接口。
6. 鸿蒙适配应有独立 profile、独立发布任务、独立诊断命令，避免影响 Android/iOS 主链路。

## 本仓库中实际发生的改动，抽象成通用做法

| 改动层 | 当前实现落点 | 通用做法 |
| --- | --- | --- |
| 共享桥接控制器 | [MindWeaveHarmonyBridge.kt](../shared/src/commonMain/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyBridge.kt) | 在共享层定义桥接 DTO、命令语义和统一快照，避免 ArkTS 直接触达 Repository 或数据库细节。 |
| OHOS Native 导出 | [MindWeaveHarmonyExports.kt](../shared/src/ohosArm64Main/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyExports.kt) | 用 C ABI 导出稳定符号；导出层只做解析、调用控制器、编码响应。 |
| ArkTS 包装层 | [MindWeaveBridge.ets](../harmonyApp/entry/src/main/ets/bridge/MindWeaveBridge.ets) | ArkTS 只负责入参整理、调用 native module、解析统一响应、承接提示信息。 |
| NAPI/C++ 薄桥接 | [napi_init.cpp](../harmonyApp/entry/src/main/cpp/napi_init.cpp) | C++ 层只负责字符串透传、native module 注册、真实桥接缺失时返回显式错误。 |
| Native 链接守卫 | [CMakeLists.txt](../harmonyApp/entry/src/main/cpp/CMakeLists.txt) | 用构建条件区分真实桥接和壳层构建，禁止隐式回退。 |
| OHOS 独立构建 profile | [settings.2.0.ohos.gradle.kts](../settings.2.0.ohos.gradle.kts)、[shared/build.2.0.ohos.gradle.kts](../shared/build.2.0.ohos.gradle.kts) | 为鸿蒙单独维护 toolchain、依赖仓库和发布任务，降低对主工程的侵入。 |
| 发布脚本与诊断 | [2.0_ohos_mindweave_build.sh](../2.0_ohos_mindweave_build.sh) | 固化发布入口、写入桥接模式标记，并提供构建诊断命令。 |
| 存储层替换 | [OhosLocalRepositories.kt](../shared/src/ohosArm64Main/kotlin/org/example/mindweave/data/local/OhosLocalRepositories.kt) | 平台数据库能力不稳定时，在 Repository 边界后替换为文件或 KV 实现，保持上层接口不变。 |
| 序列化/同步替换 | [OhosJson.kt](../shared/src/ohosProfileCommonMain/kotlin/org/example/mindweave/util/OhosJson.kt)、[LocalChangeApplierOhos.kt](../shared/src/ohosProfileCommonMain/kotlin/org/example/mindweave/sync/LocalChangeApplierOhos.kt) | 在 profile 内补齐最小可用 JSON、同步应用器和注解兼容层，避免主链路被平台特例污染。 |
| 桥接回归测试 | [MindWeaveHarmonyBridgeTest.kt](../shared/src/jvmTest/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyBridgeTest.kt) | 优先验证“bootstrap -> 写入 -> 读取 -> 凭据持久化”这类跨层闭环，而不是只测单点函数。 |

## 演进过程里最值得保留的经验

### 1. 先打通四层链路，再谈页面复杂度

本仓库最终稳定下来的调用链如下：

```text
ArkUI / ArkTS
  -> NAPI module (C++)
    -> Kotlin/Native C ABI
      -> shared graph / facade / repository
```

这个顺序很重要。鸿蒙接入初期最容易犯的错误是先堆 UI，再补底层桥接，导致问题被混在页面交互里。更可靠的方式是先保证：

- ArkTS 能调用到 native module
- native module 能调用到共享层
- 共享层能返回最小可用快照
- UI 只消费快照，不反向塑造桥接协议

### 2. 曾经存在 demo fallback，但最终必须收口到“显式失败”

仓库演进中一度引入过 demo bridge fallback，用来在本机没有 OHOS Kotlin target 时维持页面联调和 HAP 构建。这类能力在早期摸底阶段有价值，但长期保留会带来两个问题：

- 前端容易把“假成功”当成“真实业务可用”
- 构建链路会掩盖真实库未发布、符号未接管、Native 未链接等问题

因此当前实现已经把策略收敛成：

- 允许构建一个不链接真实业务库的 NAPI shell
- 但 shell 运行时只能返回“真实桥接未就绪”的明确错误
- 发布任务和 profile 明确要求真实 `libmindweave.so`

这条经验很通用：可以保留壳层构建能力，但不要保留业务成功假象。

### 3. SQLite 不稳时，不要把业务层一起推倒重来

这次鸿蒙适配里，真正难的不是桥接符号导出，而是平台基础设施不完整。当前 OHOS profile 没有直接沿用 Android/iOS 的 SQLDelight 路线，而是在 `createPlatformLocalRepositories` 后切到了 OHOS 专属本地仓储实现。

通用做法是：

- 保留 `DiaryRepository`、`ChatRepository`、`SyncRepository` 这类领域接口
- 在平台层切换存储实现，而不是改业务 Facade
- 同步、账号、偏好、模型包等都跟随同一套仓储边界收口

这样做的收益是：后续无论回到 SQLite、切到关系型数据库、还是临时落文件，都不需要改动桥接协议和 UI 调用方式。

## 推荐的桥接分层模型

### 共享层

建议放在跨平台共享模块中，职责包括：

- 定义桥接请求 DTO 和响应 DTO
- 持有应用 graph/facade，调用真实业务用例
- 聚合页面所需快照，而不是把 repository 明细暴露给鸿蒙侧
- 统一错误描述和默认值策略

本仓库对应实现见 [MindWeaveHarmonyBridge.kt](../shared/src/commonMain/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyBridge.kt)。

### Native 导出层

建议只做四件事：

- 接收 `char*` 或等价字符串入参
- 解析为共享层 DTO
- 调用桥接控制器
- 把结果重新编码成字符串并负责释放内存

本仓库对应实现见 [MindWeaveHarmonyExports.kt](../shared/src/ohosArm64Main/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyExports.kt)。

### C++/NAPI 层

建议只做 Node-API 注册和字符串透传，不再承接业务逻辑。职责越轻，后续替换 Native Core 越容易。

本仓库对应实现见 [napi_init.cpp](../harmonyApp/entry/src/main/cpp/napi_init.cpp)。

### ArkTS 层

建议封装成一个稳定的 `BridgeService` 或 `MindWeaveBridge`：

- 所有调用都先进入同一个包装类
- 所有 native 返回都在这里统一解析
- 所有错误 toast、默认值、防御式处理都在这里收口

本仓库对应实现见 [MindWeaveBridge.ets](../harmonyApp/entry/src/main/ets/bridge/MindWeaveBridge.ets)。

## 协议设计建议

### 1. 入参使用命令对象，不要散落多个位置参数

例如：

```json
{
  "title": "检查 Harmony 首页",
  "description": "确认桥接链路可用",
  "startTimeEpochMs": 1710000000000,
  "endTimeEpochMs": 1710003600000
}
```

比起 `captureSchedule(title, description, start, end)` 这种多位置参数，命令对象更适合做版本扩展、日志记录和容错。

### 2. 响应结构尽量统一

建议至少统一为：

```json
{
  "ok": true,
  "message": "操作成功",
  "focusSessionId": "optional",
  "snapshot": {}
}
```

这样 ArkTS 侧可以只维护一套解码和状态同步逻辑。

### 3. 快照优先于增量拼装

本仓库的桥接返回会尽量带回当前页面所需完整快照，而不是只返回“新增成功”。这样做的优点是：

- 前端不需要自己拼局部状态
- 桥接层更容易做兼容
- 调试时可以直接看到端上真实状态

如果后期性能成为瓶颈，再引入增量响应；不要在首版就把协议切得过细。

### 4. 对外文案不要绑定具体基础设施品牌

这是这次适配里一个很典型的经验。桥接消息如果写死为“SQLite 已就绪”，当底层临时换成文件存储或 KV 存储时，协议文案就会失真。更稳妥的说法是：

- “本地持久化已就绪”
- “本地数据已写入”
- “本地账户配置已初始化”

协议应该描述能力，不应该描述某一版底层实现。

## 构建与发布链路建议

### 1. 保持鸿蒙 profile 独立

本仓库用独立的 settings/build 文件管理鸿蒙 profile：

- [settings.2.0.ohos.gradle.kts](../settings.2.0.ohos.gradle.kts)
- [build.2.0.ohos.gradle.kts](../build.2.0.ohos.gradle.kts)
- [shared/build.2.0.ohos.gradle.kts](../shared/build.2.0.ohos.gradle.kts)

通用原则是：鸿蒙链路的仓库、插件版本、toolchain 选择都不要直接污染主工程默认配置。

### 2. 发布真实 Native 库时写入模式标记

当前实现会把真实产物同步到：

```text
harmonyApp/entry/src/main/libs/arm64-v8a/libmindweave.so
```

同时写入：

```text
harmonyApp/entry/src/main/libs/arm64-v8a/mindweave_bridge_mode.txt
```

模式标记的价值在于让 CMake 和运行时都能知道当前包里接入的是：

- `kotlin` 真实桥接
- 仅能构建但不能工作的壳层

### 3. CMake 要对真实桥接建立硬约束

当前 CMake 的收敛策略值得复用：

- 真实库存在且模式为 `kotlin` 时，链接真实 `libmindweave.so`
- 否则只构建 NAPI shell，并输出明确警告

这能把“构建问题”和“运行期协议问题”分层暴露出来。

### 4. 给鸿蒙链路单独提供 doctor 命令

当工具链和仓库组合复杂时，文档往往不如诊断命令可靠。建议至少输出：

- 当前 toolchain
- 是否识别到 OHOS Kotlin target
- 已发布 so 的路径与存在性
- 当前 bridge mode

本仓库对应任务为 `harmonyBuildDoctor`。

## 平台能力不齐时的处理顺序

### 存储层

优先保领域接口不变，再替换底层实现。不要因为鸿蒙缺 SQLite runtime，就把 UI 直接绑到文件读写。

### 序列化层

如果完整 `kotlinx.serialization` 路线在某个 profile 上不稳定，可以像当前仓库一样：

- 在 profile 目录下补最小兼容注解
- 提供手写 JSON encode/decode
- 只在鸿蒙 profile 使用，不扩散到全平台

### 网络层

如果云增强、远端同步暂时没有可用 OHOS HTTP engine，不要做半通不通的伪接入。当前实现的策略是：

- 保留接口
- 明确返回 not implemented
- 在 AI 设置上收敛到本地优先

这比“偶尔成功、偶尔静默失败”的状态更可控。

### 文件路径与运行上下文

鸿蒙本地数据库或文件存储路径，不应在桥接层写死。当前工程通过 `EntryAbility` 在运行时把 `filesDir` 注入给桥接配置：

- [BridgeRuntimeConfig.ets](../harmonyApp/entry/src/main/ets/bridge/BridgeRuntimeConfig.ets)
- [EntryAbility.ets](../harmonyApp/entry/src/main/ets/entryability/EntryAbility.ets)

这条经验同样通用：路径、沙箱、权限相关的上下文应由平台入口注入，而不是硬编码在共享层。

## 测试与诊断建议

建议至少覆盖以下闭环：

1. `bootstrap` 后能拿到真实空快照，而不是演示数据。
2. 写入日记、日程、聊天后，快照能读回新状态。
3. 账号初始化、首次改密、二次启动后的凭据持久化保持一致。
4. 真实桥接缺失时，ArkTS 能收到稳定错误，而不是崩溃或空字符串。
5. 发布任务未生成真实 so 时，构建日志和诊断命令能明确指出问题。

本仓库目前最有价值的测试，不是 UI 快照，而是桥接控制器层面的跨仓储闭环测试，见 [MindWeaveHarmonyBridgeTest.kt](../shared/src/jvmTest/kotlin/org/example/mindweave/harmony/bridge/MindWeaveHarmonyBridgeTest.kt)。

## 一套更稳妥的落地顺序

1. 先定义共享层桥接 DTO 和统一响应结构。
2. 再把 `bootstrap` 和 `snapshot` 两个最小命令打通。
3. 然后补写入类命令，如日记、日程、聊天。
4. 接着建立真实 Native 库发布链路和 CMake 链接守卫。
5. 再处理 SQLite、序列化、网络等平台能力差异。
6. 最后再扩展 ArkUI 页面、权限和复杂交互。

这个顺序的价值在于：每一步都能验证一条完整链路，不会把问题堆到最后一起爆发。

## 常见误区

- 把 ArkTS 页面状态当成业务状态源，导致 bridge 无法复用。
- 让 NAPI/C++ 重新实现业务逻辑，最终出现双份状态机。
- 为了“先跑起来”长期保留 demo 成功回包，掩盖真实桥接未接入。
- 因平台依赖不齐，直接改共享业务模型或 Repository 契约。
- 没有 bridge mode、doctor task、发布脚本，导致团队难以判断当前包到底链接了什么。
- 协议和提示文案绑定到 SQLite、某个 HTTP engine 或某个 SDK 品牌名，后续替换成本陡增。

## 实施检查清单

- 共享层是否独立定义了桥接 DTO、控制器和统一响应？
- ArkTS 是否只依赖一个桥接服务入口？
- C++/NAPI 是否足够薄，只做转发和注册？
- 是否存在“真实桥接”和“壳层构建”的显式区分？
- 发布真实 so 时是否会写入模式标记？
- 鸿蒙 profile 是否独立于主工程？
- 当数据库/序列化/网络能力缺失时，是否通过平台实现替换而不是修改业务接口？
- 是否具备 `bootstrap -> 写入 -> 读取 -> 重启后保持一致` 的自动化测试？

## 对未来跨平台鸿蒙实践的建议

如果未来再做一次类似接入，建议默认采用下面这套原则：

- 把鸿蒙看作“一个新的平台外壳”，而不是“共享层的特例分支”。
- 把桥接协议看作产品化接口，而不是临时调试代码。
- 把平台缺失能力封装在 profile 内，而不是扩散到业务核心。
- 把壳层构建能力保留给研发效率，把真实桥接能力保留给正式交付。

这样一来，即使底层技术栈从 Kotlin/Native 换成 Rust/C++ Core，或者 ArkTS 上层 UI 再重做一版，整体架构和工程组织方式仍然成立。
