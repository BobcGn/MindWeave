package org.example.mindweave.server.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.domain.model.SyncableEntity
import org.example.mindweave.domain.model.Tag
import org.example.mindweave.util.MindWeaveJson
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties

class PostgresServerSyncRepository(
    private val config: PostgresSyncDatabaseConfig,
) : ServerSyncRepository, AutoCloseable {
    private val transactionConnection = ThreadLocal<Connection?>()

    init {
        runCatching { Class.forName("org.postgresql.Driver") }
            .getOrElse { throw IllegalStateException("PostgreSQL JDBC driver is not available.", it) }
    }

    override fun upsertUser(record: ServerUserRecord) {
        executeUpdate(UPSERT_USER_SQL) {
            setString(1, record.id)
            setLong(2, record.createdAtEpochMs)
            setLong(3, record.updatedAtEpochMs)
        }
    }

    override fun findUser(userId: String): ServerUserRecord? =
        queryOneOrNull(SELECT_USER_SQL, {
            setString(1, userId)
        }) { resultSet ->
            ServerUserRecord(
                id = resultSet.getString("id"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
            )
        }

    override fun upsertDevice(record: RegisteredDeviceRecord) {
        executeUpdate(UPSERT_DEVICE_SQL) {
            setString(1, record.userId)
            setString(2, record.deviceId)
            setString(3, record.deviceName)
            setLong(4, record.registeredAtEpochMs)
            setLong(5, record.lastSeenAtEpochMs)
        }
    }

    override fun findDevice(userId: String, deviceId: String): RegisteredDeviceRecord? =
        queryOneOrNull(SELECT_DEVICE_SQL, {
            setString(1, userId)
            setString(2, deviceId)
        }) { resultSet ->
            RegisteredDeviceRecord(
                userId = resultSet.getString("user_id"),
                deviceId = resultSet.getString("device_id"),
                deviceName = resultSet.getString("device_name"),
                registeredAtEpochMs = resultSet.getLong("registered_at_epoch_ms"),
                lastSeenAtEpochMs = resultSet.getLong("last_seen_at_epoch_ms"),
            )
        }

    override fun findEntitySnapshot(
        userId: String,
        entityType: EntityType,
        entityId: String,
    ): ServerEntitySnapshot? = when (entityType) {
        EntityType.DIARY_ENTRY -> queryOneOrNull(SELECT_DIARY_ENTRY_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = DiaryEntry(
                id = resultSet.getString("id"),
                userId = resultSet.getString("user_id"),
                title = resultSet.getString("title"),
                content = resultSet.getString("content"),
                mood = DiaryMood.fromStorage(resultSet.getString("mood")),
                aiSummary = resultSet.getString("ai_summary"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }

        EntityType.SCHEDULE_EVENT -> queryOneOrNull(SELECT_SCHEDULE_EVENT_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = ScheduleEvent(
                id = resultSet.getString("id"),
                userId = resultSet.getString("user_id"),
                title = resultSet.getString("title"),
                description = resultSet.getString("description"),
                startTimeEpochMs = resultSet.getLong("start_time_epoch_ms"),
                endTimeEpochMs = resultSet.getLong("end_time_epoch_ms"),
                remindAtEpochMs = resultSet.getNullableLong("remind_at_epoch_ms"),
                type = ScheduleType.fromStorage(resultSet.getString("type")),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }

        EntityType.TAG -> queryOneOrNull(SELECT_TAG_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = Tag(
                id = resultSet.getString("id"),
                userId = resultSet.getString("user_id"),
                name = resultSet.getString("name"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }

        EntityType.DIARY_ENTRY_TAG -> queryOneOrNull(SELECT_DIARY_ENTRY_TAG_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = DiaryEntryTag(
                id = resultSet.getString("id"),
                userId = resultSet.getString("user_id"),
                entryId = resultSet.getString("entry_id"),
                tagId = resultSet.getString("tag_id"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }

        EntityType.CHAT_SESSION -> queryOneOrNull(SELECT_CHAT_SESSION_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = ChatSession(
                id = resultSet.getString("id"),
                userId = resultSet.getString("user_id"),
                title = resultSet.getString("title"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }

        EntityType.CHAT_MESSAGE -> queryOneOrNull(SELECT_CHAT_MESSAGE_SQL, {
            setString(1, userId)
            setString(2, entityId)
        }) { resultSet ->
            val entity = ChatMessage(
                id = resultSet.getString("id"),
                sessionId = resultSet.getString("session_id"),
                userId = resultSet.getString("user_id"),
                role = ChatRole.fromStorage(resultSet.getString("role")),
                content = resultSet.getString("content"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                deletedAtEpochMs = resultSet.getNullableLong("deleted_at_epoch_ms"),
                version = resultSet.getLong("version"),
                lastModifiedByDeviceId = resultSet.getString("last_modified_by_device_id"),
            )
            snapshotFrom(entityType, entity)
        }
    }

    override fun saveEntitySnapshot(snapshot: ServerEntitySnapshot) {
        when (snapshot.entityType) {
            EntityType.DIARY_ENTRY -> {
                val entity = MindWeaveJson.decodeFromString<DiaryEntry>(snapshot.payload)
                executeUpdate(UPSERT_DIARY_ENTRY_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.userId)
                    setString(3, entity.title)
                    setString(4, entity.content)
                    setString(5, entity.mood.name)
                    setString(6, entity.aiSummary)
                    setLong(7, entity.createdAtEpochMs)
                    setLong(8, entity.updatedAtEpochMs)
                    setNullableLong(9, entity.deletedAtEpochMs)
                    setLong(10, entity.version)
                    setString(11, entity.lastModifiedByDeviceId)
                }
            }

            EntityType.SCHEDULE_EVENT -> {
                val entity = MindWeaveJson.decodeFromString<ScheduleEvent>(snapshot.payload)
                executeUpdate(UPSERT_SCHEDULE_EVENT_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.userId)
                    setString(3, entity.title)
                    setString(4, entity.description)
                    setLong(5, entity.startTimeEpochMs)
                    setLong(6, entity.endTimeEpochMs)
                    setNullableLong(7, entity.remindAtEpochMs)
                    setString(8, entity.type.name)
                    setLong(9, entity.createdAtEpochMs)
                    setLong(10, entity.updatedAtEpochMs)
                    setNullableLong(11, entity.deletedAtEpochMs)
                    setLong(12, entity.version)
                    setString(13, entity.lastModifiedByDeviceId)
                }
            }

            EntityType.TAG -> {
                val entity = MindWeaveJson.decodeFromString<Tag>(snapshot.payload)
                executeUpdate(UPSERT_TAG_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.userId)
                    setString(3, entity.name)
                    setLong(4, entity.createdAtEpochMs)
                    setLong(5, entity.updatedAtEpochMs)
                    setNullableLong(6, entity.deletedAtEpochMs)
                    setLong(7, entity.version)
                    setString(8, entity.lastModifiedByDeviceId)
                }
            }

            EntityType.DIARY_ENTRY_TAG -> {
                val entity = MindWeaveJson.decodeFromString<DiaryEntryTag>(snapshot.payload)
                executeUpdate(UPSERT_DIARY_ENTRY_TAG_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.userId)
                    setString(3, entity.entryId)
                    setString(4, entity.tagId)
                    setLong(5, entity.createdAtEpochMs)
                    setLong(6, entity.updatedAtEpochMs)
                    setNullableLong(7, entity.deletedAtEpochMs)
                    setLong(8, entity.version)
                    setString(9, entity.lastModifiedByDeviceId)
                }
            }

            EntityType.CHAT_SESSION -> {
                val entity = MindWeaveJson.decodeFromString<ChatSession>(snapshot.payload)
                executeUpdate(UPSERT_CHAT_SESSION_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.userId)
                    setString(3, entity.title)
                    setLong(4, entity.createdAtEpochMs)
                    setLong(5, entity.updatedAtEpochMs)
                    setNullableLong(6, entity.deletedAtEpochMs)
                    setLong(7, entity.version)
                    setString(8, entity.lastModifiedByDeviceId)
                }
            }

            EntityType.CHAT_MESSAGE -> {
                val entity = MindWeaveJson.decodeFromString<ChatMessage>(snapshot.payload)
                executeUpdate(UPSERT_CHAT_MESSAGE_SQL) {
                    setString(1, entity.id)
                    setString(2, entity.sessionId)
                    setString(3, entity.userId)
                    setString(4, entity.role.name)
                    setString(5, entity.content)
                    setLong(6, entity.createdAtEpochMs)
                    setLong(7, entity.updatedAtEpochMs)
                    setNullableLong(8, entity.deletedAtEpochMs)
                    setLong(9, entity.version)
                    setString(10, entity.lastModifiedByDeviceId)
                }
            }
        }
    }

    override fun hasDedupeKey(userId: String, dedupeKey: String): Boolean =
        queryOneOrNull(HAS_DEDUPE_KEY_SQL, {
            setString(1, userId)
            setString(2, dedupeKey)
        }) { true } ?: false

    override fun saveDedupeKey(userId: String, dedupeKey: String) {
        executeUpdate(INSERT_DEDUPE_KEY_SQL) {
            setString(1, userId)
            setString(2, dedupeKey)
        }
    }

    override fun appendChange(record: PendingChangeLogRecord): ServerChangeLogRecord =
        executeReturning(INSERT_CHANGE_LOG_SQL, {
            setString(1, record.userId)
            setString(2, record.entityType.name)
            setString(3, record.entityId)
            setString(4, record.operation.name)
            setString(5, record.payload)
            setLong(6, record.createdAtEpochMs)
            setString(7, record.deviceId)
            setLong(8, record.version)
            setLong(9, record.updatedAtEpochMs)
            setString(10, record.dedupeKey)
        }) { resultSet ->
            ServerChangeLogRecord(
                seq = resultSet.getLong("seq"),
                userId = resultSet.getString("user_id"),
                entityType = EntityType.valueOf(resultSet.getString("entity_type")),
                entityId = resultSet.getString("entity_id"),
                operation = SyncOperation.valueOf(resultSet.getString("operation")),
                payload = resultSet.getString("payload"),
                createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
                deviceId = resultSet.getString("device_id"),
                version = resultSet.getLong("version"),
                updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
                dedupeKey = resultSet.getString("dedupe_key"),
            )
        }

    override fun listChangesAfter(
        userId: String,
        afterSeq: Long,
        excludedDeviceId: String,
    ): List<ServerChangeLogRecord> = queryList(SELECT_CHANGES_AFTER_SQL, {
        setString(1, userId)
        setLong(2, afterSeq)
        setString(3, excludedDeviceId)
    }) { resultSet ->
        ServerChangeLogRecord(
            seq = resultSet.getLong("seq"),
            userId = resultSet.getString("user_id"),
            entityType = EntityType.valueOf(resultSet.getString("entity_type")),
            entityId = resultSet.getString("entity_id"),
            operation = SyncOperation.valueOf(resultSet.getString("operation")),
            payload = resultSet.getString("payload"),
            createdAtEpochMs = resultSet.getLong("created_at_epoch_ms"),
            deviceId = resultSet.getString("device_id"),
            version = resultSet.getLong("version"),
            updatedAtEpochMs = resultSet.getLong("updated_at_epoch_ms"),
            dedupeKey = resultSet.getString("dedupe_key"),
        )
    }

    override fun latestSeq(userId: String): Long =
        queryOneOrNull(SELECT_LATEST_SEQ_SQL, {
            setString(1, userId)
        }) { resultSet ->
            resultSet.getLong("latest_seq")
        } ?: 0L

    override fun getDeviceSyncState(userId: String, deviceId: String): DeviceSyncStateRecord? =
        queryOneOrNull(SELECT_DEVICE_SYNC_STATE_SQL, {
            setString(1, userId)
            setString(2, deviceId)
        }) { resultSet ->
            DeviceSyncStateRecord(
                userId = resultSet.getString("user_id"),
                deviceId = resultSet.getString("device_id"),
                lastPulledSeq = resultSet.getLong("last_pulled_seq"),
                lastPushAtEpochMs = resultSet.getNullableLong("last_push_at_epoch_ms"),
                lastPullAtEpochMs = resultSet.getNullableLong("last_pull_at_epoch_ms"),
            )
        }

    override fun updateDeviceSyncState(record: DeviceSyncStateRecord) {
        executeUpdate(UPSERT_DEVICE_SYNC_STATE_SQL) {
            setString(1, record.userId)
            setString(2, record.deviceId)
            setLong(3, record.lastPulledSeq)
            setNullableLong(4, record.lastPushAtEpochMs)
            setNullableLong(5, record.lastPullAtEpochMs)
        }
    }

    override fun saveConflict(record: ServerSyncConflictRecord) {
        executeUpdate(INSERT_SYNC_CONFLICT_SQL) {
            setString(1, record.id)
            setString(2, record.userId)
            setString(3, record.entityType.name)
            setString(4, record.entityId)
            setString(5, record.localPayload)
            setString(6, record.remotePayload)
            setString(7, record.status)
            setNullableLong(8, record.localVersion)
            setNullableLong(9, record.remoteVersion)
            setLong(10, record.createdAtEpochMs)
        }
    }

    override fun <T> inTransaction(block: () -> T): T {
        val existingConnection = transactionConnection.get()
        if (existingConnection != null) {
            return block()
        }

        return openConnection().use { connection ->
            connection.autoCommit = false
            transactionConnection.set(connection)
            try {
                val result = block()
                connection.commit()
                result
            } catch (throwable: Throwable) {
                runCatching { connection.rollback() }
                throw throwable
            } finally {
                transactionConnection.remove()
                connection.autoCommit = true
            }
        }
    }

    override fun close() {
        transactionConnection.remove()
    }

    private fun snapshotFrom(entityType: EntityType, entity: SyncableEntity): ServerEntitySnapshot =
        ServerEntitySnapshot(
            userId = entity.userId,
            entityType = entityType,
            entityId = entity.id,
            payload = when (entity) {
                is DiaryEntry -> MindWeaveJson.encodeToString(entity)
                is ScheduleEvent -> MindWeaveJson.encodeToString(entity)
                is Tag -> MindWeaveJson.encodeToString(entity)
                is DiaryEntryTag -> MindWeaveJson.encodeToString(entity)
                is ChatSession -> MindWeaveJson.encodeToString(entity)
                is ChatMessage -> MindWeaveJson.encodeToString(entity)
                else -> error("Unsupported sync entity type: ${entity::class.simpleName}")
            },
            version = entity.version,
            updatedAtEpochMs = entity.updatedAtEpochMs,
            deletedAtEpochMs = entity.deletedAtEpochMs,
            lastModifiedByDeviceId = entity.lastModifiedByDeviceId,
        )

    private fun openConnection(): Connection {
        val properties = Properties()
        config.username?.let { properties["user"] = it }
        config.password?.let { properties["password"] = it }
        return if (properties.isEmpty()) {
            DriverManager.getConnection(config.jdbcUrl)
        } else {
            DriverManager.getConnection(config.jdbcUrl, properties)
        }
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T {
        val current = transactionConnection.get()
        return if (current != null) {
            block(current)
        } else {
            openConnection().use(block)
        }
    }

    private fun executeUpdate(
        sql: String,
        bind: PreparedStatement.() -> Unit,
    ) {
        withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.bind()
                statement.executeUpdate()
            }
        }
    }

    private fun <T> executeReturning(
        sql: String,
        bind: PreparedStatement.() -> Unit,
        map: (ResultSet) -> T,
    ): T = withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    error("Expected the database to return a row.")
                }
                map(resultSet)
            }
        }
    }

    private fun <T> queryOneOrNull(
        sql: String,
        bind: PreparedStatement.() -> Unit,
        map: (ResultSet) -> T,
    ): T? = withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) map(resultSet) else null
            }
        }
    }

    private fun <T> queryList(
        sql: String,
        bind: PreparedStatement.() -> Unit,
        map: (ResultSet) -> T,
    ): List<T> = withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(map(resultSet))
                    }
                }
            }
        }
    }

    private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) {
            setNull(index, Types.BIGINT)
        } else {
            setLong(index, value)
        }
    }

    private fun ResultSet.getNullableLong(columnLabel: String): Long? {
        val value = getLong(columnLabel)
        return if (wasNull()) null else value
    }

    private companion object {
        private const val UPSERT_USER_SQL = """
            INSERT INTO users(id, created_at_epoch_ms, updated_at_epoch_ms)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms
        """

        private const val SELECT_USER_SQL = """
            SELECT id, created_at_epoch_ms, updated_at_epoch_ms
            FROM users
            WHERE id = ?
        """

        private const val UPSERT_DEVICE_SQL = """
            INSERT INTO devices(user_id, device_id, device_name, registered_at_epoch_ms, last_seen_at_epoch_ms)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, device_id) DO UPDATE
            SET
              device_name = EXCLUDED.device_name,
              registered_at_epoch_ms = EXCLUDED.registered_at_epoch_ms,
              last_seen_at_epoch_ms = EXCLUDED.last_seen_at_epoch_ms
        """

        private const val SELECT_DEVICE_SQL = """
            SELECT user_id, device_id, device_name, registered_at_epoch_ms, last_seen_at_epoch_ms
            FROM devices
            WHERE user_id = ? AND device_id = ?
        """

        private const val SELECT_DIARY_ENTRY_SQL = """
            SELECT
              id,
              user_id,
              title,
              content,
              mood,
              ai_summary,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM diary_entries
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_DIARY_ENTRY_SQL = """
            INSERT INTO diary_entries(
              id,
              user_id,
              title,
              content,
              mood,
              ai_summary,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              user_id = EXCLUDED.user_id,
              title = EXCLUDED.title,
              content = EXCLUDED.content,
              mood = EXCLUDED.mood,
              ai_summary = EXCLUDED.ai_summary,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val SELECT_SCHEDULE_EVENT_SQL = """
            SELECT
              id,
              user_id,
              title,
              description,
              start_time_epoch_ms,
              end_time_epoch_ms,
              remind_at_epoch_ms,
              type,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM schedule_events
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_SCHEDULE_EVENT_SQL = """
            INSERT INTO schedule_events(
              id,
              user_id,
              title,
              description,
              start_time_epoch_ms,
              end_time_epoch_ms,
              remind_at_epoch_ms,
              type,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              user_id = EXCLUDED.user_id,
              title = EXCLUDED.title,
              description = EXCLUDED.description,
              start_time_epoch_ms = EXCLUDED.start_time_epoch_ms,
              end_time_epoch_ms = EXCLUDED.end_time_epoch_ms,
              remind_at_epoch_ms = EXCLUDED.remind_at_epoch_ms,
              type = EXCLUDED.type,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val SELECT_TAG_SQL = """
            SELECT
              id,
              user_id,
              name,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM tags
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_TAG_SQL = """
            INSERT INTO tags(
              id,
              user_id,
              name,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              user_id = EXCLUDED.user_id,
              name = EXCLUDED.name,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val SELECT_DIARY_ENTRY_TAG_SQL = """
            SELECT
              id,
              user_id,
              entry_id,
              tag_id,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM diary_entry_tags
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_DIARY_ENTRY_TAG_SQL = """
            INSERT INTO diary_entry_tags(
              id,
              user_id,
              entry_id,
              tag_id,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              user_id = EXCLUDED.user_id,
              entry_id = EXCLUDED.entry_id,
              tag_id = EXCLUDED.tag_id,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val SELECT_CHAT_SESSION_SQL = """
            SELECT
              id,
              user_id,
              title,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM chat_sessions
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_CHAT_SESSION_SQL = """
            INSERT INTO chat_sessions(
              id,
              user_id,
              title,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              user_id = EXCLUDED.user_id,
              title = EXCLUDED.title,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val SELECT_CHAT_MESSAGE_SQL = """
            SELECT
              id,
              session_id,
              user_id,
              role,
              content,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            FROM chat_messages
            WHERE user_id = ? AND id = ?
        """

        private const val UPSERT_CHAT_MESSAGE_SQL = """
            INSERT INTO chat_messages(
              id,
              session_id,
              user_id,
              role,
              content,
              created_at_epoch_ms,
              updated_at_epoch_ms,
              deleted_at_epoch_ms,
              version,
              last_modified_by_device_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET
              session_id = EXCLUDED.session_id,
              user_id = EXCLUDED.user_id,
              role = EXCLUDED.role,
              content = EXCLUDED.content,
              created_at_epoch_ms = EXCLUDED.created_at_epoch_ms,
              updated_at_epoch_ms = EXCLUDED.updated_at_epoch_ms,
              deleted_at_epoch_ms = EXCLUDED.deleted_at_epoch_ms,
              version = EXCLUDED.version,
              last_modified_by_device_id = EXCLUDED.last_modified_by_device_id
        """

        private const val HAS_DEDUPE_KEY_SQL = """
            SELECT 1
            FROM sync_dedupe_keys
            WHERE user_id = ? AND dedupe_key = ?
        """

        private const val INSERT_DEDUPE_KEY_SQL = """
            INSERT INTO sync_dedupe_keys(user_id, dedupe_key)
            VALUES (?, ?)
            ON CONFLICT (user_id, dedupe_key) DO NOTHING
        """

        private const val INSERT_CHANGE_LOG_SQL = """
            INSERT INTO change_log(
              user_id,
              entity_type,
              entity_id,
              operation,
              payload,
              created_at_epoch_ms,
              device_id,
              version,
              updated_at_epoch_ms,
              dedupe_key
            ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
            ON CONFLICT (dedupe_key) DO UPDATE
            SET dedupe_key = EXCLUDED.dedupe_key
            RETURNING
              seq,
              user_id,
              entity_type,
              entity_id,
              operation,
              payload::text AS payload,
              created_at_epoch_ms,
              device_id,
              version,
              updated_at_epoch_ms,
              dedupe_key
        """

        private const val SELECT_CHANGES_AFTER_SQL = """
            SELECT
              seq,
              user_id,
              entity_type,
              entity_id,
              operation,
              payload::text AS payload,
              created_at_epoch_ms,
              device_id,
              version,
              updated_at_epoch_ms,
              dedupe_key
            FROM change_log
            WHERE user_id = ?
              AND seq > ?
              AND device_id <> ?
            ORDER BY seq ASC
        """

        private const val SELECT_LATEST_SEQ_SQL = """
            SELECT COALESCE(MAX(seq), 0) AS latest_seq
            FROM change_log
            WHERE user_id = ?
        """

        private const val SELECT_DEVICE_SYNC_STATE_SQL = """
            SELECT
              user_id,
              device_id,
              last_pulled_seq,
              last_push_at_epoch_ms,
              last_pull_at_epoch_ms
            FROM device_sync_state
            WHERE user_id = ? AND device_id = ?
        """

        private const val UPSERT_DEVICE_SYNC_STATE_SQL = """
            INSERT INTO device_sync_state(
              user_id,
              device_id,
              last_pulled_seq,
              last_push_at_epoch_ms,
              last_pull_at_epoch_ms
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, device_id) DO UPDATE
            SET
              last_pulled_seq = EXCLUDED.last_pulled_seq,
              last_push_at_epoch_ms = EXCLUDED.last_push_at_epoch_ms,
              last_pull_at_epoch_ms = EXCLUDED.last_pull_at_epoch_ms
        """

        private const val INSERT_SYNC_CONFLICT_SQL = """
            INSERT INTO sync_conflicts(
              id,
              user_id,
              entity_type,
              entity_id,
              local_payload,
              remote_payload,
              status,
              local_version,
              remote_version,
              created_at_epoch_ms
            ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?)
        """
    }
}
