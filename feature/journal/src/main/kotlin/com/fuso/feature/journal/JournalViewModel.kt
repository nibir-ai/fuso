package com.fuso.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class JournalDayGroup(
    val date: LocalDate,
    val entries: List<Entry>,
)

data class JournalUiState(
    val isLoading: Boolean = false,
    val groups: List<JournalDayGroup> = emptyList(),
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    entryRepository: EntryRepository,
) : ViewModel() {

    val uiState: StateFlow<JournalUiState> = entryRepository.observeEntries()
        .map { entries ->
            val zone = ZoneId.systemDefault()
            val grouped = entries.groupBy { it.createdAt.atZone(zone).toLocalDate() }
            JournalUiState(
                isLoading = false,
                groups = grouped.entries
                    .sortedByDescending { it.key }
                    .map { (date, dayEntries) -> JournalDayGroup(date, dayEntries) },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = JournalUiState(isLoading = true),
        )
}
