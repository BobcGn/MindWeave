package org.example.mindweave.data.local

import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.platform.PlatformContext

actual fun createPlatformLocalRepositories(
    platformContext: PlatformContext,
    session: AppSession,
): LocalRepositories = createOhosLocalRepositories(
    platformContext = platformContext,
    session = session,
)
