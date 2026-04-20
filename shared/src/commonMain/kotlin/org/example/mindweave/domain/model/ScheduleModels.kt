package org.example.mindweave.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleType(val label: String) {
    WORK("工作"),
    LIFE("生活"),
    HEALTH("健康"),
    FAMILY("家庭"),
    REFLECTION("复盘");

    companion object {
        fun fromStorage(value: String): ScheduleType = entries.firstOrNull { it.name == value } ?: LIFE
    }
}

@Serializable
data class ScheduleEvent(
    override val id: String,
    override val userId: String,
    val title: String,
    val description: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val remindAtEpochMs: Long?,
    val type: ScheduleType,
    override val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val deletedAtEpochMs: Long?,
    override val version: Long,
    override val lastModifiedByDeviceId: String,
) : SyncableEntity

@Serializable
data class ScheduleDraft(
    val title: String,
    val description: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val remindAtEpochMs: Long?,
    val type: ScheduleType,
)
