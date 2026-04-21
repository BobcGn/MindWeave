package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.domain.model.UserPreferences

interface UserPreferencesRepository {
    fun observePreferences(userId: String): Flow<UserPreferences?>

    suspend fun getPreferences(userId: String): UserPreferences?

    suspend fun ensureDefaultPreferences(
        userId: String,
        bootstrapAiSettings: AiSettings,
    )

    suspend fun savePreferences(
        userId: String,
        aiMode: AiOperatingMode,
        cloudEnhancementBaseUrl: String,
        localLightweightModelPackageId: String,
        localGenerativeModelPackageId: String,
        modelDownloadPolicy: ModelDownloadPolicy,
    )
}
