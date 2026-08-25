package com.fuso.core.database.pojo

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.fuso.core.database.entity.BlockEntity
import com.fuso.core.database.entity.EntryEntity
import com.fuso.core.database.entity.EntryTagCrossRef
import com.fuso.core.database.entity.TagEntity

data class EntryWithDetails(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val blocks: List<BlockEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagName",
        ),
    )
    val tags: List<TagEntity>,
)
