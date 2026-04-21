package org.example.mindweave.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.example.mindweave.server.service.PostgresSyncDatabaseConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerConfigTest {
    @Test
    fun healthEndpointShouldReadServiceNameFromConfig() = testApplication {
        environment {
            config = MapApplicationConfig(
                "mindweave.service.name" to "mindweave-test-service",
            )
        }
        application {
            appModule()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("mindweave-test-service"))
    }

    @Test
    fun postgresConfigShouldLoadFromApplicationConfig() {
        val config = MapApplicationConfig(
            "mindweave.sync.postgres.jdbcUrl" to "jdbc:postgresql://localhost:5432/mindweave",
            "mindweave.sync.postgres.username" to "mindweave",
            "mindweave.sync.postgres.password" to "secret",
        )

        val parsed = PostgresSyncDatabaseConfig.fromConfig(config)

        assertNotNull(parsed)
        assertEquals("jdbc:postgresql://localhost:5432/mindweave", parsed.jdbcUrl)
        assertEquals("mindweave", parsed.username)
        assertEquals("secret", parsed.password)
    }
}
