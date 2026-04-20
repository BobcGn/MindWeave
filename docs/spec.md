# SPEC.md

## 1. 项目概述

本项目是一个面向个人使用的跨平台日记应用，核心理念是：

**本地优先 + 半离线 + 多设备同步 + AI 增强**

应用主要服务以下场景：
- 日常记录
- 日程整理
- 反思与总结
- 与 AI 对话协助规划生活
- 在多个设备之间保持数据一致

该项目不是单纯的笔记应用，也不是单纯的聊天应用，而是一个围绕“个人生活记录与整理”构建的系统。

---

## 2. 产品目标

### 2.1 核心目标
实现一个支持以下能力的个人系统：
- 编写日记
- 管理日程
- AI 对话
- 离线使用
- 多设备同步
- 隐私优先

### 2.2 用户体验目标
用户应当能够：
- 在无网时正常写日记与查看历史内容
- 在恢复联网后自动同步到其他设备
- 使用 AI 基于日记和日程获得总结与建议
- 在 Android 与 iOS 上保持接近一致的数据能力
- 不因网络波动影响主要记录体验

---

## 3. 技术目标

### 3.1 客户端
- 使用 KMP 作为共享业务核心
- 使用 SQLite 作为每台设备上的本地数据库
- 使用 SQLDelight 管理跨平台数据库 schema 与查询
- 使用 Ktor 处理网络请求
- 使用 kotlinx.serialization 处理数据序列化
- 使用 Coroutines + Flow 组织异步逻辑

### 3.2 服务端
- 提供同步能力
- 提供设备管理能力
- 提供 AI 编排能力
- 提供备份能力预留
- 使用 PostgreSQL 作为中心数据库

### 3.3 AI
- 通过抽象层接入 Koog
- 允许未来切换模型提供方
- 允许未来引入本地模型

---

## 4. 总体架构

整体采用三层结构：

### 4.1 本地层
每台设备维护自己的本地 SQLite 数据库。
本地数据库是该设备上的事实数据源。

负责：
- 日记读写
- 日程读写
- 聊天缓存
- 本地查询
- 离线可用

### 4.2 同步层
同步层运行在数据库之上，而不是数据库内部。

负责：
- 收集本地变更
- 推送到服务端
- 拉取远端变更
- 应用远端变更
- 冲突检测与处理

### 4.3 云端层
服务端是全局协调中心。

负责：
- 用户与设备管理
- 变更存储
- 增量同步
- AI 编排
- 备份恢复预留
- 后续推送与搜索扩展

---

## 5. 模块设计

推荐模块如下：

- `shared`
- `androidApp`
- `iosApp`
- `server`

### 5.1 shared
共享核心模块，建议包含：
- `domain`
- `database`
- `repository`
- `sync`
- `ai`
- `util`

### 5.2 androidApp
负责：
- Android UI
- 通知
- 平台权限
- Android 生命周期接入

### 5.3 iosApp
负责：
- iOS UI
- iOS 平台能力接入
- 生命周期集成

### 5.4 server
负责：
- 认证骨架
- 设备管理
- 增量同步接口
- AI 编排接口
- 变更日志
- 备份相关能力预留

---

## 6. 核心数据对象

### 6.1 日记
日记是非结构化内容为主的数据对象。

建议字段：
- `id`
- `userId`
- `title`
- `content`
- `mood`
- `createdAt`
- `updatedAt`
- `deletedAt`
- `version`
- `lastModifiedByDeviceId`

可扩展：
- 标签
- 附件
- 关联事件
- AI 摘要
- 情绪分析结果

### 6.2 日程
日程是时间结构化对象。

建议字段：
- `id`
- `userId`
- `title`
- `description`
- `startTime`
- `endTime`
- `remindAt`
- `type`
- `createdAt`
- `updatedAt`
- `deletedAt`
- `version`
- `lastModifiedByDeviceId`

### 6.3 标签
标签用于组织日记与其他内容。

建议字段：
- `id`
- `userId`
- `name`
- `createdAt`
- `updatedAt`
- `deletedAt`
- `version`
- `lastModifiedByDeviceId`

### 6.4 聊天会话
用于组织一段 AI 对话。

建议字段：
- `id`
- `userId`
- `title`
- `createdAt`
- `updatedAt`
- `deletedAt`
- `version`
- `lastModifiedByDeviceId`

### 6.5 聊天消息
建议设计为尽量追加型，减少修改冲突。

建议字段：
- `id`
- `sessionId`
- `userId`
- `role`
- `content`
- `createdAt`
- `updatedAt`
- `deletedAt`
- `version`
- `lastModifiedByDeviceId`

---

## 7. 本地数据库设计要求

本地数据库使用 SQLite。
共享层通过 SQLDelight 管理 schema。

建议首批表：
- `diary_entry`
- `schedule_event`
- `tag`
- `diary_entry_tag`
- `chat_session`
- `chat_message`
- `sync_outbox`
- `sync_metadata`
- `sync_conflict`（可选）

### 7.1 sync_outbox
用于记录待同步本地变更。

建议字段：
- `id`
- `entityType`
- `entityId`
- `operation`
- `payload`
- `createdAt`
- `retryCount`
- `status`

