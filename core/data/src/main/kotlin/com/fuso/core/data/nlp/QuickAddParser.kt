package com.fuso.core.data.nlp

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

data class ParsedQuickAdd(
    val original: String,
    val cleanedText: String,
    val targetDate: LocalDate?,
    val time: LocalTime?,
    val isEvent: Boolean,
    val spans: List<Span>,
) {
    data class Span(val startInOriginal: Int, val endInOriginal: Int, val kind: Kind)

    enum class Kind { DATE, TIME }

    val hasDateTimeSignal: Boolean get() = targetDate != null || time != null
}

object QuickAddParser {

    private val DATE_WORDS = mapOf(
        "today" to 0L,
        "tonight" to 0L,
        "tomorrow" to 1L,
        "tmr" to 1L,
        "tmrw" to 1L,
    )

    private val WEEKDAYS = DayOfWeek.entries.associateBy {
        it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH)
    } + DayOfWeek.entries.associateBy {
        it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ENGLISH).removeSuffix(".")
    }

    private val TIME_REGEX = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    private val HOUR_ONLY_REGEX = Regex("""\bat\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val CLOCK_24_REGEX = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")

    private val EVENT_HINTS = listOf(
        "meet", "meeting", "lunch", "dinner", "breakfast", "call", "appointment",
        "interview", "birthday", "party", "flight", "train", "doctor", "dentist",
        "gym", "yoga", "standup", "review", "wedding", "concert",
    )

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedQuickAdd {
        val lower = input.lowercase(Locale.ENGLISH)
        val spans = mutableListOf<ParsedQuickAdd.Span>()
        var targetDate: LocalDate? = null
        var time: LocalTime? = null

        // Explicit dates
        DATE_WORDS.forEach { (word, offset) ->
            val index = lower.indexOf(word)
            if (index >= 0 && isWordBoundary(lower, index, word.length)) {
                targetDate = today.plusDays(offset)
                spans += ParsedQuickAdd.Span(index, index + word.length, ParsedQuickAdd.Kind.DATE)
            }
        }

        // Weekdays (first occurrence wins if no explicit date yet)
        if (targetDate == null) {
            WEEKDAYS.forEach { (word, dayOfWeek) ->
                val index = lower.indexOf(" $word")
                val realIndex = if (index >= 0) index + 1 else lower.indexOf(word).takeIf {
                    it == 0 || (it > 0 && !lower[it - 1].isLetter())
                } ?: -1
                if (realIndex >= 0 && isWordBoundary(lower, realIndex, word.length)) {
                    var delta = ((dayOfWeek.value - today.dayOfWeek.value + 7) % 7).toLong()
                    if (delta == 0L) delta = 7
                    targetDate = today.plusDays(delta)
                    spans += ParsedQuickAdd.Span(realIndex, realIndex + word.length, ParsedQuickAdd.Kind.DATE)
                }
            }
        }

        // Times
        TIME_REGEX.find(lower)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
            val meridiem = match.groupValues[3].lowercase()
            time = LocalTime.of(
                when {
                    meridiem == "am" && hour == 12 -> 0
                    meridiem == "pm" && hour != 12 -> hour + 12
                    else -> hour
                }.coerceIn(0, 23),
                minute.coerceIn(0, 59),
            )
            spans += ParsedQuickAdd.Span(match.range.first, match.range.last + 1, ParsedQuickAdd.Kind.TIME)
        }

        if (time == null) {
            CLOCK_24_REGEX.find(lower)?.let { match ->
                time = LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                spans += ParsedQuickAdd.Span(match.range.first, match.range.last + 1, ParsedQuickAdd.Kind.TIME)
            }
        }

        if (time == null) {
            HOUR_ONLY_REGEX.find(lower)?.let { match ->
                val hour = match.groupValues[1].toInt()
                if (hour in 1..12) {
                    val inferred = if (hour in 1..7) hour + 12 else hour
                    time = LocalTime.of(inferred.coerceAtMost(23), 0)
                    spans += ParsedQuickAdd.Span(match.range.first, match.range.last + 1, ParsedQuickAdd.Kind.TIME)
                }
            }
        }

        val isEvent = time != null || EVENT_HINTS.any { hint ->
            lower.split(Regex("\\s+")).any { it.trim('.', ',', '!') == hint }
        }

        return ParsedQuickAdd(
            original = input,
            cleanedText = clean(input),
            targetDate = targetDate,
            time = time,
            isEvent = isEvent,
            spans = spans.sortedBy { it.startInOriginal },
        )
    }

    fun describe(parsed: ParsedQuickAdd): String? {
        val parts = mutableListOf<String>()
        parsed.targetDate?.let { date ->
            val today = LocalDate.now()
            parts += when (date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                    ", " + date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            }
        }
        parsed.time?.let { time ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            parts += time.format(formatter).uppercase()
        }
        if (parts.isEmpty()) return null
        return (if (parsed.isEvent) "Event · " else "") + parts.joinToString(" · ")
    }

    private fun clean(input: String): String =
        input.replace(Regex("""\b(?:at\s+)?\d{1,2}(?::\d{2})?\s*(am|pm)\b""", RegexOption.IGNORE_CASE), "")
            .replace(CLOCK_24_REGEX, "")
            .replace(HOUR_ONLY_REGEX, "")
            .replace(DATE_WORDS.keys.joinToString("|") { Regex.escape(it) }, "")
            .replace(WEEKDAYS.keys.joinToString("|") { Regex.escape(it) }, "")
            .replace(Regex("""\bon\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', ',', '.', '-', '\n')

    private fun isWordBoundary(text: String, start: Int, length: Int): Boolean {
        val beforeOk = start == 0 || !text[start - 1].isLetter()
        val afterIndex = start + length
        val afterOk = afterIndex >= text.length || !text[afterIndex].isLetter()
        return beforeOk && afterOk
    }
}
