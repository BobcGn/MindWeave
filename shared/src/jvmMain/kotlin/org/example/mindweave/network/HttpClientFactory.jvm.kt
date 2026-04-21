package org.example.mindweave.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.example.mindweave.util.MindWeaveJson

private val sharedHttpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(MindWeaveJson)
        }
    }
}

actual fun createMindWeaveHttpClient(): HttpClient = sharedHttpClient
