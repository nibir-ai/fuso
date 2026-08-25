package com.fuso.core.intelligence

import android.content.Context
import com.fuso.core.database.dao.UsageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RhythmProfile(
    val peakHour: Int = -1,
    val peakDay: DayOfWeek? = null,
    val totalWords: Int = 0,
    val learnedEnough: Boolean = false,
)

@Singleton
class BehaviorModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val usageDao: UsageDao,
    private val insightProvider: InsightProvider,
) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _rhythm = MutableStateFlow(RhythmProfile())
    val rhythm: StateFlow<RhythmProfile> = _rhythm.asStateFlow()

    suspend fun refresh() {
        val hours = runCatching { usageDao.hourWeights() }.getOrDefault(emptyList())
        val days = runCatching { usageDao.dayWeights() }.getOrDefault(emptyList())
        val words = runCatching { usageDao.totalWordsWritten() }.getOrDefault(0)
        val sampleWeight = hours.sumOf { it.weight }
        _rhythm.value = RhythmProfile(
            peakHour = hours.maxByOrNull { it.weight }?.value ?: -1,
            peakDay = days.maxByOrNull { it.weight }?.value?.let { DayOfWeek.of(it) },
            totalWords = words,
            learnedEnough = sampleWeight >= MIN_SAMPLES,
        )
    }

    suspend fun suggestedReminderTime(now: LocalDateTime): LocalTime {
        if (_rhythm.value.peakHour < 0) refresh()
        val peak = _rhythm.value.peakHour
        if (peak < 0) return DEFAULT_REMINDER_TIME
        val candidate = LocalTime.of(peak.coerceIn(0, 23), 0)
        return if (candidate <= now.toLocalTime().minusMinutes(30)) {
            candidate.plusHours(NEXT_WINDOW_HOURS).coerceIn(candidate, LATEST_REMINDER_TIME)
        } else {
            candidate
        }
    }

    suspend fun shouldRemindNow(now: LocalDateTime): Boolean {
        if (now.toLocalTime() < EARLIEST_REMINDER_TIME || now.toLocalTime() > LATEST_REMINDER_TIME) return false
        val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        if (usageDao.entriesSavedSince(startOfDay) > 0) return false
        return !prefs.getBoolean(keyFor(now.toLocalDate()), false)
    }

    fun markReminded(date: LocalDate) {
        prefs.edit().putBoolean(keyFor(date), true).apply()
        val cutoff = date.minusDays(PREF_RETENTION_DAYS).toEpochDay()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { key ->
            val epochDay = key.removePrefix(KEY_PREFIX).toLongOrNull()
            if (epochDay != null && epochDay < cutoff) prefs.edit().remove(key).apply()
        }
    }

    suspend fun streakDays(today: LocalDate): Int {
        var day: LocalDate = today.minusDays(1)
        var streak = 0
        while (streak <= MAX_STREAK_SCAN) {
            if (usageDao.entriesSavedSince(day.atStartOfDay(zone).toInstant().toEpochMilli()) == 0) break
            streak++
            day = day.minusDays(1)
        }
        if (usageDao.entriesSavedSince(today.atStartOfDay(zone).toInstant().toEpochMilli()) > 0) streak++
        return streak
    }

    suspend fun lastEntrySavedAt(): Instant? =
        usageDao.lastEntrySavedAtMillis()?.let { Instant.ofEpochMilli(it) }

    suspend fun nudgeMessage(now: LocalDateTime): String {
        val signals = currentSignals(now)
        val synthesized = runCatching { insightProvider.nudgeMessage(signals) }.getOrNull()
        if (!synthesized.isNullOrBlank()) {
            return synthesized.trim().trim('"')
        }
        return localTemplate(signals, now)
    }

    suspend fun weeklyInsight(today: LocalDate): String? {
        val cacheKey = "insight_week_${today.toEpochDay() / 7}"
        prefs.getString(cacheKey, null)?.let { return it }
        val signals = currentSignals(today.atTime(LocalTime.NOON))
        if (signals.totalWords == 0 && signals.peakHour == null) return null
        val text = runCatching { insightProvider.weeklyInsight(signals) }.getOrNull()
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        prefs.edit().putString(cacheKey, text).apply()
        val staleWeek = (today.toEpochDay() / 7) - 8
        prefs.all.keys.filter { it.startsWith("insight_week_") }.forEach { key ->
            val week = key.removePrefix("insight_week_").toLongOrNull()
            if (week != null && week < staleWeek) prefs.edit().remove(key).apply()
        }
        return text
    }

    private suspend fun currentSignals(now: LocalDateTime): RhythmSignals {
        refresh()
        val rhythm = _rhythm.value
        val streak = runCatching { streakDays(now.toLocalDate()) }.getOrDefault(0)
        val last = lastEntrySavedAt()
        val daysSince = last?.let {
            java.time.temporal.ChronoUnit.DAYS.between(it.atZone(zone).toLocalDate(), now.toLocalDate()).toInt()
        } ?: -1
        return RhythmSignals(
            peakHour = rhythm.peakHour.takeIf { it >= 0 },
            peakDay = rhythm.peakDay?.name,
            streakDays = streak,
            entriesThisWeek = usageDao.entriesSavedSince(
                now.toLocalDate().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(),
            ),
            totalWords = rhythm.totalWords,
            daysSinceLastEntry = daysSince,
        )
    }

    private fun localTemplate(signals: RhythmSignals, now: LocalDateTime): String {
        val messages = mutableListOf<String>()
        if (signals.streakDays >= 2) messages += "${signals.streakDays} days in a row — the thread is yours to keep."
        if (signals.peakHour != null) {
            messages += "Around ${signals.peakHour}:00 is usually when you write. Two quiet minutes count."
        }
        if (signals.peakDay == now.dayOfWeek.name && signals.peakHour != null) {
            messages += "${now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())} tends to be one of your writing days."
        }
        messages += listOf(
            "The page is warm and waiting.",
            "A line or two keeps the light on.",
            "Something small happened today. It counts.",
        )
        val index = abs((now.toLocalDate().toEpochDay() * 31 + now.hour).toInt()) % messages.size
        return messages[index]
    }

    private fun keyFor(date: LocalDate): String = KEY_PREFIX + date.toEpochDay()

    private companion object {
        const val PREFS_NAME = "fuso_rhythm"
        const val KEY_PREFIX = "notified_"
        const val MIN_SAMPLES = 6
        const val NEXT_WINDOW_HOURS = 3L
        const val PREF_RETENTION_DAYS = 7L
        const val MAX_STREAK_SCAN = 400
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(20, 0)
        val EARLIEST_REMINDER_TIME: LocalTime = LocalTime.of(8, 0)
        val LATEST_REMINDER_TIME: LocalTime = LocalTime.of(21, 30)
    }
}
