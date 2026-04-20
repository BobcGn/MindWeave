package org.example.mindweave.sync

interface SyncApi {
    suspend fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse

    suspend fun push(request: SyncPushRequest): SyncPushResponse

    suspend fun pull(request: SyncPullRequest): SyncPullResponse
}
