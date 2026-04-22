package org.example.mindweave.data.local

import org.example.mindweave.database.DriverFactory
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.platform.PlatformContext

actual fun createPlatformLocalRepositories(
    platformContext: PlatformContext,
    session: AppSession,
): LocalRepositories {
    val database = MindWeaveDatabase(DriverFactory(platformContext).createDriver())
    return createLocalRepositories(database, session)
}
