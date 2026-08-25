package com.fuso.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fuso.core.database.pojo.HourWeight
import com.fuso.core.database.pojo.DayWeight

@Dao
interface UsageDao {

    @Insert
    suspend fun insertEvent(event: com.fuso.core.database.entity.UsageEventEntity)

    @Query(
        """
        SELECT hourOfDay AS value, COUNT(*) AS weight FROM usage_events
        WHERE type IN ('APP_OPEN', 'ENTRY_SAVED')
        GROUP BY hourOfDay
        ORDER BY weight DESC
        """,
    )
    suspend fun hourWeights(): List<HourWeight>

    @Query(
        """
        SELECT dayOfWeek AS value, COUNT(*) AS weight FROM usage_events
        WHERE type IN ('APP_OPEN', 'ENTRY_SAVED')
        GROUP BY dayOfWeek
        ORDER BY weight DESC
        """,
    )
    suspend fun dayWeights(): List<DayWeight>

    @Query("SELECT MAX(timestampEpochMillis) FROM usage_events WHERE type = 'ENTRY_SAVED'")
    suspend fun lastEntrySavedAtMillis(): Long?

    @Query("SELECT COUNT(*) FROM usage_events WHERE type = 'ENTRY_SAVED' AND timestampEpochMillis >= :sinceMillis")
    suspend fun entriesSavedSince(sinceMillis: Long): Int

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM usage_events WHERE type = 'ENTRY_SAVED'")
    suspend fun totalWordsWritten(): Int
}
