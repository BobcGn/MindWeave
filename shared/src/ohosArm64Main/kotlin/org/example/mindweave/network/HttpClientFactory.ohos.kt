package org.example.mindweave.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import org.example.mindweave.util.MindWeaveJson

private val sharedHttpClient: HttpClient by lazy {
    HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = """
                        {
                          "message":"Harmony bridge 当前只启用本地优先能力，云增强尚未接入 HTTP engine。",
                          "url":"${request.url}"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.NotImplemented,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        install(ContentNegotiation) {
            json(MindWeaveJson)
        }
    }
}

actual fun createMindWeaveHttpClient(): HttpClient = sharedHttpClient
