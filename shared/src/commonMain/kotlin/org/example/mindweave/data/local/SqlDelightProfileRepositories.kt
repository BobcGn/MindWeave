package org.example.mindweave.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.example.mindweave.DEFAULT_BOOTSTRAP_PASSWORD
import org.example.mindweave.DEFAULT_BOOTSTRAP_USERNAME
import org.example.mindweave.ai.AiOperatingMode
import org.example.mindweave.ai.AiSettings
import org.example.mindweave.ai.ModelDownloadPolicy
import org.example.mindweave.ai.ModelInstallStatus
import org.example.mindweave.ai.ModelPackage
import org.example.mindweave.ai.ModelPackageKind
import org.example.mindweave.ai.ModelVersion
import org.example.mindweave.db.MindWeaveDatabase
import org.example.mindweave.db.ProfileQueries
import org.example.mindweave.domain.model.UserAccount
import org.example.mindweave.domain.model.UserPreferences
import org.example.mindweave.repository.AccountRepository
import org.example.mindweave.repository.ModelPackageRepository
import org.example.mindweave.repository.UserPreferencesRepository
import org.example.mindweave.util.currentEpochMillis
import org.example.mindweave.util.hashPassword

class SqlDelightAccountRepository(
    database: MindWeaveDatabase,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : AccountRepository {
    private val queries: ProfileQueries = database.profileQueries

    override fun observeAccount(userId: String): Flow<UserAccount?> =
        queries.selectAccountByUserId(userId, ::mapAccount)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)

    override suspend fun getAccount(userId: String): UserAccount? =
        queries.selectAccountByUserId(userId, ::mapAccount).executeAsOneOrNull()

    override suspend fun authenticate(userId: String, username: String, password: String): UserAccount? {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) return null
        val account = getAccount(userId) ?: return null
        if (account.username != normalizedUsername) {
            return null
        }
        if (account.passwordHash != hashPassword(password)) {
            return null
        }

        val loginAtEpochMs = nowProvider()
        queries.recordSuccessfulLogin(
            last_login_at_epoch_ms = loginAtEpochMs,
            user_id = account.userId,
        )
        return account.copy(lastLoginAtEpochMs = loginAtEpochMs)
    }

    override suspend fun ensureDefaultAccount(userId: String) {
        if (getAccount(userId) != null) return
        val now = nowProvider()
        queries.upsertAccount(
            user_id = userId,
            username = DEFAULT_BOOTSTRAP_USERNAME,
            password_hash = hashPassword(DEFAULT_BOOTSTRAP_PASSWORD),
            must_change_credentials = true,
            created_at_epoch_ms = now,
            updated_at_epoch_ms = now,
            credentials_updated_at_epoch_ms = now,
            last_login_at_epoch_ms = null,
        )
    }

    override suspend fun forceResetCredentials(
        userId: String,
        newUsername: String,
        newPassword: String,
    ): String? = updateCredentials(
        userId = userId,
        currentPassword = null,
        newUsername = newUsername,
        newPassword = newPassword,
    )

    override suspend fun changeCredentials(
        userId: String,
        currentPassword: String,
        newUsername: String,
        newPassword: String,
    ): String? = updateCredentials(
        userId = userId,
        currentPassword = currentPassword,
        newUsername = newUsername,
        newPassword = newPassword,
    )

    private suspend fun updateCredentials(
        userId: String,
        currentPassword: String?,
        newUsername: String,
        newPassword: String,
    ): String? {
        val account = getAccount(userId) ?: return "账户不存在。"
        val normalizedUsername = newUsername.trim()
        if (normalizedUsername.isBlank()) return "账号不能为空。"
        if (newPassword.isBlank()) return "密码不能为空。"
        if (normalizedUsername == DEFAULT_BOOTSTRAP_USERNAME) return "默认账号不能继续使用，请更换为个人账号。"
        if (newPassword == DEFAULT_BOOTSTRAP_PASSWORD) return "默认密码不能继续使用，请设置新密码。"
        if (currentPassword != null && account.passwordHash != hashPassword(currentPassword)) {
            return "当前密码不正确。"
        }
        val existing = queries.selectAccountByUsername(normalizedUsername, ::mapAccount).executeAsOneOrNull()
        if (existing != null && existing.userId != userId) {
            return "该账号已存在。"
        }
        val now = nowProvider()
        queries.upsertAccount(
            user_id = account.userId,
            username = normalizedUsername,
            password_hash = hashPassword(newPassword),
            must_change_credentials = false,
            created_at_epoch_ms = account.createdAtEpochMs,
            updated_at_epoch_ms = now,
            credentials_updated_at_epoch_ms = now,
            last_login_at_epoch_ms = account.lastLoginAtEpochMs,
        )
        return null
    }

    private fun mapAccount(
        userId: String,
        username: String,
        passwordHash: String,
        mustChangeCredentials: Boolean,
        createdAtEpochMs: Long,
        updatedAtEpochMs: Long,
        credentialsUpdatedAtEpochMs: Long,
        lastLoginAtEpochMs: Long?,
    ): UserAccount = UserAccount(
        userId = userId,
        username = username,
        passwordHash = passwordHash,
        mustChangeCredentials = mustChangeCredentials,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        credentialsUpdatedAtEpochMs = credentialsUpdatedAtEpochMs,
        lastLoginAtEpochMs = lastLoginAtEpochMs,
    )
}

