package org.example.mindweave.data.local

import org.example.mindweave.domain.model.AppSession
import org.example.mindweave.repository.AccountRepository
import org.example.mindweave.repository.ChatRepository
import org.example.mindweave.repository.DiaryRepository
import org.example.mindweave.repository.ModelPackageRepository
import org.example.mindweave.repository.ScheduleRepository
import org.example.mindweave.repository.SyncRepository
import org.example.mindweave.repository.TagRepository
import org.example.mindweave.repository.UserPreferencesRepository

data class LocalRepositories(
    val diaryRepository: DiaryRepository,
    val scheduleRepository: ScheduleRepository,
    val tagRepository: TagRepository,
    val chatRepository: ChatRepository,
    val syncRepository: SyncRepository,
    val accountRepository: AccountRepository,
    val userPreferencesRepository: UserPreferencesRepository,
    val modelPackageRepository: ModelPackageRepository,
    val session: AppSession,
)
