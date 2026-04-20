package org.example.mindweave.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.platform.PlatformContext

actual class DriverFactory actual constructor(
    platformContext: PlatformContext,
) {
    actual fun createDriver(): SqlDriver =
        JdbcSqliteDriver(
            url = "jdbc:sqlite:mindweave.db",
            properties = Properties(),
            schema = MindWeaveDatabase.Schema,
        )
}
