package org.example.mindweave.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.platform.PlatformContext

actual class DriverFactory actual constructor(
    private val platformContext: PlatformContext,
) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = MindWeaveDatabase.Schema,
            context = platformContext.context,
            name = "mindweave.db",
        )
}
