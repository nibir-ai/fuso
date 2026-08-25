package com.fuso.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuso.core.ui.EmptyState
import com.fuso.core.ui.FadeSlideIn

const val NotesRoute = "notes"

@Composable
fun NotesScreen(
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateNote: () -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotesContent(uiState = uiState, onNoteClick = onNoteClick, onCreateNote = onCreateNote, modifier = modifier)
}

@Composable
private fun NotesContent(
    uiState: NotesUiState,
    onNoteClick: (String) -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoading && uiState.pinnedNotes.isEmpty() && uiState.otherNotes.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FadeSlideIn(index = 1) {
                EmptyState(
                    ornament = Icons.Rounded.PushPin,
                    title = "A wall for your thoughts",
                    body = "Notes live here — lists, ideas, little things worth keeping. Pin the ones you return to.",
                    actionLabel = "Create a note",
                    onAction = onCreateNote,
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.pinnedNotes.isNotEmpty()) {
            sectionHeader(title = "Pinned")
            pinnedItems(uiState = uiState, onNoteClick = onNoteClick)
        }
        if (uiState.otherNotes.isNotEmpty()) {
            sectionHeader(title = if (uiState.pinnedNotes.isEmpty()) "Notes" else "Everything else")
            otherItems(uiState = uiState, onNoteClick = onNoteClick)
        }
    }
}

private fun LazyGridScope.sectionHeader(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "header-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
    }
}

private fun LazyGridScope.pinnedItems(uiState: NotesUiState, onNoteClick: (String) -> Unit) {
    itemsIndexed(
        items = uiState.pinnedNotes,
        key = { _, entry -> entry.id },
    ) { index, note ->
        FadeSlideIn(index = index) {
            NoteCard(note = note, onClick = { onNoteClick(note.id) }, heroKey = "entry-${note.id}")
        }
    }
}

private fun LazyGridScope.otherItems(uiState: NotesUiState, onNoteClick: (String) -> Unit) {
    itemsIndexed(
        items = uiState.otherNotes,
        key = { _, entry -> entry.id },
    ) { index, note ->
        FadeSlideIn(index = uiState.pinnedNotes.size + index) {
            NoteCard(note = note, onClick = { onNoteClick(note.id) }, heroKey = "entry-${note.id}")
        }
    }
}