### 7.2 sync_metadata
用于记录同步状态。

建议字段：
- `key`
- `value`

示例：
- `lastSyncToken`
- `lastSyncTime`

### 7.3 sync_conflict
用于记录同步冲突，便于排查与展示。

建议字段：
- `id`
- `entityType`
- `entityId`
- `localPayload`
- `remotePayload`
- `status`
- `createdAt`

---

## 8. 仓储层设计

仓储层用于隔离数据库细节与业务逻辑。

至少需要：
- `DiaryRepository`
- `ScheduleRepository`
- `TagRepository`
- `ChatRepository`
- `SyncRepository`（可选）

职责包括：
- CRUD
- 软删除
- 列表查询
- 按时间范围查询
- 标签关联查询
- 为同步层提供变更读取能力

---

## 9. 同步设计

### 9.1 同步原则
必须采用 **服务端中转的双向增量同步**。

同步过程：
1. 本地修改先写 SQLite
2. 将本地变更加入 outbox
3. 调用 push 接口上传本地变更
4. 调用 pull 接口拉取远端变更
5. 将远端变更应用到本地数据库
6. 更新同步游标

### 9.2 为什么不直接同步 SQLite 文件
不采用 SQLite 文件级同步，原因包括：
- 无法细粒度合并
- 冲突难以处理
- 不适合实时增量同步
- 不利于跨平台一致性控制

### 9.3 冲突策略
初版采用简单规则：
- 日记：最后写入胜出
- 日程：最后写入胜出
- 标签：并集合并
- 聊天消息：追加型，尽量不改历史

### 9.4 同步接口建议
建议服务端提供：
- `POST /sync/push`
- `POST /sync/pull`

或者统一为：
- `POST /sync`

### 9.5 幂等性
必须保证以下能力：
- 重复推送不会重复创建数据
- 重复拉取不会重复应用变更
- 网络异常重试后不会造成明显破坏

---

## 10. 服务端设计

### 10.1 服务端职责
服务端不承担主要编辑体验，而承担增强与协调能力。

服务端负责：
- 认证骨架
- 设备注册
- 增量同步
- 变更日志
- AI 请求编排
- 备份预留
- 后续推送与搜索扩展

### 10.2 服务端数据层建议
主业务表：
- `users`
- `devices`
- `diary_entries`
- `schedule_events`
- `tags`
- `chat_sessions`
- `chat_messages`

同步相关表：
- `change_log`
- `device_sync_state`（可选）

### 10.3 变更日志
推荐使用变更日志实现增量同步。

建议字段：
- `seq`
- `userId`
- `entityType`
- `entityId`
- `operation`
- `payload`
- `createdAt`
- `deviceId`

客户端通过 `lastSyncSeq` 拉取新变更。

---

## 11. AI 设计

### 11.1 AI 抽象
建议定义：
- `AiAgent`
- `AiRequest`
- `AiResponse`
- `ChatContext`
- `ConversationSummary`

### 11.2 AI 的上下文来源
上下文组装应支持：
- 最近日记
- 未来日程
- 最近对话
- 用户偏好（后续）

### 11.3 AI 能力方向
初版建议支持：
- 普通对话
- 今日总结
- 每周总结
- 情绪反思
- 日程建议

### 11.4 AI 部署原则
必须允许：
- 云端模型
- 模型提供方替换
- 后续接入本地模型

---

## 12. 实现阶段规划

### Phase 1：基础架构
目标：
- 搭建模块边界
- 建立 domain 模型
- 建立 SQLDelight schema
- 建立 repository 骨架

### Phase 2：本地可用 MVP
目标：
- 日记 CRUD
- 日程 CRUD
- 聊天基础存储
- 本地查询
- 测试基础逻辑

### Phase 3：同步骨架
目标：
- outbox
- sync metadata
- SyncManager
- SyncApi 抽象
- ConflictResolver
- 基础同步测试

### Phase 4：服务端 MVP
目标：
- 同步接口
- 变更日志
- 设备注册
- 基础认证骨架

### Phase 5：AI 抽象与接入
目标：
- AiAgent
- ChatContext
- Koog 实现占位
- 上下文组装逻辑

### Phase 6：产品增强
目标：
- 总结
- 推送
- 搜索
- 备份
- 语义能力
- 更完整的冲突处理

---

## 13. 测试要求

必须优先测试以下内容：
- SQLDelight 查询正确性
- Repository 行为
- 软删除
- 同步 push/pull
- 幂等性
- 冲突处理
- AI 上下文组装

---

## 14. 非目标

当前阶段不追求：
- 复杂 CRDT 协同编辑
- 文件级数据库复制
- 大型权限管理后台
- 重量级工作流引擎
- 复杂多租户能力

本项目首先服务于个人场景，而不是企业协作系统。

---

## 15. 成功标准

第一阶段成功标准：
- 在单设备离线可稳定使用
- 本地数据库结构合理且可扩展
- 同步协议已具备演进空间
- 服务端 MVP 可支撑多设备同步
- AI 接入已留好抽象

最终成功标准：
- 用户可以在多个设备上使用同一个账号
- 日记、日程、AI 对话可稳定同步
- 离线时可正常工作
- 联网后可恢复同步
- AI 能基于个人数据给出有用反馈