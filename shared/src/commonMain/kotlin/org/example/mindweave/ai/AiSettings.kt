package org.example.mindweave.ai

enum class AiOperatingMode(
    val storageValue: String,
    val label: String,
    val description: String,
) {
    LOCAL_ONLY(
        storageValue = "LOCAL_ONLY",
        label = "仅本地",
        description = "所有 AI 任务都优先在端侧完成，不访问云端增强。",
    ),
    LOCAL_FIRST_CLOUD_ENHANCEMENT(
        storageValue = "LOCAL_FIRST_CLOUD_ENHANCEMENT",
        label = "本地优先",
        description = "优先尝试本地能力，本地能力不足时允许云端增强。",
    ),
    MANUAL_CLOUD_ENHANCEMENT(
        storageValue = "MANUAL_CLOUD_ENHANCEMENT",
        label = "手动云增强",
        description = "默认只用本地能力，仅在显式允许时才调用云端增强。",
    ),
    DISABLED(
        storageValue = "DISABLED",
        label = "完全关闭",
        description = "关闭所有 AI 相关处理。",
    );

    companion object {
        fun fromStorage(value: String): AiOperatingMode =
            entries.firstOrNull { it.storageValue == value } ?: LOCAL_ONLY
    }
}

sealed interface AiSettings {
    val mode: AiOperatingMode

    data object Disabled : AiSettings {
        override val mode: AiOperatingMode = AiOperatingMode.DISABLED
    }

    data class LocalOnly(
        val lightweightModelPackageId: String = DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
        val generativeModelPackageId: String = DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
        val downloadPolicy: ModelDownloadPolicy = ModelDownloadPolicy.PREBUNDLED,
    ) : AiSettings {
        override val mode: AiOperatingMode = AiOperatingMode.LOCAL_ONLY
    }

    data class LocalFirstCloudEnhancement(
        val cloudEnhancementBaseUrl: String = DEFAULT_CLOUD_ENHANCEMENT_BASE_URL,
        val lightweightModelPackageId: String = DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
        val generativeModelPackageId: String = DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
        val downloadPolicy: ModelDownloadPolicy = ModelDownloadPolicy.PREBUNDLED,
    ) : AiSettings {
        override val mode: AiOperatingMode = AiOperatingMode.LOCAL_FIRST_CLOUD_ENHANCEMENT
    }

    data class ManualCloudEnhancement(
        val cloudEnhancementBaseUrl: String = DEFAULT_CLOUD_ENHANCEMENT_BASE_URL,
        val lightweightModelPackageId: String = DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID,
        val generativeModelPackageId: String = DEFAULT_GENERATIVE_MODEL_PACKAGE_ID,
        val downloadPolicy: ModelDownloadPolicy = ModelDownloadPolicy.PREBUNDLED,
    ) : AiSettings {
        override val mode: AiOperatingMode = AiOperatingMode.MANUAL_CLOUD_ENHANCEMENT
    }

    companion object {
        const val DEFAULT_CLOUD_ENHANCEMENT_BASE_URL: String = ""
        const val DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID: String = "mindweave-lite-core"
        const val DEFAULT_GENERATIVE_MODEL_PACKAGE_ID: String = "mindweave-gen-core"
        const val DEFAULT_EMBEDDING_MODEL_PACKAGE_ID: String = "mindweave-embed-core"
    }
}

fun AiSettings.cloudEnhancementBaseUrlOrNull(): String? = when (this) {
    AiSettings.Disabled -> null
    is AiSettings.LocalOnly -> null
    is AiSettings.LocalFirstCloudEnhancement -> cloudEnhancementBaseUrl.trim().ifBlank { null }
    is AiSettings.ManualCloudEnhancement -> cloudEnhancementBaseUrl.trim().ifBlank { null }
}

fun AiSettings.lightweightModelPackageId(): String = when (this) {
    AiSettings.Disabled -> AiSettings.DEFAULT_LIGHTWEIGHT_MODEL_PACKAGE_ID
    is AiSettings.LocalOnly -> lightweightModelPackageId
    is AiSettings.LocalFirstCloudEnhancement -> lightweightModelPackageId
    is AiSettings.ManualCloudEnhancement -> lightweightModelPackageId
}

fun AiSettings.generativeModelPackageId(): String = when (this) {
    AiSettings.Disabled -> AiSettings.DEFAULT_GENERATIVE_MODEL_PACKAGE_ID
    is AiSettings.LocalOnly -> generativeModelPackageId
    is AiSettings.LocalFirstCloudEnhancement -> generativeModelPackageId
    is AiSettings.ManualCloudEnhancement -> generativeModelPackageId
}

fun AiSettings.modelDownloadPolicy(): ModelDownloadPolicy = when (this) {
    AiSettings.Disabled -> ModelDownloadPolicy.MANUAL_ONLY
    is AiSettings.LocalOnly -> downloadPolicy
    is AiSettings.LocalFirstCloudEnhancement -> downloadPolicy
    is AiSettings.ManualCloudEnhancement -> downloadPolicy
}
