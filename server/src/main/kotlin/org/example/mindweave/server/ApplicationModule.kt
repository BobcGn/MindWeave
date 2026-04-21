package org.example.mindweave.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.example.mindweave.domain.ai.AiChatRequest
import org.example.mindweave.server.service.ServerSyncService
import org.example.mindweave.server.service.createManagedServerSyncService
import org.example.mindweave.server.service.DeviceNotRegisteredException
import org.example.mindweave.server.service.InvalidSyncRequestException
import org.example.mindweave.server.service.ServerAiService
import org.example.mindweave.server.service.ServerErrorResponse
import org.example.mindweave.server.service.SyncConflictException
import org.example.mindweave.sync.DeviceRegistrationRequest
import org.example.mindweave.sync.SyncPullRequest
import org.example.mindweave.sync.SyncPushRequest
import org.example.mindweave.util.MindWeaveJson

fun Application.appModule(
    syncService: ServerSyncService? = null,
    aiService: ServerAiService = ServerAiService(),
) {
    val serviceName = environment.config.propertyOrNull("mindweave.service.name")?.getString()
        ?.trim()
        ?.ifEmpty { null }
        ?: "mindweave-server"
    val managedSyncService = syncService?.let { service ->
        org.example.mindweave.server.service.ManagedServerSyncService(service = service)
    } ?: createManagedServerSyncService(environment)

    monitor.subscribe(ApplicationStopped) {
        managedSyncService.close()
    }

    install(CallLogging)
    install(ContentNegotiation) {
        json(MindWeaveJson)
    }
    install(StatusPages) {
        exception<InvalidSyncRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ServerErrorResponse(
                    code = "INVALID_SYNC_REQUEST",
                    message = cause.message ?: "invalid sync request",
                ),
            )
        }
        exception<DeviceNotRegisteredException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ServerErrorResponse(
                    code = "DEVICE_NOT_REGISTERED",
                    message = cause.message ?: "device is not registered",
                ),
            )
        }
        exception<SyncConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ServerErrorResponse(
                    code = "SYNC_CONFLICT",
                    message = cause.message ?: "sync conflict",
                    conflicts = cause.conflicts,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ServerErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = cause.message ?: "unknown error",
                ),
            )
        }
    }

    routing {
        get("/health") {
            call.respond(
                mapOf(
                    "status" to "ok",
                    "service" to serviceName,
                ),
            )
        }
        post("/devices/register") {
            val request = call.receive<DeviceRegistrationRequest>()
            call.respond(managedSyncService.service.registerDevice(request))
        }
        post("/sync/push") {
            val request = call.receive<SyncPushRequest>()
            call.respond(managedSyncService.service.push(request))
        }
        post("/sync/pull") {
            val request = call.receive<SyncPullRequest>()
            call.respond(managedSyncService.service.pull(request))
        }
        post("/ai/chat") {
            val request = call.receive<AiChatRequest>()
            call.respond(aiService.chat(request))
        }
    }
}
