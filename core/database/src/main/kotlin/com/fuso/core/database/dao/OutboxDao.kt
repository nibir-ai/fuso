package com.fuso.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fuso.core.database.entity.OutboxEntity

@Dao
interface OutboxDao {

    @Insert
    suspend fun enqueue(event: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY id ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteProcessed(ids: List<Long>)
}
