package com.fuso.core.model

import java.time.Instant

enum class EntryType {
    JOURNAL,
    NOTE,
}

data class Entry(
    val id: String,
    val type: EntryType,
    val title: String,
    val preview: String,
    val createdAt: Instant,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val colorIndex: Int? = null,
)
