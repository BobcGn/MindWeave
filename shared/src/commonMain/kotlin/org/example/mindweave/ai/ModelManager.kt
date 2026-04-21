package org.example.mindweave.ai

import kotlinx.serialization.Serializable
import org.example.mindweave.repository.ModelPackageRepository
import org.example.mindweave.util.currentEpochMillis

@Serializable
enum class ModelPackageKind(
    val label: String,
) {
    LIGHTWEIGHT("本地轻模型"),
    GENERATIVE("本地生成模型"),
    EMBEDDING("本地向量模型"),
}

@Serializable
enum class ModelInstallStatus(
    val label: String,
) {
    AVAILABLE("可用"),
    DOWNLOADING("下载中"),
    INSTALLED("已安装"),
    FAILED("失败");

    companion object {
        fun fromStorage(value: String): ModelInstallStatus =
            entries.firstOrNull { it.name == value } ?: AVAILABLE
    }
}

@Serializable
enum class ModelDownloadPolicy(
    val label: String,
) {
    MANUAL_ONLY("手动下载"),
    WIFI_ONLY("仅 Wi-Fi"),
    BACKGROUND_ALLOWED("允许后台"),
    PREBUNDLED("预置模型");

    companion object {
        fun fromStorage(value: String): ModelDownloadPolicy =
            entries.firstOrNull { it.name == value } ?: PREBUNDLED
    }
}

@Serializable
data class ModelVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): ModelVersion {
            val parts = value.split(".")
            return ModelVersion(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 1,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
    }
}

@Serializable
data class ModelPackage(
    val packageId: String,
    val displayName: String,
    val kind: ModelPackageKind,
    val version: ModelVersion,
    val installStatus: ModelInstallStatus,
    val localPath: String?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val isEnabled: Boolean,
    val downloadPolicy: ModelDownloadPolicy,
    val updatedAtEpochMs: Long,
) {
    val isReady: Boolean
        get() = installStatus == ModelInstallStatus.INSTALLED && isEnabled
}

interface ModelManager {
    suspend fun ensureDefaultPackages(settings: AiSettings)

    suspend fun listPackages(): List<ModelPackage>

    suspend fun getPackage(packageId: String): ModelPackage?

    suspend fun hasReadyPackage(packageId: String): Boolean

    suspend fun upsertPackage(modelPackage: ModelPackage)
}

class DefaultModelManager(
    private val repository: ModelPackageRepository,
    private val nowProvider: () -> Long = ::currentEpochMillis,
) : ModelManager {
    override suspend fun ensureDefaultPackages(settings: AiSettings) {
        defaultPackages(settings).forEach { modelPackage ->
            if (repository.getModelPackage(modelPackage.packageId) == null) {
                repository.upsertModelPackage(modelPackage)
            }
        }
    }

    override suspend fun listPackages(): List<ModelPackage> = repository.listModelPackages()

    override suspend fun getPackage(packageId: String): ModelPackage? =
        repository.getModelPackage(packageId)

    override suspend fun hasReadyPackage(packageId: String): Boolean =
        repository.getModelPackage(packageId)?.isReady == true

    override suspend fun upsertPackage(modelPackage: ModelPackage) {
        repository.upsertModelPackage(modelPackage)
    }

    private fun defaultPackages(settings: AiSettings): List<ModelPackage> {
        val policy = settings.modelDownloadPolicy()
        val now = nowProvider()
        return listOf(
            ModelPackage(
                packageId = settings.lightweightModelPackageId(),
                displayName = "MindWeave Lite Core",
                kind = ModelPackageKind.LIGHTWEIGHT,
                version = ModelVersion(1, 0, 0),
                installStatus = ModelInstallStatus.INSTALLED,
                localPath = null,
                downloadedBytes = 0,
                totalBytes = 0,
                isEnabled = true,
                downloadPolicy = policy,
                updatedAtEpochMs = now,
            ),
            ModelPackage(
                packageId = settings.generativeModelPackageId(),
                displayName = "MindWeave Local Generator",
                kind = ModelPackageKind.GENERATIVE,
                version = ModelVersion(1, 0, 0),
                installStatus = ModelInstallStatus.INSTALLED,
                localPath = null,
                downloadedBytes = 0,
                totalBytes = 0,
                isEnabled = true,
                downloadPolicy = policy,
                updatedAtEpochMs = now,
            ),
            ModelPackage(
                packageId = AiSettings.DEFAULT_EMBEDDING_MODEL_PACKAGE_ID,
                displayName = "MindWeave Local Embedding",
                kind = ModelPackageKind.EMBEDDING,
                version = ModelVersion(1, 0, 0),
                installStatus = ModelInstallStatus.INSTALLED,
                localPath = null,
                downloadedBytes = 0,
                totalBytes = 0,
                isEnabled = true,
                downloadPolicy = policy,
                updatedAtEpochMs = now,
            ),
        )
    }
}
