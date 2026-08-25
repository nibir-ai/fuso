package com.fuso.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.model.Entry
import com.fuso.core.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NotesUiState(
    val isLoading: Boolean = false,
    val pinnedNotes: List<Entry> = emptyList(),
    val otherNotes: List<Entry> = emptyList(),
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    entryRepository: EntryRepository,
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> = entryRepository.observeEntries()
        .map { entries ->
            val notes = entries.filter { it.type == EntryType.NOTE }
            NotesUiState(
                isLoading = false,
                pinnedNotes = notes.filter { it.isPinned },
                otherNotes = notes.filterNot { it.isPinned },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(isLoading = true),
        )
}
