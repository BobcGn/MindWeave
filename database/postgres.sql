-- MindWeave PostgreSQL bootstrap script.
-- Run this with psql against a maintenance database such as `postgres`:
--   psql -d postgres -f database/postgres.sql

SELECT 'CREATE DATABASE mindweave WITH ENCODING ''UTF8'''
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_database
  WHERE datname = 'mindweave'
)\gexec

\connect mindweave

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  device_name TEXT NOT NULL,
  registered_at_epoch_ms BIGINT NOT NULL,
  last_seen_at_epoch_ms BIGINT NOT NULL,
  PRIMARY KEY (user_id, device_id)
);

CREATE TABLE IF NOT EXISTS diary_entries (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  mood TEXT NOT NULL,
  ai_summary TEXT,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS diary_entries_user_updated_idx
ON diary_entries(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS diary_entries_user_deleted_idx
ON diary_entries(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS schedule_events (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  start_time_epoch_ms BIGINT NOT NULL,
  end_time_epoch_ms BIGINT NOT NULL,
  remind_at_epoch_ms BIGINT,
  type TEXT NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS schedule_events_user_updated_idx
ON schedule_events(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS schedule_events_user_deleted_idx
ON schedule_events(user_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS schedule_events_time_idx
ON schedule_events(user_id, start_time_epoch_ms, end_time_epoch_ms);

CREATE TABLE IF NOT EXISTS tags (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS tags_user_name_idx
ON tags(user_id, name);

CREATE INDEX IF NOT EXISTS tags_user_updated_idx
ON tags(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS tags_user_deleted_idx
ON tags(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS diary_entry_tags (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  entry_id TEXT NOT NULL REFERENCES diary_entries(id) ON DELETE CASCADE,
  tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS diary_entry_tags_entry_idx
ON diary_entry_tags(entry_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS diary_entry_tags_tag_idx
ON diary_entry_tags(tag_id, deleted_at_epoch_ms);

CREATE INDEX IF NOT EXISTS diary_entry_tags_user_deleted_idx
ON diary_entry_tags(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS chat_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_sessions_user_updated_idx
ON chat_sessions(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS chat_sessions_user_deleted_idx
ON chat_sessions(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS chat_messages (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  deleted_at_epoch_ms BIGINT,
  version BIGINT NOT NULL,
  last_modified_by_device_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_messages_session_created_idx
ON chat_messages(session_id, created_at_epoch_ms ASC);

CREATE INDEX IF NOT EXISTS chat_messages_user_updated_idx
ON chat_messages(user_id, updated_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS chat_messages_user_deleted_idx
ON chat_messages(user_id, deleted_at_epoch_ms);

CREATE TABLE IF NOT EXISTS change_log (
  seq BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  operation TEXT NOT NULL,
  payload JSONB NOT NULL,
  created_at_epoch_ms BIGINT NOT NULL,
  device_id TEXT NOT NULL,
  version BIGINT NOT NULL,
  updated_at_epoch_ms BIGINT NOT NULL,
  dedupe_key TEXT NOT NULL UNIQUE,
  CONSTRAINT change_log_device_fk
    FOREIGN KEY (user_id, device_id) REFERENCES devices(user_id, device_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS change_log_user_seq_idx
ON change_log(user_id, seq);

CREATE INDEX IF NOT EXISTS change_log_user_entity_seq_idx
ON change_log(user_id, entity_type, entity_id, seq DESC);

CREATE INDEX IF NOT EXISTS change_log_user_device_created_idx
ON change_log(user_id, device_id, created_at_epoch_ms DESC);

CREATE TABLE IF NOT EXISTS sync_dedupe_keys (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  dedupe_key TEXT NOT NULL,
  PRIMARY KEY (user_id, dedupe_key)
);

CREATE TABLE IF NOT EXISTS device_sync_state (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  last_pulled_seq BIGINT NOT NULL DEFAULT 0,
  last_push_at_epoch_ms BIGINT,
  last_pull_at_epoch_ms BIGINT,
  PRIMARY KEY (user_id, device_id),
  CONSTRAINT device_sync_state_device_fk
    FOREIGN KEY (user_id, device_id) REFERENCES devices(user_id, device_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS device_sync_state_last_pull_idx
ON device_sync_state(user_id, last_pulled_seq DESC);

CREATE TABLE IF NOT EXISTS sync_conflicts (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  local_payload JSONB NOT NULL,
  remote_payload JSONB NOT NULL,
  status TEXT NOT NULL,
  local_version BIGINT,
  remote_version BIGINT,
  created_at_epoch_ms BIGINT NOT NULL,
  resolved_at_epoch_ms BIGINT
);

CREATE INDEX IF NOT EXISTS sync_conflicts_user_created_idx
ON sync_conflicts(user_id, created_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS sync_conflicts_entity_idx
ON sync_conflicts(user_id, entity_type, entity_id, created_at_epoch_ms DESC);
