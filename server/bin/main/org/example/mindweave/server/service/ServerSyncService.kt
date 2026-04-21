package org.example.mindweave.server.service

import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.DeviceRegistrationResponse
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPullResponse
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.sync.SyncPushResponse

interface ServerSyncService {
    fun registerDevice(request: DeviceRegistrationRequest): DeviceRegistrationResponse

    fun push(request: SyncPushRequest): SyncPushResponse

    fun pull(request: SyncPullRequest): SyncPullResponse
}
