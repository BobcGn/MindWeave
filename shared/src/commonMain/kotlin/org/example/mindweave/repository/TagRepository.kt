package org.example.mindweave.repository

import kotlinx.coroutines.flow.Flow
import org.example.mindweave.domain.model.DiaryEntryTag
import org.example.mindweave.domain.model.Tag

interface TagRepository {
    fun observeTags(userId: String): Flow<List<Tag>>

    suspend fun ensureTags(userId: String, deviceId: String, names: List<String>): List<Tag>

    suspend fun replaceTagsForDiary(
        userId: String,
        deviceId: String,
        entryId: String,
        tagNames: List<String>,
        baseVersion: Long,
    )

    suspend fun getTagsForDiary(entryId: String): List<Tag>

    suspend fun getTagById(id: String): Tag?

    suspend fun getDiaryEntryTagById(id: String): DiaryEntryTag?

    suspend fun upsertTag(tag: Tag, trackSync: Boolean = true)

    suspend fun upsertDiaryEntryTag(link: DiaryEntryTag, trackSync: Boolean = true)
}
