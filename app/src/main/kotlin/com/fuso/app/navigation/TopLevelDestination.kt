package com.fuso.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.fuso.feature.calendar.CalendarRoute
import com.fuso.feature.journal.JournalRoute
import com.fuso.feature.notes.NotesRoute
import com.fuso.feature.search.SearchRoute
import com.fuso.feature.today.TodayRoute

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY(TodayRoute, "Today", Icons.Rounded.Today),
    JOURNAL(JournalRoute, "Journal", Icons.Rounded.EditNote),
    CALENDAR(CalendarRoute, "Calendar", Icons.Rounded.CalendarMonth),
    NOTES(NotesRoute, "Notes", Icons.AutoMirrored.Rounded.Notes),
    SEARCH(SearchRoute, "Search", Icons.Rounded.Search),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
