package com.fuso.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val searchText: String = "",
    val colorIndex: Int? = null,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val position: Int,
    val type: String,
    val contentJson: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagName")],
)
data class EntryTagCrossRef(
    val entryId: String,
    val tagName: String,
)

@Fts4(contentEntity = EntryEntity::class)
@Entity(tableName = "entries_fts")
data class EntryFtsEntity(
    val title: String,
    val searchText: String,
)
