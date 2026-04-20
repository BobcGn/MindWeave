package org.example.mindweave.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiaryMood(val label: String) {
    CALM("平静"),
    ENERGIZED("有劲"),
    HEAVY("沉重"),
    GRATEFUL("感激"),
    UNCERTAIN("摇摆");

    companion object {
        fun fromStorage(value: String): DiaryMood = entries.firstOrNull { it.name == value } ?: UNCERTAIN
    }
}

@Serializable
data class DiaryEntry(
    override val id: String,
    override val userId: String,
    val title: String,
    val content: String,
    val mood: DiaryMood,
    val aiSummary: String?,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class DiaryDraft(
    val title: String,
    val content: String,
    val mood: DiaryMood,
    val tags: List<String>,
)

@Serializable
data class Tag(
    override val id: String,
    override val userId: String,
    val name: String,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class DiaryEntryTag(
    override val id: String,
    override val userId: String,
    val entryId: String,
    val tagId: String,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class DiaryTimelineItem(
    val entry: DiaryEntry,
    val tags: List<String>,
)
