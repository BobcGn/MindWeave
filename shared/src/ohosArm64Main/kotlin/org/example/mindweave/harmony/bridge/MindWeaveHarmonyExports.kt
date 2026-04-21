package org.example.mindweave.harmony.bridge

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.getPointer
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlin.native.CName
import kotlin.native.concurrent.ThreadLocal
import org.example.mindweave.platform.PlatformContext
import org.example.mindweave.util.MindWeaveJson

@ThreadLocal
private object HarmonyBridgeRegistry {
    var controller: MindWeaveHarmonyBridgeController? = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_bootstrap")
fun mindweaveBridgeBootstrap(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    respond {
        val request = decodeRequest<HarmonyBridgeInitRequest>(requestJson)
        val controller = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = request.databaseName),
            initRequest = request,
        )
        HarmonyBridgeRegistry.controller = controller
        runBlocking {
            MindWeaveJson.encodeToString(controller.bootstrap(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_get_snapshot")
fun mindweaveBridgeGetSnapshot(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeRequest<HarmonyBridgeSnapshotRequest>(requestJson)
            MindWeaveJson.encodeToString(controller.snapshot(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_capture_diary")
fun mindweaveBridgeCaptureDiary(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeRequest<HarmonyBridgeDiaryDraft>(requestJson)
            MindWeaveJson.encodeToString(controller.captureDiary(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_capture_schedule")
fun mindweaveBridgeCaptureSchedule(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeRequest<HarmonyBridgeScheduleDraft>(requestJson)
            MindWeaveJson.encodeToString(controller.captureSchedule(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_send_chat_message")
fun mindweaveBridgeSendChatMessage(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeRequest<HarmonyBridgeChatRequest>(requestJson)
            MindWeaveJson.encodeToString(controller.sendChatMessage(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_run_sync")
fun mindweaveBridgeRunSync(): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            MindWeaveJson.encodeToString(controller.runSync())
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_save_preferences")
fun mindweaveBridgeSavePreferences(requestJson: CPointer<ByteVar>?): CPointer<ByteVar>? =
    withController { controller ->
        runBlocking {
            val request = decodeRequest<HarmonyBridgePreferenceRequest>(requestJson)
            MindWeaveJson.encodeToString(controller.savePreferences(request))
        }
    }

@OptIn(ExperimentalForeignApi::class)
@CName("mindweave_bridge_dispose_string")
fun mindweaveBridgeDisposeString(value: CPointer<ByteVar>?) {
    if (value != null) {
        nativeHeap.free(value)
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
    MindWeaveJson.encodeToString(
        HarmonyBridgeResponse(
            ok = false,
            message = throwable.message ?: "Harmony bridge 未知错误。",
        ),
    ).toNativeString()
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <reified T> decodeRequest(requestJson: CPointer<ByteVar>?): T {
    val payload = requestJson?.toKString()?.takeIf(String::isNotBlank) ?: "{}"
    return MindWeaveJson.decodeFromString(payload)
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNativeString(): CPointer<ByteVar> = cstr.getPointer(nativeHeap)
