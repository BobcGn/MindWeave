package org.example.mindweave.harmony.bridge

import kotlinx.coroutines.test.runTest
import org.example.mindweave.platform.PlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MindWeaveHarmonyBridgeTest {
    @Test
    fun bootstrapShouldSeedSnapshotAndSupportDiaryAndChatCommands() = runTest {
        val databaseName = "/tmp/mindweave-harmony-bridge-test.db"
        val initRequest = HarmonyBridgeInitRequest(
            databaseName = databaseName,
            deviceId = "harmony-test-device",
            deviceName = "Harmony Test Device",
            enableDemoData = true,
        )
        val controller = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = databaseName),
            initRequest = initRequest,
        )

        val bootstrapResponse = controller.bootstrap(initRequest)
        assertTrue(bootstrapResponse.ok)
        val bootstrapSnapshot = assertNotNull(bootstrapResponse.snapshot)
        assertTrue(bootstrapSnapshot.timeline.isNotEmpty())

        val diaryResponse = controller.captureDiary(
            HarmonyBridgeDiaryDraft(
                title = "Harmony diary",
                content = "通过 ArkUI 写入一条日记。",
                tags = listOf("Harmony", "Bridge"),
            ),
        )
        assertTrue(diaryResponse.ok)
        assertTrue(
            diaryResponse.snapshot?.timeline?.any { it.entry.title == "Harmony diary" } == true,
        )

        val chatResponse = controller.sendChatMessage(
            HarmonyBridgeChatRequest(prompt = "帮我总结一下 Harmony 侧接入的价值。"),
        )
        assertTrue(chatResponse.ok)
        assertEquals(
            chatResponse.focusSessionId,
            chatResponse.snapshot?.chatSessions?.firstOrNull()?.id ?: chatResponse.focusSessionId,
        )
        assertTrue(chatResponse.snapshot?.conversation?.isNotEmpty() == true)
    }
}
