package com.fuso.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_events",
    indices = [Index("timestampEpochMillis"), Index("type")],
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timestampEpochMillis: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val wordCount: Int = 0,
    val detail: String = "",
) {
    companion object {
        const val TYPE_APP_OPEN = "APP_OPEN"
        const val TYPE_ENTRY_SAVED = "ENTRY_SAVED"

        fun of(type: String, atMillis: Long, zone: java.time.ZoneId, wordCount: Int = 0, detail: String = ""): UsageEventEntity {
            val dateTime = java.time.Instant.ofEpochMilli(atMillis).atZone(zone)
            return UsageEventEntity(
                type = type,
                timestampEpochMillis = atMillis,
                hourOfDay = dateTime.hour,
                dayOfWeek = dateTime.dayOfWeek.value,
                wordCount = wordCount,
                detail = detail,
            )
        }
    }
}
