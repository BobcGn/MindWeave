package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.ScheduleDraft
import org.example.mindweave.domain.model.ScheduleEvent

interface ScheduleRepository {
    fun observeUpcoming(userId: String): Flow<List<ScheduleEvent>>

    suspend fun createDraft(userId: String, deviceId: String, draft: ScheduleDraft): ScheduleEvent

    suspend fun upsert(event: ScheduleEvent, trackSync: Boolean = true)

    suspend fun getById(id: String): ScheduleEvent?

    suspend fun getUpcoming(userId: String, fromEpochMs: Long, limit: Long): List<ScheduleEvent>

    suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long)

    suspend fun countActive(userId: String): Long
}
