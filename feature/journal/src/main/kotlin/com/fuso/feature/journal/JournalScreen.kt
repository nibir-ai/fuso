package com.fuso.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuso.core.model.Entry
import com.fuso.core.ui.EntryCard
import com.fuso.core.ui.EmptyState
import com.fuso.core.ui.FadeSlideIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

const val JournalRoute = "journal"

@Composable
fun JournalScreen(
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStartWriting: () -> Unit = {},
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalContent(
        uiState = uiState,
        onEntryClick = onEntryClick,
        onStartWriting = onStartWriting,
        modifier = modifier,
    )
}

@Composable
private fun JournalContent(
    uiState: JournalUiState,
    onEntryClick: (String) -> Unit,
    onStartWriting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoading && uiState.groups.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadeSlideIn(index = 1) {
                EmptyState(
                    ornament = Icons.AutoMirrored.Rounded.Notes,
                    title = "Your story starts here",
                    body = "Every page you write becomes part of the trail. The first one is the easiest — one honest line is enough.",
                    actionLabel = "Write your first entry",
                    onAction = onStartWriting,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        uiState.groups.forEachIndexed { groupIndex, group ->
            stickyHeader(key = "header-${group.date.toEpochDay()}", contentType = "header") {
                DayHeader(date = group.date, entryCount = group.entries.size)
            }
            items(
                items = group.entries,
                key = { it.id },
                contentType = { "entry" },
            ) { entry ->
                val flatIndex = group.entries.indexOf(entry)
                if (groupIndex == 0 && flatIndex < 4) {
                    FadeSlideIn(index = flatIndex) {
                        EntryCard(entry = entry, onClick = { onEntryClick(entry.id) }, heroKey = "entry-${entry.id}")
                    }
                } else {
                    EntryCard(entry = entry, onClick = { onEntryClick(entry.id) }, heroKey = "entry-${entry.id}")
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, entryCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " · " +
                    if (entryCount == 1) "1 entry" else "$entryCount entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
