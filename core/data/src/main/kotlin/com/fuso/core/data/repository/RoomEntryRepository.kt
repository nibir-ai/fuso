package com.fuso.core.data.repository

import com.fuso.core.data.mapper.EntryMappers
import com.fuso.core.database.FusoDatabase
import com.fuso.core.database.dao.BlockDao
import com.fuso.core.database.dao.EntryDao
import com.fuso.core.database.entity.BlockEntity
import com.fuso.core.database.entity.EntryTagCrossRef
import com.fuso.core.database.entity.OutboxEntity
import com.fuso.core.database.entity.TagEntity
import com.fuso.core.data.sync.SyncEnqueuer
import com.fuso.core.intelligence.UsageTracker
import com.fuso.core.model.BlockContent
import com.fuso.core.model.Entry
import com.fuso.core.model.EntryType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomEntryRepository @Inject constructor(
    private val database: FusoDatabase,
    private val entryDao: EntryDao,
    private val blockDao: BlockDao,
    private val usageTracker: UsageTracker,
    private val outboxDao: com.fuso.core.database.dao.OutboxDao,
    private val syncEnqueuer: SyncEnqueuer,
) : EntryRepository {

    override fun observeEntries(): Flow<List<Entry>> =
        entryDao.observeAllEntries()
            .map(EntryMappers::toDomainList)

    override fun observeEntriesBetween(start: Instant, end: Instant): Flow<List<Entry>> =
        entryDao.observeEntriesBetween(start.toEpochMilli(), end.toEpochMilli())
            .map(EntryMappers::toDomainList)

    override fun observeEntry(entryId: String): Flow<Entry?> =
        entryDao.observeEntryById(entryId)
            .map { details -> details?.let(EntryMappers::toDomain) }

    override fun observeBlocks(entryId: String): Flow<List<BlockContent>> =
        blockDao.observeBlocksForEntry(entryId)
            .map { entities -> EntryMappers.parseBlocks(entities) }

    override fun search(query: String): Flow<List<Entry>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeEntries()
        return entryDao.searchEntries(toFtsQuery(trimmed))
            .map(EntryMappers::toDomainList)
    }

    override suspend fun saveEntry(
        entryId: String,
        type: EntryType,
        title: String,
        blocks: List<BlockContent>,
        tags: List<String>,
        isPinned: Boolean,
        createdAt: Instant,
        updatedAt: Instant,
    ) {
        database.runInTransaction {
            val blockEntities = blocks.mapIndexed { position, content ->
                BlockEntity(
                    id = "$entryId-block-$position",
                    entryId = entryId,
                    position = position,
                    type = blockTypeName(content),
                    contentJson = EntryMappers.encodeBlock(content),
                )
            }
            entryDao.upsertEntries(
                listOf(
                    EntryMappers.toEntity(
                        id = entryId,
                        type = type,
                        title = title,
                        blocks = blocks,
                        tags = tags,
                        createdAtMillis = createdAt.toEpochMilli(),
                        updatedAtMillis = updatedAt.toEpochMilli(),
                        isPinned = isPinned,
                    ),
                ),
            )
            blockDao.deleteBlocksNotIn(entryId, blockEntities.map { it.id })
            blockDao.upsertBlocks(blockEntities)
            entryDao.clearTagsForEntry(entryId)
            if (tags.isNotEmpty()) {
                entryDao.insertTags(tags.map { TagEntity(it) })
                entryDao.insertEntryTagRefs(tags.map { EntryTagCrossRef(entryId, it) })
            }
            outboxDao.enqueue(
                OutboxEntity(
                    opType = OutboxEntity.OP_UPSERT,
                    entryId = entryId,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        syncEnqueuer.requestSync()
        val wordCount = blocks.sumOf { block ->
            block.text.trim().split(WHITESPACE).count { it.isNotBlank() }
        }
        usageTracker.logEntrySaved(wordCount = wordCount, entryId = entryId)
    }

    override suspend fun softDeleteEntry(entryId: String) {
        val deletedMillis = System.currentTimeMillis()
        database.runInTransaction {
            entryDao.softDeleteEntry(entryId, deletedMillis)
            outboxDao.enqueue(
                OutboxEntity(
                    opType = OutboxEntity.OP_DELETE,
                    entryId = entryId,
                    createdAtMillis = deletedMillis,
                ),
            )
        }
        syncEnqueuer.requestSync()
    }

    override fun observeTrashedEntries(): Flow<List<Entry>> =
        entryDao.observeTrashed().map(EntryMappers::toDomainList)

    override suspend fun restoreEntry(entryId: String) {
        database.runInTransaction {
            entryDao.restoreEntry(entryId, System.currentTimeMillis())
            outboxDao.enqueue(
                OutboxEntity(
                    opType = OutboxEntity.OP_UPSERT,
                    entryId = entryId,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        syncEnqueuer.requestSync()
    }

    override suspend fun deleteForever(entryId: String) {
        database.runInTransaction {
            entryDao.deleteForeverById(entryId)
            outboxDao.enqueue(
                OutboxEntity(
                    opType = OutboxEntity.OP_DELETE,
                    entryId = entryId,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        syncEnqueuer.requestSync()
    }

    override suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val ids = entryDao.trashedIdsBefore(cutoff)
        if (ids.isEmpty()) return
        database.runInTransaction {
            entryDao.purgeDeletedBefore(cutoff)
            ids.forEach { id ->
                outboxDao.enqueue(
                    OutboxEntity(
                        opType = OutboxEntity.OP_DELETE,
                        entryId = id,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
        syncEnqueuer.requestSync()
    }

    private fun toFtsQuery(query: String): String =
        query.split(WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ") { token -> "\"${token.replace("\"", "")}\"*" }

    private fun blockTypeName(content: BlockContent): String = when (content) {
        is BlockContent.Paragraph -> "paragraph"
        is BlockContent.Heading -> "heading"
        is BlockContent.Todo -> "todo"
        is BlockContent.Bullet -> "bullet"
        is BlockContent.Numbered -> "numbered"
        is BlockContent.Quote -> "quote"
        BlockContent.Divider -> "divider"
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val TRASH_RETENTION_DAYS = 30L
    }
}
