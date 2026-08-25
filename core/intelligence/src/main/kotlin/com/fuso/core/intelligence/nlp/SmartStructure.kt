package com.fuso.core.intelligence.nlp

import com.fuso.core.model.BlockContent

/**
 * On-device structural intelligence: turns messy free-typed text into
 * tidy lists without ever leaving the phone.
 */
object SmartStructure {

    enum class DetectorKind { TODO, BULLET, NUMBERED }

    private val BULLET_PREFIX = Regex("""^\s*[-•*–—]\s+""")
    private val TODO_PREFIX = Regex("""^\s*(?:\[( |x|X|✓|✔)]|☐\s*)\s*""")
    private val NUMBERED_PREFIX = Regex("""^\s*(\d{1,3})[.)\]]\s+""")

    /**
     * Decides whether the given text looks like an intentional list and of which kind.
     * Conservative: requires a clear majority of lines to carry list markers.
     */
    fun detectKind(text: String): DetectorKind? {
        val nonEmpty = text.lines().filter { it.isNotBlank() }
        if (nonEmpty.size < 2) return null
        var bullets = 0
        var todos = 0
        var numbered = 0
        nonEmpty.forEach { line ->
            when {
                TODO_PREFIX.containsMatchIn(line) -> todos++
                NUMBERED_PREFIX.containsMatchIn(line) -> numbered++
                BULLET_PREFIX.containsMatchIn(line) -> bullets++
            }
        }
        val threshold = (nonEmpty.size * 0.6f).toInt().coerceAtLeast(2)
        return when {
            todos >= threshold -> DetectorKind.TODO
            numbered >= threshold -> DetectorKind.NUMBERED
            bullets >= threshold -> DetectorKind.BULLET
            else -> null
        }
    }

    /**
     * Converts free-typed text into structured blocks.
     * Falls back to a single polished paragraph when no list intent is found.
     */
    fun toBlocks(text: String): List<BlockContent> {
        val polished = polish(text)
        val kind = detectKind(polished)
        val lines = polished.lines().filter { it.isNotBlank() }
        if (kind == null || lines.size < 2) {
            return listOf(BlockContent.Paragraph(polished.trim()))
        }
        return when (kind) {
            DetectorKind.BULLET -> lines.map { line ->
                BlockContent.Bullet(line.replace(BULLET_PREFIX, "").trim())
            }
            DetectorKind.TODO -> lines.map { line ->
                val checked = Regex("""\[x|X|✓|✔]""").containsMatchIn(line)
                BlockContent.Todo(line.replace(TODO_PREFIX, "").trim(), isChecked = checked)
            }
            DetectorKind.NUMBERED -> lines.mapIndexed { index, line ->
                BlockContent.Numbered(line.replace(NUMBERED_PREFIX, "").trim(), index = index + 1)
            }
        }
    }

    private val COMMON_TYPOS = mapOf(
        "teh" to "the",
        "recieve" to "receive",
        "seperate" to "separate",
        "definately" to "definitely",
        "occured" to "occurred",
        "untill" to "until",
        "wich" to "which",
        "becuase" to "because",
        "alot" to "a lot",
        "tommorrow" to "tomorrow",
        "wierd" to "weird",
        "freind" to "friend",
        "acheive" to "achieve",
        "calender" to "calendar",
        "journel" to "journal",
    )

    private val SENTENCE_BOUNDARY = Regex("""([.!?]\s+)([a-z])""")

    /**
     * Lightweight local polish: typo fixes, spacing hygiene, sentence capitalisation.
     * Deliberately conservative — never rewrites meaning.
     */
    fun polish(text: String): String {
        var result = text
        COMMON_TYPOS.forEach { (wrong, right) ->
            result = result.replace(Regex("""(?i)\b$wrong\b"""), right)
        }
        result = result.replace(Regex(""" {2,}"""), " ")
        result = result.replace(Regex("""\s+([,.!?;:])"""), "$1")
        result = result.replace(Regex("""([,.!?;:])(?=[A-Za-z])"""), "$1 ")
        result = result.replace(Regex("""^(\s*)([a-z])""")) { match ->
            "${match.groupValues[1]}${match.groupValues[2].uppercase()}"
        }
        result = SENTENCE_BOUNDARY.replace(result) { match ->
            match.value.dropLast(1) + match.groupValues.last().uppercase()
        }
        return result.trim()
    }
}
