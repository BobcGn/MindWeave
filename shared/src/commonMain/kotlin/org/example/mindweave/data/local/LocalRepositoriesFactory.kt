package org.example.mindweave.data.local

import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.platform.PlatformContext

expect fun createPlatformLocalRepositories(
    platformContext: PlatformContext,
    session: AppSession,
): LocalRepositories
