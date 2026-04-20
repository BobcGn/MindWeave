package org.example.mindweave.server.service

import io.ktor.server.application.ApplicationEnvironment
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
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): PostgresSyncDatabaseConfig? {
            val jdbcUrl = environment["MINDWEAVE_POSTGRES_URL"]?.trim().orEmpty()
            if (jdbcUrl.isEmpty()) {
                return null
            }

            return PostgresSyncDatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = environment["MINDWEAVE_POSTGRES_USER"]?.trim()?.ifEmpty { null },
                password = environment["MINDWEAVE_POSTGRES_PASSWORD"]?.trim()?.ifEmpty { null },
            )
        }
    }
}

fun createManagedServerSyncService(environment: ApplicationEnvironment): ManagedServerSyncService {
    val config = PostgresSyncDatabaseConfig.fromEnvironment()
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
