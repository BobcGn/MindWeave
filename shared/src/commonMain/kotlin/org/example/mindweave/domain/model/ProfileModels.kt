package org.example.mindweave.domain.model

import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.ModelDownloadPolicy

data class UserAccount(
    val userId: String,
    val username: String,
    val passwordHash: String,
    val mustChangeCredentials: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val credentialsUpdatedAtEpochMs: Long,
    val lastLoginAtEpochMs: Long?,
)

data class UserPreferences(
    val userId: String,
    val aiMode: AiOperatingMode,
    val cloudEnhancementBaseUrl: String,
    val localLightweightModelPackageId: String,
    val localGenerativeModelPackageId: String,
    val modelDownloadPolicy: ModelDownloadPolicy,
    val updatedAtEpochMs: Long,
)
