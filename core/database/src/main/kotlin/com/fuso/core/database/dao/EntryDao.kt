package com.fuso.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fuso.core.database.entity.EntryEntity
import com.fuso.core.database.entity.EntryTagCrossRef
import com.fuso.core.database.entity.TagEntity
import com.fuso.core.database.pojo.EntryWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun countEntries(): Int

    @Query("SELECT * FROM entries WHERE deletedAtEpochMillis IS NULL ORDER BY createdAtEpochMillis DESC")
    fun observeRawEntries(): Flow<List<EntryEntity>>

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAtEpochMillis IS NULL AND isArchived = 0 ORDER BY createdAtEpochMillis DESC")
    fun observeAllEntries(): Flow<List<EntryWithDetails>>

    @Transaction
    @Query(
        """
        SELECT * FROM entries
        WHERE deletedAtEpochMillis IS NULL
          AND isArchived = 0
          AND createdAtEpochMillis >= :startMillis
          AND createdAtEpochMillis < :endMillisExclusive
        ORDER BY createdAtEpochMillis DESC
        """,
    )
    fun observeEntriesBetween(startMillis: Long, endMillisExclusive: Long): Flow<List<EntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :entryId AND deletedAtEpochMillis IS NULL LIMIT 1")
    fun observeEntryById(entryId: String): Flow<EntryWithDetails?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): EntryWithDetails?

    @Transaction
    @Query(
        """
        SELECT entries.* FROM entries
        JOIN entries_fts ON entries.rowid = entries_fts.rowid
        WHERE entries_fts MATCH :ftsQuery
          AND entries.deletedAtEpochMillis IS NULL
          AND entries.isArchived = 0
        ORDER BY entries.createdAtEpochMillis DESC
        """,
    )
    fun searchEntries(ftsQuery: String): Flow<List<EntryWithDetails>>

    @Upsert
    suspend fun upsertEntries(entries: List<EntryEntity>)

    @Query("UPDATE entries SET deletedAtEpochMillis = :deletedMillis, updatedAtEpochMillis = :deletedMillis WHERE id = :entryId")
    suspend fun softDeleteEntry(entryId: String, deletedMillis: Long)

    @Transaction
    @Query("SELECT * FROM entries WHERE deletedAtEpochMillis IS NOT NULL ORDER BY deletedAtEpochMillis DESC")
    fun observeTrashed(): Flow<List<EntryWithDetails>>

    @Query("UPDATE entries SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :restoredMillis WHERE id = :entryId")
    suspend fun restoreEntry(entryId: String, restoredMillis: Long)

    @Query("DELETE FROM entries WHERE id = :entryId")
    suspend fun deleteForeverById(entryId: String)

    @Query("SELECT id FROM entries WHERE deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :cutoffMillis")
    suspend fun trashedIdsBefore(cutoffMillis: Long): List<String>

    @Query("DELETE FROM entries WHERE deletedAtEpochMillis IS NOT NULL AND deletedAtEpochMillis < :cutoffMillis")
    suspend fun purgeDeletedBefore(cutoffMillis: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun clearTagsForEntry(entryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryTagRefs(refs: List<EntryTagCrossRef>)
}
