package com.fuso.core.data.sync

import com.fuso.core.data.mapper.EntryMappers
import com.fuso.core.data.remote.RemoteEntry
import com.fuso.core.data.remote.SupabaseApi
import com.fuso.core.data.remote.SupabaseConfig
import com.fuso.core.database.FusoDatabase
import com.fuso.core.database.dao.BlockDao
import com.fuso.core.database.dao.EntryDao
import com.fuso.core.database.dao.OutboxDao
import com.fuso.core.database.entity.BlockEntity
import com.fuso.core.database.entity.EntryTagCrossRef
import com.fuso.core.database.entity.OutboxEntity
import com.fuso.core.database.entity.TagEntity
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.serializer

enum class SyncStatus { Idle, Running, Succeeded, Failed, SignedOut }

data class SyncState(
    val status: SyncStatus = SyncStatus.Idle,
    val lastMessage: String = "",
    val pendingOps: Int = 0,
)

@Singleton
class SyncEngine @Inject constructor(
    private val api: SupabaseApi,
    private val sessionManager: SessionManager,
    private val config: SupabaseConfig,
    private val database: FusoDatabase,
    private val entryDao: EntryDao,
    private val blockDao: BlockDao,
    private val outboxDao: OutboxDao,
) {

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun sync(): SyncState {
        if (config.url.isBlank()) {
            return updateState(SyncStatus.Failed, "Sync is not configured")
        }
        val session = sessionManager.sessionOnce() ?: return updateState(SyncStatus.SignedOut, "")
        _state.value = _state.value.copy(status = SyncStatus.Running, pendingOps = outboxDao.count())

        val result = runCatching {
            val token = sessionManager.freshAccessToken(config)
                ?: throw com.fuso.core.data.remote.SyncException(com.fuso.core.data.remote.SyncHttpError.Unauthorized)
            pushOutbox(token)
            pullRemote(token)
            Unit
        }

        return result.fold(
            onSuccess = {
                updateState(SyncStatus.Succeeded, System.currentTimeMillis().toString())
                    .copy(pendingOps = outboxDao.count())
            },
            onFailure = { failure ->
                updateState(SyncStatus.Failed, failure.message ?: "Sync failed")
            },
        )
    }

    private suspend fun pushOutbox(accessToken: String) {
        while (true) {
            val batch = outboxDao.nextBatch(BATCH_SIZE)
            if (batch.isEmpty()) return
            val ids = batch.map { it.id }
            val upsertIds = batch.filter { it.opType == OutboxEntity.OP_UPSERT }.map { it.entryId }
            val deleteIds = batch.filter { it.opType == OutboxEntity.OP_DELETE }.map { it.entryId }

            if (upsertIds.isNotEmpty()) {
                val remoteRows = upsertIds.mapNotNull { entryId -> buildRemoteRow(entryId) }
                if (remoteRows.isNotEmpty()) {
                    api.upsertEntries(accessToken, remoteRows).getOrThrow()
                }
            }
            deleteIds.forEach { deleteId ->
                runCatching { api.deleteEntry(accessToken, deleteId).getOrThrow() }
            }
            outboxDao.deleteProcessed(ids)
        }
    }

    private suspend fun buildRemoteRow(entryId: String): RemoteEntry? {
        val details = entryDao.getEntryById(entryId) ?: return null
        val blocks = EntryMappers.parseBlocks(details.blocks.sortedBy { it.position })
        val tags = details.tags.map { it.name }
        val userId = sessionManager.sessionOnce()?.userId ?: return null
        return RemoteEntry(
            id = details.entry.id,
            user_id = userId,
            type = details.entry.type,
            title = details.entry.title,
            blocks_json = EntryMappers.blockJson.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(com.fuso.core.model.BlockContent.serializer()),
                blocks,
            ),
            tags_json = EntryMappers.blockJson.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                tags,
            ),
            is_pinned = details.entry.isPinned,
            is_archived = details.entry.isArchived,
            created_at = formatIso(details.entry.createdAtEpochMillis),
            updated_at = formatIso(details.entry.updatedAtEpochMillis),
            deleted_at = details.entry.deletedAtEpochMillis?.let(::formatIso),
        )
    }

    private suspend fun pullRemote(accessToken: String) {
        val sinceMillis = sessionManager.lastSyncedAtMillis()
        val sinceIso = sinceMillis?.let { formatIso(it - CLOCK_SKEW_MILLIS) }
        val remoteEntries = api.fetchUpdatedSince(accessToken, sinceIso).getOrThrow()
        if (remoteEntries.isEmpty()) return

        database.runInTransaction {
            remoteEntries.forEach { remote -> mergeRemoteIntoLocal(remote) }
        }
        val maxUpdatedAt = remoteEntries.maxOfOrNull { parseIso(it.updated_at) } ?: System.currentTimeMillis()
        sessionManager.setLastSyncedAt(maxUpdatedAt + 1)
    }

    private suspend fun mergeRemoteIntoLocal(remote: RemoteEntry) {
        val local = entryDao.getEntryById(remote.id)
        val remoteUpdated = parseIso(remote.updated_at)
        if (local != null && local.entry.updatedAtEpochMillis >= remoteUpdated && remote.deleted_at == null) return

        if (remote.deleted_at != null) {
            entryDao.softDeleteEntry(remote.id, parseIso(remote.deleted_at))
            return
        }

        val blocks = EntryMappers.blockJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(com.fuso.core.model.BlockContent.serializer()),
            remote.blocks_json,
        )
        val tags = EntryMappers.blockJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(String.serializer()),
            remote.tags_json,
        )

        entryDao.upsertEntries(
            listOf(
                EntryMappers.toEntity(
                    id = remote.id,
                    type = com.fuso.core.model.EntryType.valueOf(remote.type),
                    title = remote.title,
                    blocks = blocks,
                    tags = tags,
                    createdAtMillis = parseIso(remote.created_at),
                    updatedAtMillis = remoteUpdated,
                    isPinned = remote.is_pinned,
                    isArchived = remote.is_archived,
                    deletedAtMillis = null,
                ),
            ),
        )
        blockDao.deleteBlocksNotIn(remote.id, blocks.mapIndexed { position, _ -> "${remote.id}-block-$position" })
        blockDao.upsertBlocks(
            blocks.mapIndexed { position, content ->
                BlockEntity(
                    id = "${remote.id}-block-$position",
                    entryId = remote.id,
                    position = position,
                    type = blockTypeName(content),
                    contentJson = EntryMappers.encodeBlock(content),
                )
            },
        )
        entryDao.clearTagsForEntry(remote.id)
        if (tags.isNotEmpty()) {
            entryDao.insertTags(tags.map { TagEntity(it) })
            entryDao.insertEntryTagRefs(tags.map { EntryTagCrossRef(remote.id, it) })
        }
    }

    private fun blockTypeName(content: com.fuso.core.model.BlockContent): String = when (content) {
        is com.fuso.core.model.BlockContent.Paragraph -> "paragraph"
        is com.fuso.core.model.BlockContent.Heading -> "heading"
        is com.fuso.core.model.BlockContent.Todo -> "todo"
        is com.fuso.core.model.BlockContent.Bullet -> "bullet"
        is com.fuso.core.model.BlockContent.Numbered -> "numbered"
        is com.fuso.core.model.BlockContent.Quote -> "quote"
        com.fuso.core.model.BlockContent.Divider -> "divider"
    }

    private fun updateState(status: SyncStatus, message: String): SyncState {
        val newState = SyncState(
            status = status,
            lastMessage = message,
            pendingOps = _state.value.pendingOps,
        )
        _state.value = newState
        return newState
    }

    private companion object {
        const val BATCH_SIZE = 40
        const val CLOCK_SKEW_MILLIS = 2000L

        fun formatIso(millis: Long): String =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

        fun parseIso(iso: String): Long =
            runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
    }
}
