-- SQLite creates the database file when this script is executed against a new path.
-- Example:
--   sqlite3 mindweave.sqlite < database/sqlite_init.sql

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS diary_entry (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  mood TEXT NOT NULL,
  ai_summary TEXT,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS diary_entry_user_updated_idx
ON diary_entry(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS diary_entry_user_deleted_idx
ON diary_entry(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS schedule_event (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  start_time_epoch_ms INTEGER NOT NULL,
  end_time_epoch_ms INTEGER NOT NULL,
  remind_at_epoch_ms INTEGER,
  type TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS schedule_event_user_updated_idx
ON schedule_event(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS schedule_event_user_deleted_idx
ON schedule_event(user_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS schedule_event_time_idx
ON schedule_event(user_id, start_time_epoch_ms, end_time_epoch_ms);

CREATE TABLE IF NOT EXISTS tag (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS tag_user_name_idx
ON tag(user_id, name);

CREATE INDEX IF NOT EXISTS tag_user_updated_idx
ON tag(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS tag_user_deleted_idx
ON tag(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS diary_entry_tag (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL,
  entry_id TEXT NOT NULL REFERENCES diary_entry(id),
  tag_id TEXT NOT NULL REFERENCES tag(id),
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS diary_entry_tag_entry_idx
ON diary_entry_tag(entry_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS diary_entry_tag_tag_idx
ON diary_entry_tag(tag_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS diary_entry_tag_user_deleted_idx
ON diary_entry_tag(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS chat_session (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL,
  title TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_session_user_updated_idx
ON chat_session(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS chat_session_user_deleted_idx
ON chat_session(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS chat_message (
  id TEXT NOT NULL PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES chat_session(id),
  user_id TEXT NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL,
  deleted_at_epoch_ms INTEGER,
  version INTEGER NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_message_session_created_idx
ON chat_message(session_id, created_at_epoch_ms ASC);

CREATE INDEX IF NOT EXISTS chat_message_user_updated_idx
ON chat_message(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS chat_message_user_deleted_idx
ON chat_message(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS sync_outbox (
  id TEXT NOT NULL PRIMARY KEY,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  operation TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  retry_count INTEGER NOT NULL,
  status TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS sync_outbox_status_created_idx
ON sync_outbox(status, created_at_epoch_ms ASC);

CREATE TABLE IF NOT EXISTS sync_metadata (
  key TEXT NOT NULL PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_conflict (
  id TEXT NOT NULL PRIMARY KEY,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  local_payload TEXT NOT NULL,
  remote_payload TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS sync_conflict_entity_idx
ON sync_conflict(entity_type, entity_id, created_at_epoch_ms DESC);
