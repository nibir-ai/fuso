package com.fuso.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fuso.core.database.entity.BlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {

    @Query("SELECT * FROM blocks WHERE entryId = :entryId ORDER BY position ASC")
    fun observeBlocksForEntry(entryId: String): Flow<List<BlockEntity>>

    @Upsert
    suspend fun upsertBlocks(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE entryId = :entryId AND id NOT IN (:keptBlockIds)")
    suspend fun deleteBlocksNotIn(entryId: String, keptBlockIds: List<String>)
}
