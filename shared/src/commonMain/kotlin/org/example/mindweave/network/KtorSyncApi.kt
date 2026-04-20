package org.example.mindweave.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.DeviceRegistrationResponse
import org.example.mindweave.sync.SyncApi
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPullResponse
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.sync.SyncPushResponse

class KtorSyncApi(
    private val baseUrl: String,
    private val client: HttpClient,
) : SyncApi {
    override suspend fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse =
        client.post("$baseUrl/devices/register") {
            setBody(request)
        }.body()

    override suspend fun push(request: SyncPushRequest): SyncPushResponse =
        client.post("$baseUrl/sync/push") {
            setBody(request)
        }.body()

    override suspend fun pull(request: SyncPullRequest): SyncPullResponse =
        client.post("$baseUrl/sync/pull") {
            setBody(request)
        }.body()
}
