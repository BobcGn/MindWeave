package org.example.mindweave.server.service

import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.config.ApplicationConfig
import java.net.URI

data class ManagedServerSyncService(
    val service: ServerSyncService,
    val close: () -> Unit = {},
)

data class PostgresSyncDatabaseConfig(
    val jdbcUrl: String,
    val username: String?,
    val password: String?,
) {
    fun redactedJdbcUrl(): String = runCatching {
        val uri = URI(jdbcUrl.removePrefix("jdbc:"))
        URI(
            uri.scheme,
            null,
            uri.host,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        ).toString().let { "jdbc:$it" }
    }.getOrElse {
        jdbcUrl.substringBefore('?')
    }

    companion object {
        fun fromConfig(config: ApplicationConfig): PostgresSyncDatabaseConfig? {
            val jdbcUrl = config.propertyOrNull("mindweave.sync.postgres.jdbcUrl")
                ?.getString()
                ?.trim()
                .orEmpty()
            if (jdbcUrl.isEmpty()) {
                return null
            }

            return PostgresSyncDatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = config.propertyOrNull("mindweave.sync.postgres.username")
                    ?.getString()
                    ?.trim()
                    ?.ifEmpty { null },
                password = config.propertyOrNull("mindweave.sync.postgres.password")
                    ?.getString()
                    ?.trim()
                    ?.ifEmpty { null },
            )
        }
    }
}

fun createManagedServerSyncService(environment: ApplicationEnvironment): ManagedServerSyncService {
    val config = PostgresSyncDatabaseConfig.fromConfig(environment.config)
    if (config == null) {
        environment.log.info("MindWeave sync service is using in-memory persistence.")
        return ManagedServerSyncService(
            service = InMemorySyncService(),
        )
    }

    val repository = PostgresServerSyncRepository(config)
    repository.inTransaction { }
    environment.log.info("MindWeave sync service is using PostgreSQL: ${config.redactedJdbcUrl()}")
    return ManagedServerSyncService(
        service = InMemorySyncService(repository = repository),
        close = repository::close,
    )
}
