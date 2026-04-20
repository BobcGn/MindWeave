package org.example.mindweave

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.example.mindweave.server.appModule

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        appModule()
    }
        .start(wait = true)
}
