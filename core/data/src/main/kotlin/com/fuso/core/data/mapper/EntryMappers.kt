package com.fuso.core.data.mapper

import com.fuso.core.database.entity.BlockEntity
import com.fuso.core.database.entity.EntryEntity
import com.fuso.core.database.pojo.EntryWithDetails
import com.fuso.core.model.BlockContent
import com.fuso.core.model.Entry
import com.fuso.core.model.EntryType
import java.time.Instant
import kotlinx.serialization.json.Json

object EntryMappers {

    val blockJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun toDomain(details: EntryWithDetails): Entry = Entry(
        id = details.entry.id,
        type = EntryType.valueOf(details.entry.type),
        title = details.entry.title,
        preview = derivePreview(details.blocks.sortedBy { it.position }.mapNotNull { parseBlock(it.contentJson) }),
        createdAt = Instant.ofEpochMilli(details.entry.createdAtEpochMillis),
        tags = details.tags.map { it.name },
        isPinned = details.entry.isPinned,
        colorIndex = details.entry.colorIndex,
    )

    fun toDomainList(details: List<EntryWithDetails>): List<Entry> = details.map(::toDomain)

    fun derivePreview(blocks: List<BlockContent>): String =
        blocks.firstOrNull { it.text.isNotBlank() }?.text ?: ""

    fun parseBlock(contentJson: String): BlockContent? = runCatching {
        blockJson.decodeFromString(BlockContent.serializer(), contentJson)
    }.getOrNull()

    fun parseBlocks(blockEntities: List<BlockEntity>): List<BlockContent> =
        blockEntities.sortedBy { it.position }.mapNotNull { parseBlock(it.contentJson) }

    fun encodeBlock(content: BlockContent): String =
        blockJson.encodeToString(BlockContent.serializer(), content)

    fun buildSearchText(title: String, blocks: List<BlockContent>, tags: List<String>): String =
        (listOf(title) + blocks.map { it.text } + tags)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")

    fun toEntity(
        id: String,
        type: EntryType,
        title: String,
        blocks: List<BlockContent>,
        tags: List<String>,
        createdAtMillis: Long,
        updatedAtMillis: Long,
        isPinned: Boolean,
        isArchived: Boolean = false,
        deletedAtMillis: Long? = null,
    ): EntryEntity = EntryEntity(
        id = id,
        type = type.name,
        title = title,
        createdAtEpochMillis = createdAtMillis,
        updatedAtEpochMillis = updatedAtMillis,
        deletedAtEpochMillis = deletedAtMillis,
        isPinned = isPinned,
        isArchived = isArchived,
        searchText = buildSearchText(title, blocks, tags),
    )
}
