package com.fuso.core.designsystem.theme

import androidx.compose.ui.graphics.Color

data class NoteColor(val index: Int, val light: Color, val dark: Color)

object NoteColors {

    val palette: List<NoteColor> = listOf(
        NoteColor(0, Color(0xFFFFDCCB), Color(0xFF4A322A)),
        NoteColor(1, Color(0xFFE7EBC8), Color(0xFF3A3F2C)),
        NoteColor(2, Color(0xFFFFF0BE), Color(0xFF463D22)),
        NoteColor(3, Color(0xFFD9E9F7), Color(0xFF2A3B4A)),
        NoteColor(4, Color(0xFFEBDDF4), Color(0xFF3E3247)),
        NoteColor(5, Color(0xFFDBEEE1), Color(0xFF28402F)),
    )

    fun color(index: Int?, dark: Boolean): Color? =
        index?.let { i -> palette.getOrNull(i)?.let { if (dark) it.dark else it.light } }
}