class SqlDelightUserPreferencesRepository(
    database: MindWeaveDatabase,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : UserPreferencesRepository {
    private val queries: ProfileQueries = database.profileQueries

    override fun observePreferences(userId: String): Flow<UserPreferences?> =
        queries.selectPreferencesByUserId(userId, ::mapPreferences)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)

    override suspend fun getPreferences(userId: String): UserPreferences? =
        queries.selectPreferencesByUserId(userId, ::mapPreferences).executeAsOneOrNull()

    override suspend fun ensureDefaultPreferences(
        userId: String,
        bootstrapAiSettings: AiSettings,
    ) {
        if (getPreferences(userId) != null) return
        val seed = when (bootstrapAiSettings) {
            AiSettings.Disabled -> UserPreferences(
                userId = userId,
                aiMode = AiOperatingMode.DISABLED,
                cloudEnhancementBaseUrl = "",
                localLightweightModelPackageId = AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
                localGenerativeModelPackageId = AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
                modelDownloadPolicy = ModelDownloadPolicy.PREBUNDLED,
                updatedAtEpochMs = nowProvider(),
            )
            is AiSettings.LocalOnly -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = "",
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = nowProvider(),
            )
            is AiSettings.LocalFirstCloudEnhancement -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = bootstrapAiSettings.cloudEnhancementBaseUrl,
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = nowProvider(),
            )
            is AiSettings.ManualCloudEnhancement -> UserPreferences(
                userId = userId,
                aiMode = bootstrapAiSettings.mode,
                cloudEnhancementBaseUrl = bootstrapAiSettings.cloudEnhancementBaseUrl,
                localLightweightModelPackageId = bootstrapAiSettings.lightweightModelPackageId,
                localGenerativeModelPackageId = bootstrapAiSettings.generativeModelPackageId,
                modelDownloadPolicy = bootstrapAiSettings.downloadPolicy,
                updatedAtEpochMs = nowProvider(),
            )
        }
        queries.upsertUserPreferences(
            user_id = seed.userId,
            openai_api_key = "",
            openai_model = "",
            openai_base_url = "",
            ai_mode = seed.aiMode.storageValue,
            cloud_enhancement_base_url = seed.cloudEnhancementBaseUrl,
            local_lightweight_model_package_id = seed.localLightweightModelPackageId,
            local_generative_model_package_id = seed.localGenerativeModelPackageId,
            model_download_policy = seed.modelDownloadPolicy.name,
            updated_at_epoch_ms = seed.updatedAtEpochMs,
        )
    }

    override suspend fun savePreferences(
        userId: String,
        aiMode: AiOperatingMode,
        cloudEnhancementBaseUrl: String,
        localLightweightModelPackageId: String,
        localGenerativeModelPackageId: String,
        modelDownloadPolicy: ModelDownloadPolicy,
    ) {
        queries.upsertUserPreferences(
            user_id = userId,
            openai_api_key = "",
            openai_model = "",
            openai_base_url = "",
            ai_mode = aiMode.storageValue,
            cloud_enhancement_base_url = cloudEnhancementBaseUrl.trim(),
            local_lightweight_model_package_id = localLightweightModelPackageId.trim()
                .ifBlank { AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID },
            local_generative_model_package_id = localGenerativeModelPackageId.trim()
                .ifBlank { AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID },
            model_download_policy = modelDownloadPolicy.name,
            updated_at_epoch_ms = nowProvider(),
        )
    }

    private fun mapPreferences(
        userId: String,
        aiMode: String,
        cloudEnhancementBaseUrl: String,
        localLightweightModelPackageId: String,
        localGenerativeModelPackageId: String,
        modelDownloadPolicy: String,
        updatedAtEpochMs: Long,
    ): UserPreferences = UserPreferences(
        userId = userId,
        aiMode = AiOperatingMode.fromStorage(aiMode),
        cloudEnhancementBaseUrl = cloudEnhancementBaseUrl,
        localLightweightModelPackageId = localLightweightModelPackageId,
        localGenerativeModelPackageId = localGenerativeModelPackageId,
        modelDownloadPolicy = ModelDownloadPolicy.fromStorage(modelDownloadPolicy),
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

class SqlDelightModelPackageRepository(
    database: MindWeaveDatabase,
) : ModelPackageRepository {
    private val queries: ProfileQueries = database.profileQueries

    override suspend fun listModelPackages(): List<ModelPackage> =
        queries.selectAllModelPackages(::mapModelPackage).executeAsList()

    override suspend fun getModelPackage(packageId: String): ModelPackage? =
        queries.selectModelPackageById(packageId, ::mapModelPackage).executeAsOneOrNull()

    override suspend fun upsertModelPackage(modelPackage: ModelPackage) {
        queries.upsertModelPackage(
            package_id = modelPackage.packageId,
            display_name = modelPackage.displayName,
            package_kind = modelPackage.kind.name,
            semantic_version = modelPackage.version.toString(),
            install_status = modelPackage.installStatus.name,
            local_path = modelPackage.localPath,
            downloaded_bytes = modelPackage.downloadedBytes,
            total_bytes = modelPackage.totalBytes,
            is_enabled = modelPackage.isEnabled,
            download_policy = modelPackage.downloadPolicy.name,
            updated_at_epoch_ms = modelPackage.updatedAtEpochMs,
        )
    }

    private fun mapModelPackage(
        packageId: String,
        displayName: String,
        packageKind: String,
        semanticVersion: String,
        installStatus: String,
        localPath: String?,
        downloadedBytes: Long,
        totalBytes: Long?,
        isEnabled: Boolean,
        downloadPolicy: String,
        updatedAtEpochMs: Long,
    ): ModelPackage = ModelPackage(
        packageId = packageId,
        displayName = displayName,
        kind = ModelPackageKind.entries.firstOrNull { it.name == packageKind } ?: ModelPackageKind.LIGHTWEIGHT,
        version = ModelVersion.parse(semanticVersion),
        installStatus = ModelInstallStatus.fromStorage(installStatus),
        localPath = localPath,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        isEnabled = isEnabled,
        downloadPolicy = ModelDownloadPolicy.fromStorage(downloadPolicy),
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
