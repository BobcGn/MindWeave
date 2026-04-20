package org.example.mindweave.database

import app.cash.sqldelight.db.SqlDriver
import org.example.mindweave.platform.PlatformContext

expect class DriverFactory(platformContext: PlatformContext) {
    fun createDriver(): SqlDriver
}
