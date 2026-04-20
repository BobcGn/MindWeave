package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.DiaryDraft
import org.example.mindweave.domain.model.DiaryEntry
import org.example.mindweave.domain.model.DiaryTimelineItem

interface DiaryRepository {
    fun observeTimeline(userId: String): Flow<List<DiaryTimelineItem>>

    suspend fun createDraft(userId: String, deviceId: String, draft: DiaryDraft): DiaryEntry

    suspend fun upsert(entry: DiaryEntry, trackSync: Boolean = true)

    suspend fun getById(id: String): DiaryTimelineItem?

    suspend fun getRecent(userId: String, limit: Long): List<DiaryTimelineItem>

    suspend fun softDelete(id: String, deviceId: String, deletedAtEpochMs: Long, nextVersion: Long)

    suspend fun countActive(userId: String): Long
}
