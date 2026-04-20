package org.example.mindweave.app

import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.ChatContextAssembler
import org.example.mindweave.ai.createAiAgent
import org.example.mindweave.data.local.createLocalRepositories
import org.example.mindweave.database.DriverFactory
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.platform.PlatformContext
import org.example.mindweave.sync.InMemorySyncApi
import org.example.mindweave.sync.LocalChangeApplier
import org.example.mindweave.sync.SyncApi
import org.example.mindweave.sync.SyncManager

data class MindWeaveAppGraph(
    val session: AppSession,
    val facade: MindWeaveFacade,
)

fun createMindWeaveAppGraph(
    platformContext: PlatformContext,
    aiSettings: AiSettings = AiSettings.Disabled,
    syncApi: SyncApi = InMemorySyncApi(),
): MindWeaveAppGraph {
    val session = AppSession(
        userId = "local-user",
        deviceId = "local-device",
        deviceName = "MindWeave Device",
    )
    val database = MindWeaveDatabase(DriverFactory(platformContext).createDriver())
    val repositories = createLocalRepositories(database, session)
    val aiAgent = createAiAgent(aiSettings)
    val contextAssembler = ChatContextAssembler(
        diaryRepository = repositories.diaryRepository,
        scheduleRepository = repositories.scheduleRepository,
        chatRepository = repositories.chatRepository,
    )
    val syncManager = SyncManager(
        syncApi = syncApi,
        syncRepository = repositories.syncRepository,
        localChangeApplier = LocalChangeApplier(
            diaryRepository = repositories.diaryRepository,
            scheduleRepository = repositories.scheduleRepository,
            tagRepository = repositories.tagRepository,
            chatRepository = repositories.chatRepository,
            syncRepository = repositories.syncRepository,
        ),
    )
    return MindWeaveAppGraph(
        session = session,
        facade = MindWeaveFacade(
            session = session,
            diaryRepository = repositories.diaryRepository,
            scheduleRepository = repositories.scheduleRepository,
            tagRepository = repositories.tagRepository,
            chatRepository = repositories.chatRepository,
            syncRepository = repositories.syncRepository,
            aiAgent = aiAgent,
            contextAssembler = contextAssembler,
            syncManager = syncManager,
        ),
    )
}
