package com.fuso.core.designsystem.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppearanceController(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM),
    )

    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(value: ThemeMode) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
        _mode.value = value
    }

    private companion object {
        const val PREFS_NAME = "fuso_appearance"
        const val KEY_MODE = "theme_mode"
    }
}
