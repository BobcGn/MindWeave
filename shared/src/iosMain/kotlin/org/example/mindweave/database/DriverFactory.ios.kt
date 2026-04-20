package org.example.mindweave.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.platform.PlatformContext

actual class DriverFactory actual constructor(
    platformContext: PlatformContext,
) {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(MindWeaveDatabase.Schema, "mindweave.db")
}
