package com.fuso.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class BlockContent {

    abstract val text: String

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        override val text: String,
        val inlineMarks: List<InlineMark> = emptyList(),
    ) : BlockContent()

    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,
        override val text: String,
    ) : BlockContent() {
        init {
            require(level in 1..3) { "Heading level must be 1..3" }
        }
    }

    @Serializable
    @SerialName("todo")
    data class Todo(
        override val text: String,
        val isChecked: Boolean = false,
    ) : BlockContent()

    @Serializable
    @SerialName("bullet")
    data class Bullet(override val text: String) : BlockContent()

    @Serializable
    @SerialName("numbered")
    data class Numbered(override val text: String, val index: Int) : BlockContent()

    @Serializable
    @SerialName("quote")
    data class Quote(
        override val text: String,
        val attribution: String? = null,
    ) : BlockContent()

    @Serializable
    @SerialName("divider")
    data object Divider : BlockContent() {
        override val text: String = ""
    }
}

@Serializable
data class InlineMark(
    val start: Int,
    val end: Int,
    val style: MarkStyle,
)

@Serializable
enum class MarkStyle {
    BOLD,
    ITALIC,
    HIGHLIGHT,
}
