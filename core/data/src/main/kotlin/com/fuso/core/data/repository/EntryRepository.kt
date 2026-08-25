package com.fuso.core.data.repository

import com.fuso.core.model.BlockContent
import com.fuso.core.model.Entry
import com.fuso.core.model.EntryType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface EntryRepository {

    fun observeEntries(): Flow<List<Entry>>

    fun observeEntriesBetween(start: Instant, end: Instant): Flow<List<Entry>>

    fun observeEntry(entryId: String): Flow<Entry?>

    fun observeBlocks(entryId: String): Flow<List<BlockContent>>

    fun search(query: String): Flow<List<Entry>>

    suspend fun saveEntry(
        entryId: String,
        type: EntryType,
        title: String,
        blocks: List<BlockContent>,
        tags: List<String>,
        isPinned: Boolean,
        createdAt: Instant,
        updatedAt: Instant = Instant.now(),
    )

    suspend fun softDeleteEntry(entryId: String)

    fun observeTrashedEntries(): Flow<List<Entry>>

    suspend fun restoreEntry(entryId: String)

    suspend fun deleteForever(entryId: String)

    suspend fun purgeOldTrash()
}
