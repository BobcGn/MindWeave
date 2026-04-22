package org.example.mindweave.util

import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.ai.ModelInstallStatus
import org.example.mindweave.ai.ModelPackage
import org.example.mindweave.ai.ModelPackageKind
import org.example.mindweave.ai.ModelVersion
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.domain.model.ChatMessage
import org.example.mindweave.domain.model.ChatRole
import org.example.mindweave.domain.model.ChatSession
import org.example.mindweave.domain.model.ConflictStatus
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.domain.model.DiaryTimelineItem
import org.example.mindweave.domain.model.EntityType
import org.example.mindweave.domain.model.OutboxChange
import org.example.mindweave.domain.model.OutboxStatus
import org.example.mindweave.domain.model.ScheduleEvent
import org.example.mindweave.domain.model.ScheduleType
import org.example.mindweave.domain.model.SyncConflictRecord
import org.example.mindweave.domain.model.SyncOperation
import org.example.mindweave.domain.model.SyncState
import org.example.mindweave.domain.model.SyncableEntity
import org.example.mindweave.domain.model.Tag
import org.example.mindweave.domain.model.UserAccount
import org.example.mindweave.domain.model.UserPreferences

internal sealed interface JsonValue

internal data class JsonObject(
    val properties: Map<String, JsonValue>,
) : JsonValue

internal data class JsonArray(
    val values: List<JsonValue>,
) : JsonValue

internal data class JsonString(
    val value: String,
) : JsonValue

internal data class JsonNumber(
    val raw: String,
) : JsonValue

internal data class JsonBoolean(
    val value: Boolean,
) : JsonValue

internal data object JsonNull : JsonValue

internal fun parseJson(text: String): JsonValue = JsonParser(text).parse()

internal fun stringifyJson(value: JsonValue): String = buildString {
    appendJsonValue(value)
}

internal fun jsonObjectOf(vararg pairs: Pair<String, JsonValue>): JsonObject =
    JsonObject(linkedMapOf<String, JsonValue>().apply { pairs.forEach { (key, value) -> put(key, value) } })

internal fun jsonArrayOf(values: List<JsonValue>): JsonArray = JsonArray(values)

internal fun jsonString(value: String?): JsonValue = value?.let(::JsonString) ?: JsonNull

internal fun jsonLong(value: Long?): JsonValue = value?.let { JsonNumber(it.toString()) } ?: JsonNull

internal fun jsonBoolean(value: Boolean?): JsonValue = value?.let(::JsonBoolean) ?: JsonNull

internal fun JsonValue.asObject(): JsonObject = this as? JsonObject ?: error("Expected JsonObject.")

internal fun JsonValue.asArray(): JsonArray = this as? JsonArray ?: error("Expected JsonArray.")

internal fun JsonValue.asString(): String = when (this) {
    is JsonString -> value
    is JsonNumber -> raw
    is JsonBoolean -> value.toString()
    JsonNull -> ""
    else -> error("Expected scalar string value.")
}

internal fun JsonValue.asLong(): Long = when (this) {
    is JsonNumber -> raw.toLong()
    is JsonString -> value.toLong()
    else -> error("Expected numeric value.")
}

internal fun JsonValue.asBoolean(): Boolean = when (this) {
    is JsonBoolean -> value
    is JsonString -> value.equals("true", ignoreCase = true)
    else -> error("Expected boolean value.")
}

internal fun JsonObject.string(name: String, default: String = ""): String =
    (properties[name] ?: JsonNull).let {
        if (it == JsonNull) default else it.asString()
    }

internal fun JsonObject.long(name: String, default: Long = 0L): Long =
    (properties[name] ?: JsonNull).let {
        if (it == JsonNull) default else it.asLong()
    }

internal fun JsonObject.longOrNull(name: String): Long? =
    properties[name]?.takeUnless { it == JsonNull }?.asLong()

internal fun JsonObject.boolean(name: String, default: Boolean = false): Boolean =
    (properties[name] ?: JsonNull).let {
        if (it == JsonNull) default else it.asBoolean()
    }

internal fun JsonObject.stringOrNull(name: String): String? =
    properties[name]?.takeUnless { it == JsonNull }?.asString()

internal fun JsonObject.objectOrNull(name: String): JsonObject? =
    properties[name]?.takeUnless { it == JsonNull }?.asObject()

