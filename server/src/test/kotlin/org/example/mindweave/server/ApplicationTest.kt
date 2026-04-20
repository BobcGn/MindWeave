package org.example.mindweave.server

import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryMood
import org.example.mindweave.server.service.ServerErrorResponse
import org.example.mindweave.sync.SyncPullResponse
import org.example.mindweave.sync.SyncPushResponse
import org.example.mindweave.util.MindWeaveJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthEndpointShouldRespond() = testApplication {
        application {
            appModule()
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("mindweave-server"))
    }

    @Test
    fun syncEndpointsShouldAcceptPushAndPull() = testApplication {
        application {
            appModule()
        }

        val register = client.post("/devices/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"user-1","deviceId":"device-a","deviceName":"Phone"}""")
        }
        assertEquals(HttpStatusCode.OK, register.status)

        val registerPeer = client.post("/devices/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"user-1","deviceId":"device-b","deviceName":"Desktop"}""")
        }
        assertEquals(HttpStatusCode.OK, registerPeer.status)

        val push = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "userId":"user-1",
                  "deviceId":"device-a",
                  "changes":[
                    ${diaryChangeJson(deviceId = "device-a", version = 1, updatedAtEpochMs = 1)}
                  ]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, push.status)
        val pushResponse = MindWeaveJson.decodeFromString<SyncPushResponse>(push.bodyAsText())
        assertEquals(1, pushResponse.acceptedCount)
        assertEquals(1, pushResponse.latestSeq)

        val pull = client.post("/sync/pull") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"user-1","deviceId":"device-b","afterSeq":0}""")
        }
        assertEquals(HttpStatusCode.OK, pull.status)
        val pullResponse = MindWeaveJson.decodeFromString<SyncPullResponse>(pull.bodyAsText())
        assertEquals(1, pullResponse.changes.size)
        assertEquals("diary-1", pullResponse.changes.single().entityId)
    }

    @Test
    fun syncPushShouldRejectUnregisteredDevice() = testApplication {
        application {
            appModule()
        }

        val push = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "userId":"user-1",
                  "deviceId":"device-a",
                  "changes":[
                    ${diaryChangeJson(deviceId = "device-a", version = 1, updatedAtEpochMs = 1)}
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Forbidden, push.status)
        val error = MindWeaveJson.decodeFromString<ServerErrorResponse>(push.bodyAsText())
        assertEquals("DEVICE_NOT_REGISTERED", error.code)
    }

    @Test
    fun syncPushShouldReturnConflictForStaleChanges() = testApplication {
        application {
            appModule()
        }

        client.post("/devices/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"user-1","deviceId":"device-a","deviceName":"Phone"}""")
        }
        client.post("/devices/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId":"user-1","deviceId":"device-b","deviceName":"Desktop"}""")
        }

        client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "userId":"user-1",
                  "deviceId":"device-a",
                  "changes":[
                    ${diaryChangeJson(deviceId = "device-a", version = 1, updatedAtEpochMs = 1)}
                  ]
                }
                """.trimIndent(),
            )
        }
        client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "userId":"user-1",
                  "deviceId":"device-b",
                  "changes":[
                    ${diaryChangeJson(deviceId = "device-b", version = 2, updatedAtEpochMs = 2, content = "newer")}
                  ]
                }
                """.trimIndent(),
            )
        }

        val stalePush = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "userId":"user-1",
                  "deviceId":"device-a",
                  "changes":[
                    ${diaryChangeJson(deviceId = "device-a", version = 1, updatedAtEpochMs = 1, content = "stale")}
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Conflict, stalePush.status)
        val error = MindWeaveJson.decodeFromString<ServerErrorResponse>(stalePush.bodyAsText())
        assertEquals("SYNC_CONFLICT", error.code)
        assertEquals(1, error.conflicts.size)
    }
}

private fun diaryChangeJson(
    deviceId: String,
    version: Long,
    updatedAtEpochMs: Long,
    content: String = "entry",
): String {
    val payload = MindWeaveJson.encodeToString(
        DiaryEntry(
            id = "diary-1",
            userId = "user-1",
            title = "Title",
            content = content,
            mood = DiaryMood.CALM,
            aiSummary = null,
            createdAtEpochMs = 1,
            updatedAtEpochMs = updatedAtEpochMs,
            deletedAtEpochMs = null,
            version = version,
            lastModifiedByDeviceId = deviceId,
        ),
    )
    return """
        {
          "entityType":"DIARY_ENTRY",
          "entityId":"diary-1",
          "operation":"UPSERT",
          "payload":${MindWeaveJson.encodeToString(payload)},
          "createdAtEpochMs":$updatedAtEpochMs,
          "deviceId":"$deviceId"
        }
    """.trimIndent()
}
