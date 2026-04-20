# 思织当前实现说明

## 当前定位

思织现在按 `docs/spec.md` 和 `docs/Ruler.md` 收敛为：

- 本地优先
- 半离线
- 多设备同步预留
- AI 通过抽象层接入

本地 SQLite 仍然是每台设备上的事实数据源。

## 已落地结构

### `shared`

已按规范拆出以下职责：

- `domain/model`
  - 日记、日程、标签、聊天会话、聊天消息、同步对象
- `domain/ai`
  - `AiRequest`、`AiResponse`、`ChatContext`、`ConversationSummary`
- `database`
  - SQLDelight 驱动工厂
- `repository`
  - `DiaryRepository`、`ScheduleRepository`、`TagRepository`、`ChatRepository`、`SyncRepository`
- `data/local`
  - 基于 SQLDelight 的本地仓储实现
- `sync`
  - `SyncApi`、`SyncManager`、`LocalChangeApplier`、`LwwConflictResolver`
- `ai`
  - `AiAgent` 抽象、`OfflineAiAgent`、`KoogAiAgent`、`ChatContextAssembler`
- `app`
  - `MindWeaveFacade`、`MindWeaveAppGraph`

### `composeApp`

已从模板页升级为三块主工作区：

- 日记写作与时间线
- 日程录入与近期日程
- AI 对话与同步状态

### `server`

已提供 MVP 级接口骨架：

- `GET /health`
- `POST /devices/register`
- `POST /sync/push`
- `POST /sync/pull`
- `POST /ai/chat`

当前服务端使用内存实现，目的是先对齐协议与路由；后续再替换为 PostgreSQL 持久化。

## 当前数据库表

客户端本地库已包含：

- `diary_entry`
- `schedule_event`
- `tag`
- `diary_entry_tag`
- `chat_session`
- `chat_message`
- `sync_outbox`
- `sync_metadata`
- `sync_conflict`

并补了从旧演示 schema 迁移到当前多表结构的 `1.sqm`。

## 当前测试覆盖

已补的重点测试：

- 本地仓储写入、软删除、outbox 产生
- 基础 push/pull 同步与增量游标
- AI 上下文组装
- 服务端健康检查与同步接口

## 下一步建议

优先级最高的后续工作：

1. 把服务端从内存实现替换为 PostgreSQL + change_log
2. 给客户端补真正的 Ktor Client 远端配置入口
3. 补编辑/删除 UI 和冲突展示页
4. 把设备 ID / 用户 ID 从硬编码切到真实登录与本地持久化
