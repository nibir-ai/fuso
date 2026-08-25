package com.fuso.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.nlp.QuickAddParser
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.intelligence.BehaviorModel
import com.fuso.core.model.BlockContent
import com.fuso.core.model.Entry
import com.fuso.core.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val isLoading: Boolean = false,
    val streak: Int = 0,
    val recentEntries: List<Entry> = emptyList(),
    val weeklyInsight: String? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    behaviorModel: BehaviorModel,
) : ViewModel() {

    private val weeklyInsight = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TodayUiState> = entryRepository.observeEntries()
        .map { entries ->
            TodayUiState(
                isLoading = false,
                streak = calculateStreak(entries),
                recentEntries = entries.take(8),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(isLoading = true),
        )

    val insight: StateFlow<String?> = weeklyInsight.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                behaviorModel.refresh()
                weeklyInsight.value = behaviorModel.weeklyInsight(LocalDate.now())
            }
        }
    }

    fun quickAdd(rawInput: String) {
        if (rawInput.isBlank()) return
        viewModelScope.launch {
            val parsed = QuickAddParser.parse(rawInput)
            val text = parsed.cleanedText.ifBlank { rawInput.trim() }
            runCatching {
                entryRepository.saveEntry(
                    entryId = "e-" + java.util.UUID.randomUUID().toString(),
                    type = if (parsed.isEvent) EntryType.JOURNAL else EntryType.NOTE,
                    title = text,
                    blocks = listOf(BlockContent.Paragraph(text)),
                    tags = buildList {
                        add("quick")
                        parsed.targetDate?.let { add(it.toString()) }
                    },
                    isPinned = false,
                    createdAt = java.time.Instant.now(),
                )
            }
            _justSaved.value = true
            delay(1400)
            _justSaved.value = false
        }
    }

    private fun calculateStreak(entries: List<Entry>): Int {
        val zone = ZoneId.systemDefault()
        val journalDays = entries.asSequence()
            .filter { it.type == EntryType.JOURNAL }
            .map { it.createdAt.atZone(zone).toLocalDate() }
            .toHashSet()
        if (journalDays.isEmpty()) return 0
        var day = LocalDate.now()
        if (!journalDays.contains(day)) day = day.minusDays(1)
        var streak = 0
        while (journalDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
