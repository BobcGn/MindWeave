package org.example.mindweave.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.DeviceRegistrationResponse
import org.example.mindweave.sync.DeviceNotRegisteredSyncException
import org.example.mindweave.sync.InvalidSyncRequestSyncException
import org.example.mindweave.sync.SyncApi
import org.example.mindweave.sync.SyncApiErrorResponse
import org.example.mindweave.sync.SyncApiException
import org.example.mindweave.sync.SyncConflictApiException
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPullResponse
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.sync.SyncPushResponse
import org.example.mindweave.util.MindWeaveJson

class KtorSyncApi(
    private val baseUrl: String,
    private val client: HttpClient,
) : SyncApi {
    override suspend fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse =
        postJson("$baseUrl/devices/register", request)

    override suspend fun push(request: SyncPushRequest): SyncPushResponse =
        postJson("$baseUrl/sync/push", request)

    override suspend fun pull(request: SyncPullRequest): SyncPullResponse =
        postJson("$baseUrl/sync/pull", request)

    private suspend inline fun <reified T> postJson(
        url: String,
        payload: Any,
    ): T {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (response.status.value in 200..299) {
            return response.body()
        }

        val errorBody = response.bodyAsText()
        val parsedError = runCatching {
            MindWeaveJson.decodeFromString<SyncApiErrorResponse>(errorBody)
        }.getOrNull()
        val message = parsedError?.message?.ifBlank { null }
            ?: "Sync request failed with status ${response.status.value}."

        throw when (response.status) {
            HttpStatusCode.BadRequest -> InvalidSyncRequestSyncException(message)
            HttpStatusCode.Forbidden -> DeviceNotRegisteredSyncException(message)
            HttpStatusCode.Conflict -> SyncConflictApiException(
                conflicts = parsedError?.conflicts.orEmpty(),
                message = message,
            )
            else -> SyncApiException(
                code = parsedError?.code ?: "HTTP_${response.status.value}",
                message = message,
            )
        }
    }
}