internal fun JsonObject.array(name: String): List<JsonValue> =
    properties[name]?.takeUnless { it == JsonNull }?.asArray()?.values ?: emptyList()

internal fun JsonObject.stringList(name: String): List<String> =
    array(name).map(JsonValue::asString)

internal fun DiaryEntry.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "userId" to JsonString(userId),
    "title" to JsonString(title),
    "content" to JsonString(content),
    "mood" to JsonString(mood.name),
    "aiSummary" to jsonString(aiSummary),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toDiaryEntry(): DiaryEntry = DiaryEntry(
    id = string("id"),
    userId = string("userId"),
    title = string("title"),
    content = string("content"),
    mood = DiaryMood.fromStorage(string("mood")),
    aiSummary = stringOrNull("aiSummary"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun DiaryTimelineItem.toJson(): JsonObject = jsonObjectOf(
    "entry" to entry.toJson(),
    "tags" to jsonArrayOf(tags.map(::JsonString)),
)

internal fun ScheduleEvent.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "userId" to JsonString(userId),
    "title" to JsonString(title),
    "description" to JsonString(description),
    "startTimeEpochMs" to jsonLong(startTimeEpochMs),
    "endTimeEpochMs" to jsonLong(endTimeEpochMs),
    "remindAtEpochMs" to jsonLong(remindAtEpochMs),
    "type" to JsonString(type.name),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toScheduleEvent(): ScheduleEvent = ScheduleEvent(
    id = string("id"),
    userId = string("userId"),
    title = string("title"),
    description = string("description"),
    startTimeEpochMs = long("startTimeEpochMs"),
    endTimeEpochMs = long("endTimeEpochMs"),
    remindAtEpochMs = longOrNull("remindAtEpochMs"),
    type = ScheduleType.fromStorage(string("type")),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun Tag.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "userId" to JsonString(userId),
    "name" to JsonString(name),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toTag(): Tag = Tag(
    id = string("id"),
    userId = string("userId"),
    name = string("name"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun DiaryEntryTag.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "userId" to JsonString(userId),
    "entryId" to JsonString(entryId),
    "tagId" to JsonString(tagId),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toDiaryEntryTag(): DiaryEntryTag = DiaryEntryTag(
    id = string("id"),
    userId = string("userId"),
    entryId = string("entryId"),
    tagId = string("tagId"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun ChatSession.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "userId" to JsonString(userId),
    "title" to JsonString(title),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toChatSession(): ChatSession = ChatSession(
    id = string("id"),
    userId = string("userId"),
    title = string("title"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun ChatMessage.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "sessionId" to JsonString(sessionId),
    "userId" to JsonString(userId),
    "role" to JsonString(role.name),
    "content" to JsonString(content),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "deletedAtEpochMs" to jsonLong(deletedAtEpochMs),
    "version" to jsonLong(version),
    "lastModifiedByDeviceId" to JsonString(lastModifiedByDeviceId),
)

internal fun JsonObject.toChatMessage(): ChatMessage = ChatMessage(
    id = string("id"),
    sessionId = string("sessionId"),
    userId = string("userId"),
    role = ChatRole.fromStorage(string("role")),
    content = string("content"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    deletedAtEpochMs = longOrNull("deletedAtEpochMs"),
    version = long("version"),
    lastModifiedByDeviceId = string("lastModifiedByDeviceId"),
)

internal fun OutboxChange.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "entityType" to JsonString(entityType.name),
    "entityId" to JsonString(entityId),
    "operation" to JsonString(operation.name),
    "payload" to JsonString(payload),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "retryCount" to jsonLong(retryCount),
    "status" to JsonString(status.name),
)

internal fun JsonObject.toOutboxChange(): OutboxChange = OutboxChange(
    id = string("id"),
    entityType = EntityType.valueOf(string("entityType")),
    entityId = string("entityId"),
    operation = SyncOperation.valueOf(string("operation")),
    payload = string("payload"),
    createdAtEpochMs = long("createdAtEpochMs"),
    retryCount = long("retryCount"),
    status = OutboxStatus.valueOf(string("status")),
)

internal fun SyncConflictRecord.toJson(): JsonObject = jsonObjectOf(
    "id" to JsonString(id),
    "entityType" to JsonString(entityType.name),
    "entityId" to JsonString(entityId),
    "localPayload" to JsonString(localPayload),
    "remotePayload" to JsonString(remotePayload),
    "status" to JsonString(status.name),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
)

internal fun JsonObject.toSyncConflictRecord(): SyncConflictRecord = SyncConflictRecord(
    id = string("id"),
    entityType = EntityType.valueOf(string("entityType")),
    entityId = string("entityId"),
    localPayload = string("localPayload"),
    remotePayload = string("remotePayload"),
    status = ConflictStatus.valueOf(string("status")),
    createdAtEpochMs = long("createdAtEpochMs"),
)

internal fun UserAccount.toJson(): JsonObject = jsonObjectOf(
    "userId" to JsonString(userId),
    "username" to JsonString(username),
    "passwordHash" to JsonString(passwordHash),
    "mustChangeCredentials" to jsonBoolean(mustChangeCredentials),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "credentialsUpdatedAtEpochMs" to jsonLong(credentialsUpdatedAtEpochMs),
    "lastLoginAtEpochMs" to jsonLong(lastLoginAtEpochMs),
)

internal fun JsonObject.toUserAccount(): UserAccount = UserAccount(
    userId = string("userId"),
    username = string("username"),
    passwordHash = string("passwordHash"),
    mustChangeCredentials = boolean("mustChangeCredentials"),
    createdAtEpochMs = long("createdAtEpochMs"),
    updatedAtEpochMs = long("updatedAtEpochMs"),
    credentialsUpdatedAtEpochMs = long("credentialsUpdatedAtEpochMs"),
    lastLoginAtEpochMs = longOrNull("lastLoginAtEpochMs"),
)

internal fun UserPreferences.toJson(): JsonObject = jsonObjectOf(
    "userId" to JsonString(userId),
    "aiMode" to JsonString(aiMode.storageValue),
    "cloudEnhancementBaseUrl" to JsonString(cloudEnhancementBaseUrl),
    "localLightweightModelPackageId" to JsonString(localLightweightModelPackageId),
    "localGenerativeModelPackageId" to JsonString(localGenerativeModelPackageId),
    "modelDownloadPolicy" to JsonString(modelDownloadPolicy.name),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
)

internal fun JsonObject.toUserPreferences(): UserPreferences = UserPreferences(
    userId = string("userId"),
    aiMode = AiOperatingMode.fromStorage(string("aiMode")),
    cloudEnhancementBaseUrl = string("cloudEnhancementBaseUrl"),
    localLightweightModelPackageId = string("localLightweightModelPackageId"),
    localGenerativeModelPackageId = string("localGenerativeModelPackageId"),
    modelDownloadPolicy = ModelDownloadPolicy.fromStorage(string("modelDownloadPolicy")),
    updatedAtEpochMs = long("updatedAtEpochMs"),
)

internal fun ModelPackage.toJson(): JsonObject = jsonObjectOf(
    "packageId" to JsonString(packageId),
    "displayName" to JsonString(displayName),
    "kind" to JsonString(kind.name),
    "version" to JsonString(version.toString()),
    "installStatus" to JsonString(installStatus.name),
    "localPath" to jsonString(localPath),
    "downloadedBytes" to jsonLong(downloadedBytes),
    "totalBytes" to jsonLong(totalBytes),
    "isEnabled" to jsonBoolean(isEnabled),
    "downloadPolicy" to JsonString(downloadPolicy.name),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
)

internal fun JsonObject.toModelPackage(): ModelPackage = ModelPackage(
    packageId = string("packageId"),
    displayName = string("displayName"),
    kind = ModelPackageKind.entries.firstOrNull { it.name == string("kind") } ?: ModelPackageKind.LIGHTWEIGHT,
    version = ModelVersion.parse(string("version")),
    installStatus = ModelInstallStatus.fromStorage(string("installStatus")),
    localPath = stringOrNull("localPath"),
    downloadedBytes = long("downloadedBytes"),
    totalBytes = longOrNull("totalBytes"),
    isEnabled = boolean("isEnabled"),
    downloadPolicy = ModelDownloadPolicy.fromStorage(string("downloadPolicy")),
    updatedAtEpochMs = long("updatedAtEpochMs"),
)

internal fun AppSession.toJson(): JsonObject = jsonObjectOf(
    "userId" to JsonString(userId),
    "deviceId" to JsonString(deviceId),
    "deviceName" to JsonString(deviceName),
)

internal fun JsonObject.toAppSession(): AppSession = AppSession(
    userId = string("userId"),
    deviceId = string("deviceId"),
    deviceName = string("deviceName"),
)

internal fun SyncState.toJson(): JsonObject = jsonObjectOf(
    "pendingChanges" to jsonLong(pendingChanges),
    "lastSyncSeq" to jsonLong(lastSyncSeq),
)

internal fun encodeSyncableEntity(entity: SyncableEntity): String = stringifyJson(
    when (entity) {
        is DiaryEntry -> entity.toJson()
        is ScheduleEvent -> entity.toJson()
        is Tag -> entity.toJson()
        is DiaryEntryTag -> entity.toJson()
        is ChatSession -> entity.toJson()
        is ChatMessage -> entity.toJson()
        else -> error("Unsupported sync entity ${entity::class.simpleName}")
    },
)

internal fun decodeDiaryEntry(payload: String): DiaryEntry = parseJson(payload).asObject().toDiaryEntry()

internal fun decodeScheduleEvent(payload: String): ScheduleEvent = parseJson(payload).asObject().toScheduleEvent()

internal fun decodeTag(payload: String): Tag = parseJson(payload).asObject().toTag()

internal fun decodeDiaryEntryTag(payload: String): DiaryEntryTag = parseJson(payload).asObject().toDiaryEntryTag()

internal fun decodeChatSession(payload: String): ChatSession = parseJson(payload).asObject().toChatSession()

internal fun decodeChatMessage(payload: String): ChatMessage = parseJson(payload).asObject().toChatMessage()

private class JsonParser(
    private val source: String,
) {
    private var index: Int = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't', 'f' -> JsonBoolean(parseBoolean())
            'n' -> {
                consume("null")
                JsonNull
            }
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonObject {
        expect('{')
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonObject(emptyMap())
        }
        val properties = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            properties[key] = parseValue()
            skipWhitespace()
            when (val next = peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return JsonObject(properties)
                }
                else -> error("Unexpected token '$next' in object.")
            }
        }
    }

    private fun parseArray(): JsonArray {
        expect('[')
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonArray(emptyList())
        }
        val values = mutableListOf<JsonValue>()
        while (true) {
            skipWhitespace()
            values += parseValue()
            skipWhitespace()
            when (val next = peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    return JsonArray(values)
                }
                else -> error("Unexpected token '$next' in array.")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            when (val current = source[index++]) {
                '"' -> return result.toString()
                '\\' -> {
                    val escaped = source[index++]
                    result.append(
                        when (escaped) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                val hex = source.substring(index, index + 4)
                                index += 4
                                hex.toInt(16).toChar()
                            }
                            else -> error("Unsupported escape '$escaped'.")
                        },
                    )
                }
                else -> result.append(current)
            }
        }
        error("Unterminated string.")
    }

    private fun parseBoolean(): Boolean {
        return if (source.startsWith("true", index)) {
            index += 4
            true
        } else {
            consume("false")
            false
        }
    }

    private fun parseNumber(): JsonNumber {
        val start = index
        while (index < source.length && source[index] !in setOf(',', '}', ']', ' ', '\n', '\r', '\t')) {
            index++
        }
        return JsonNumber(source.substring(start, index))
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }

    private fun expect(expected: Char) {
        if (peek() != expected) {
            error("Expected '$expected' at $index.")
        }
        index++
    }

    private fun consume(expected: String) {
        if (!source.startsWith(expected, index)) {
            error("Expected \"$expected\" at $index.")
        }
        index += expected.length
    }

    private fun peek(): Char = source.getOrElse(index) { error("Unexpected end of JSON input.") }
}

private fun StringBuilder.appendJsonValue(value: JsonValue) {
    when (value) {
        is JsonObject -> {
            append('{')
            value.properties.entries.forEachIndexed { index, (key, property) ->
                if (index > 0) append(',')
                appendQuoted(key)
                append(':')
                appendJsonValue(property)
            }
            append('}')
        }
        is JsonArray -> {
            append('[')
            value.values.forEachIndexed { index, element ->
                if (index > 0) append(',')
                appendJsonValue(element)
            }
            append(']')
        }
        is JsonString -> appendQuoted(value.value)
        is JsonNumber -> append(value.raw)
        is JsonBoolean -> append(if (value.value) "true" else "false")
        JsonNull -> append("null")
    }
}

private fun StringBuilder.appendQuoted(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}
