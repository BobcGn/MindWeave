@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.example.mindweave.harmony.bridge

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlin.native.CName
import kotlin.native.concurrent.ThreadLocal
import org.example.mindweave.platform.PlatformContext
import org.example.mindweave.util.JsonNull
import org.example.mindweave.util.JsonObject
import org.example.mindweave.util.JsonString
import org.example.mindweave.util.asObject
import org.example.mindweave.util.asLong
import org.example.mindweave.util.asString
import org.example.mindweave.util.jsonArrayOf
import org.example.mindweave.util.jsonBoolean
import org.example.mindweave.util.jsonLong
import org.example.mindweave.util.jsonObjectOf
import org.example.mindweave.util.jsonString
import org.example.mindweave.util.longOrNull
import org.example.mindweave.util.parseJson
import org.example.mindweave.util.string
import org.example.mindweave.util.stringList
import org.example.mindweave.util.stringOrNull
import org.example.mindweave.util.stringifyJson
import org.example.mindweave.util.toJson
import platform.posix.free
import platform.posix.strdup

@ThreadLocal
private object HarmonyBridgeRegistry {
    var controller: MindWeaveHarmonyBridgeController? = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_bootstrap")
fun mindweaveBridgeBootstrap(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    respond {
        val request = decodeInitRequest(requestJson)
        val controller = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = request.databaseName),
            initRequest = request,
        )
        HarmonyBridgeRegistry.controller = controller
        runBlocking {
            encodeResponse(controller.bootstrap(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_get_snapshot")
fun mindweaveBridgeGetSnapshot(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeSnapshotRequest(requestJson)
            encodeResponse(controller.snapshot(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_capture_diary")
fun mindweaveBridgeCaptureDiary(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeDiaryDraftRequest(requestJson)
            encodeResponse(controller.captureDiary(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_capture_schedule")
fun mindweaveBridgeCaptureSchedule(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeScheduleDraftRequest(requestJson)
            encodeResponse(controller.captureSchedule(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_send_chat_message")
fun mindweaveBridgeSendChatMessage(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeChatRequest(requestJson)
            encodeResponse(controller.sendChatMessage(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_run_sync")
fun mindweaveBridgeRunSync(): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            encodeResponse(controller.runSync())
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_save_preferences")
fun mindweaveBridgeSavePreferences(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodePreferenceRequest(requestJson)
            encodeResponse(controller.savePreferences(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_authenticate")
fun mindweaveBridgeAuthenticate(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeAuthenticateRequest(requestJson)
            encodeResponse(controller.authenticate(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_force_reset_credentials")
fun mindweaveBridgeForceResetCredentials(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeCredentialResetRequest(requestJson)
            encodeResponse(controller.forceResetCredentials(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_change_credentials")
fun mindweaveBridgeChangeCredentials(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeChangeCredentialsRequest(requestJson)
            encodeResponse(controller.changeCredentials(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_dispose_string")
fun mindweaveBridgeDisposeString(value: CPointer<ByteVar>?) {
    if (value != null) {
        free(value)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun withController(
    block: (MindWeaveHarmonyBridgeController) -> String,
): CPointer<ByteVar>? = respond {
    val controller = HarmonyBridgeRegistry.controller ?: error("Harmony bridge 尚未 bootstrap。")
    block(controller)
}

@OptIn(ExperimentalForeignApi::class)
private inline fun respond(block: () -> String): CPointer<ByteVar>? = try {
    block().toNativeString()
} catch (throwable: Throwable) {
    encodeResponse(
        HarmonyBridgeResponse(
            ok = false,
            message = throwable.message ?: "Harmony bridge 未知错误。",
        ),
    ).toNativeString()
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNativeString(): CPointer<ByteVar> =
    strdup(this) ?: error("Unable to allocate native string.")

@OptIn(ExperimentalForeignApi::class)
private fun decodeInitRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeInitRequest {
    val payload = requestObject(requestJson)
    val defaults = HarmonyBridgeInitRequest()
    return HarmonyBridgeInitRequest(
        databaseName = payload.string("databaseName", defaults.databaseName),
        userId = payload.string("userId", defaults.userId),
        deviceId = payload.string("deviceId", defaults.deviceId),
        deviceName = payload.string("deviceName", defaults.deviceName),
        aiMode = payload.string("aiMode", defaults.aiMode),
        cloudEnhancementBaseUrl = payload.string("cloudEnhancementBaseUrl", defaults.cloudEnhancementBaseUrl),
        localLightweightModelPackageId = payload.string(
            "localLightweightModelPackageId",
            defaults.localLightweightModelPackageId,
        ),
        localGenerativeModelPackageId = payload.string(
            "localGenerativeModelPackageId",
            defaults.localGenerativeModelPackageId,
        ),
        modelDownloadPolicy = payload.string("modelDownloadPolicy", defaults.modelDownloadPolicy),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeSnapshotRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeSnapshotRequest =
    HarmonyBridgeSnapshotRequest(
        selectedSessionId = requestObject(requestJson).stringOrNull("selectedSessionId"),
    )

@OptIn(ExperimentalForeignApi::class)
private fun decodeDiaryDraftRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeDiaryDraft {
    val payload = requestObject(requestJson)
    return HarmonyBridgeDiaryDraft(
        title = payload.string("title"),
        content = payload.requiredString("content"),
        mood = payload.string("mood", "CALM"),
        tags = payload.stringList("tags"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeScheduleDraftRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeScheduleDraft {
    val payload = requestObject(requestJson)
    return HarmonyBridgeScheduleDraft(
        title = payload.requiredString("title"),
        description = payload.string("description"),
        startTimeEpochMs = payload.requiredLong("startTimeEpochMs"),
        endTimeEpochMs = payload.requiredLong("endTimeEpochMs"),
        remindAtEpochMs = payload.longOrNull("remindAtEpochMs"),
        type = payload.string("type", "WORK"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeChatRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeChatRequest {
    val payload = requestObject(requestJson)
    return HarmonyBridgeChatRequest(
        existingSessionId = payload.stringOrNull("existingSessionId"),
        prompt = payload.requiredString("prompt"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodePreferenceRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgePreferenceRequest {
    val payload = requestObject(requestJson)
    val defaults = HarmonyBridgePreferenceRequest()
    return HarmonyBridgePreferenceRequest(
        aiMode = payload.string("aiMode", defaults.aiMode),
        cloudEnhancementBaseUrl = payload.string("cloudEnhancementBaseUrl", defaults.cloudEnhancementBaseUrl),
        localLightweightModelPackageId = payload.string(
            "localLightweightModelPackageId",
            defaults.localLightweightModelPackageId,
        ),
        localGenerativeModelPackageId = payload.string(
            "localGenerativeModelPackageId",
            defaults.localGenerativeModelPackageId,
        ),
        modelDownloadPolicy = payload.string("modelDownloadPolicy", defaults.modelDownloadPolicy),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeAuthenticateRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeAuthenticateRequest {
    val payload = requestObject(requestJson)
    return HarmonyBridgeAuthenticateRequest(
        username = payload.requiredString("username"),
        password = payload.requiredString("password"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeCredentialResetRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeCredentialResetRequest {
    val payload = requestObject(requestJson)
    return HarmonyBridgeCredentialResetRequest(
        newUsername = payload.requiredString("newUsername"),
        newPassword = payload.requiredString("newPassword"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun decodeChangeCredentialsRequest(requestJson: CPointer<ByteVar>?): HarmonyBridgeChangeCredentialsRequest {
    val payload = requestObject(requestJson)
    return HarmonyBridgeChangeCredentialsRequest(
        currentPassword = payload.requiredString("currentPassword"),
        newUsername = payload.requiredString("newUsername"),
        newPassword = payload.requiredString("newPassword"),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun requestObject(requestJson: CPointer<ByteVar>?): JsonObject {
    val payload = requestJson?.toKString()?.takeIf(String::isNotBlank) ?: "{}"
    return parseJson(payload).asObject()
}

private fun encodeResponse(response: HarmonyBridgeResponse): String = stringifyJson(response.toJson())

private fun HarmonyBridgeResponse.toJson(): JsonObject = jsonObjectOf(
    "ok" to jsonBoolean(ok),
    "message" to JsonString(message),
    "focusSessionId" to jsonString(focusSessionId),
    "snapshot" to (snapshot?.toJson() ?: JsonNull),
)

private fun HarmonyBridgeSnapshot.toJson(): JsonObject = jsonObjectOf(
    "platformName" to JsonString(platformName),
    "session" to session.toJson(),
    "account" to (account?.toJson() ?: JsonNull),
    "preferences" to (preferences?.toJson() ?: JsonNull),
    "timeline" to jsonArrayOf(timeline.map { it.toJson() }),
    "schedules" to jsonArrayOf(schedules.map { it.toJson() }),
    "chatSessions" to jsonArrayOf(chatSessions.map { it.toJson() }),
    "conversation" to jsonArrayOf(conversation.map { it.toJson() }),
    "syncState" to syncState.toJson(),
)

private fun HarmonyBridgeAccountState.toJson(): JsonObject = jsonObjectOf(
    "username" to JsonString(username),
    "mustChangeCredentials" to jsonBoolean(mustChangeCredentials),
    "createdAtEpochMs" to jsonLong(createdAtEpochMs),
    "updatedAtEpochMs" to jsonLong(updatedAtEpochMs),
    "credentialsUpdatedAtEpochMs" to jsonLong(credentialsUpdatedAtEpochMs),
    "lastLoginAtEpochMs" to jsonLong(lastLoginAtEpochMs),
)

private fun HarmonyBridgePreferenceState.toJson(): JsonObject = jsonObjectOf(
    "aiMode" to JsonString(aiMode),
    "aiModeLabel" to JsonString(aiModeLabel),
    "cloudEnhancementBaseUrl" to JsonString(cloudEnhancementBaseUrl),
    "localLightweightModelPackageId" to JsonString(localLightweightModelPackageId),
    "localGenerativeModelPackageId" to JsonString(localGenerativeModelPackageId),
    "modelDownloadPolicy" to JsonString(modelDownloadPolicy),
    "modelDownloadPolicyLabel" to JsonString(modelDownloadPolicyLabel),
)

private fun JsonObject.requiredString(name: String): String =
    properties[name]?.takeUnless { it == JsonNull }?.asString() ?: error("Missing field '$name'.")

private fun JsonObject.requiredLong(name: String): Long =
    properties[name]?.takeUnless { it == JsonNull }?.asLong() ?: error("Missing field '$name'.")
