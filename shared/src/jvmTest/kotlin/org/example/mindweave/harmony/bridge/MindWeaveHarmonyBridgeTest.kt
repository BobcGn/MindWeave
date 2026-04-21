package org.example.mindweave.harmony.bridge

import java.io.File
import kotlinx.coroutines.test.runTest
import org.example.mindweave.platform.PlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MindWeaveHarmonyBridgeTest {
    @Test
    fun bootstrapShouldStartFromRealEmptySnapshotAndSupportDiaryAndChatCommands() = runTest {
        val databaseFile = File("/tmp/mindweave-harmony-bridge-test.db")
        databaseFile.delete()
        val initRequest = HarmonyBridgeInitRequest(
            databaseName = databaseFile.absolutePath,
            deviceId = "harmony-test-device",
            deviceName = "Harmony Test Device",
        )
        val controller = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = databaseFile.absolutePath),
            initRequest = initRequest,
        )

        val bootstrapResponse = controller.bootstrap(initRequest)
        assertTrue(bootstrapResponse.ok)
        val bootstrapSnapshot = assertNotNull(bootstrapResponse.snapshot)
        assertTrue(bootstrapSnapshot.timeline.isEmpty())
        assertTrue(bootstrapSnapshot.schedules.isEmpty())
        assertTrue(bootstrapSnapshot.chatSessions.isEmpty())
        assertTrue(bootstrapSnapshot.conversation.isEmpty())
        assertTrue(bootstrapSnapshot.account?.mustChangeCredentials == true)

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

        databaseFile.delete()
    }

    @Test
    fun credentialChangesShouldPersistAcrossBridgeRebootstrap() = runTest {
        val databaseFile = File("/tmp/mindweave-harmony-bridge-persistence-test.db")
        databaseFile.delete()
        val databaseName = databaseFile.absolutePath
        val initRequest = HarmonyBridgeInitRequest(
            databaseName = databaseName,
            deviceId = "harmony-test-device",
            deviceName = "Harmony Test Device",
        )

        val firstController = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = databaseName),
            initRequest = initRequest,
        )
        assertTrue(firstController.bootstrap(initRequest).ok)

        val resetResponse = firstController.forceResetCredentials(
            HarmonyBridgeCredentialResetRequest(
                newUsername = "owner",
                newPassword = "owner-pass",
            ),
        )
        assertTrue(resetResponse.ok)
        assertEquals("owner", resetResponse.snapshot?.account?.username)
        assertFalse(resetResponse.snapshot?.account?.mustChangeCredentials ?: true)

        val secondController = createMindWeaveHarmonyBridgeController(
            platformContext = PlatformContext(databaseName = databaseName),
            initRequest = initRequest,
        )
        val secondBootstrap = secondController.bootstrap(initRequest)
        assertTrue(secondBootstrap.ok)
        assertEquals("owner", secondBootstrap.snapshot?.account?.username)
        assertFalse(secondBootstrap.snapshot?.account?.mustChangeCredentials ?: true)

        val loginResponse = secondController.authenticate(
            HarmonyBridgeAuthenticateRequest(
                username = "owner",
                password = "owner-pass",
            ),
        )
        assertTrue(loginResponse.ok)
        assertEquals("owner", loginResponse.snapshot?.account?.username)

        databaseFile.delete()
    }
}
