package com.fuso.core.data.mood

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setMood(date: LocalDate, mood: Int) {
        require(mood in 1..5) { "Mood must be 1..5" }
        prefs.edit().putInt(keyFor(date), mood).apply()
    }

    fun moodFor(date: LocalDate): Int? {
        val value = prefs.getInt(keyFor(date), -1)
        return if (value in 1..5) value else null
    }

    fun moodsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Int> {
        val result = mutableMapOf<LocalDate, Int>()
        var date = start
        while (!date.isAfter(endInclusive)) {
            moodFor(date)?.let { result[date] = it }
            date = date.plusDays(1)
        }
        return result
    }

    private fun keyFor(date: LocalDate): String = KEY_PREFIX + date.toEpochDay()

    private companion object {
        const val PREFS_NAME = "fuso_mood"
        const val KEY_PREFIX = "mood_"
    }
}
