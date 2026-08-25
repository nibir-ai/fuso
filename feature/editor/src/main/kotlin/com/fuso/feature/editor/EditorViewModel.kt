package com.fuso.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.model.BlockContent
import com.fuso.core.model.EntryType
import com.fuso.core.model.MarkStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NewEntryArg = "new"
const val TypeArg = "type"
const val EditorRoutePattern = "editor/{entryId}?type={type}"
fun editorRoute(entryId: String): String = "editor/$entryId"
fun editorRoute(entryId: String, type: EntryType): String = "editor/$entryId?type=${type.name}"

enum class SaveState { Idle, Dirty, Saving, Saved }

data class EditorBlockUi(
    val id: String,
    val content: BlockContent,
)

data class FocusRequest(
    val blockId: String,
    val stamp: Long,
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val entryId: String = "",
    val isExistingEntry: Boolean = false,
    val type: EntryType = EntryType.JOURNAL,
    val title: String = "",
    val blocks: List<EditorBlockUi> = emptyList(),
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val colorIndex: Int? = null,
    val createdAt: Instant = Instant.now(),
    val saveState: SaveState = SaveState.Idle,
    val focusedBlockId: String? = null,
    val focusRequest: FocusRequest? = null,
) {
    val slashMenuBlockId: String?
        get() = focusedBlockId?.takeIf { id ->
            blocks.firstOrNull { it.id == id }?.content?.text?.startsWith("/") == true
        }
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    private val rawArg: String = checkNotNull(savedStateHandle["entryId"])
    private val isNewEntry = rawArg == NewEntryArg
    private val requestedType: EntryType = savedStateHandle.get<String>(TypeArg)
        ?.let { name -> runCatching { EntryType.valueOf(name) }.getOrNull() }
        ?: EntryType.JOURNAL
    private val entryId: String = if (isNewEntry) "e-" + UUID.randomUUID().toString() else rawArg
    private var createdAt: Instant = Instant.now()

    private val selectionRanges = mutableMapOf<String, Pair<Int, Int>>()
    private var saveJob: Job? = null
    private var loaded = false

    private val _uiState = MutableStateFlow(
        EditorUiState(isLoading = true, entryId = entryId),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun setColorIndex(value: Int?) {
        mutate { it.copy(colorIndex = value) }
    }

    init {
        viewModelScope.launch {
            if (isNewEntry) {
                createdAt = Instant.now()
                val first = EditorBlockUi(newBlockId(), BlockContent.Paragraph(""))
                _uiState.value = EditorUiState(
                    isLoading = false,
                    entryId = entryId,
                    type = requestedType,
                    blocks = listOf(first),
                    focusRequest = FocusRequest(first.id, System.nanoTime()),
                )
            } else {
                val entry = entryRepository.observeEntry(entryId).first()
                val blocks = entryRepository.observeBlocks(entryId).first().orEmpty()
                if (entry == null) {
                    _uiState.value = EditorUiState(isLoading = false, entryId = entryId)
                } else {
                    createdAt = entry.createdAt
                    _uiState.value = EditorUiState(
                        isLoading = false,
                        entryId = entryId,
                        isExistingEntry = true,
                        type = entry.type,
                        title = entry.title,
                        tags = entry.tags,
                        isPinned = entry.isPinned,
                        colorIndex = entry.colorIndex,
                        createdAt = entry.createdAt,
                        blocks = if (blocks.isEmpty()) {
                            listOf(EditorBlockUi(newBlockId(), BlockContent.Paragraph("")))
                        } else {
                            blocks.map { EditorBlockUi(newBlockId(), it) }
                        },
                    )
                }
            }
            loaded = true
        }
    }

    fun onTitleChange(value: String) {
        mutate { it.copy(title = value.take(MAX_TITLE_LENGTH)) }
    }

    fun onFocused(blockId: String?) {
        _uiState.update { it.copy(focusedBlockId = blockId) }
    }

    fun onSelectionChanged(blockId: String, start: Int, end: Int) {
        selectionRanges[blockId] = start to end
    }

    fun onBlockTextChanged(blockId: String, value: String) {
        if (!value.contains('\n')) {
            setText(blockId, value)
            return
        }
        val parts = value.split('\n').map { it.trim('\r') }
        setText(blockId, parts.first())
        var anchorId = blockId
        parts.drop(1).forEach { part ->
            anchorId = insertBlockAfter(anchorId, BlockContent.Paragraph(part))
        }
    }

    private fun setText(blockId: String, text: String) {
        mutate { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    if (block.id != blockId) {
                        block
                    } else {
                        block.copy(content = block.content.withText(text))
                    }
                },
            )
        }
    }

    fun convertBlock(blockId: String, make: (String) -> BlockContent) {
        mutate { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    if (block.id != blockId) {
                        block
                    } else {
                        val stripped = block.content.text.removePrefix("/").substringAfter(' ', missingDelimiterValue = "")
                        block.copy(content = make(stripped))
                    }
                },
            )
        }
    }

    fun insertBlockAfter(anchorId: String?, content: BlockContent = BlockContent.Paragraph("")): String {
        val newId = newBlockId()
        mutate { state ->
            val index = anchorId?.let { anchor -> state.blocks.indexOfFirst { it.id == anchor } } ?: -1
            val insertAt = if (index >= 0) index + 1 else state.blocks.size
            val mutable = state.blocks.toMutableList()
            mutable.add(insertAt, EditorBlockUi(newId, content))
            state.copy(blocks = mutable)
        }
        requestFocus(newId)
        return newId
    }

    fun deleteBlockIfEmpty(blockId: String) {
        val state = _uiState.value
        if (state.blocks.size <= 1) return
        val block = state.blocks.firstOrNull { it.id == blockId } ?: return
        if (block.content.text.isNotEmpty()) return
        val index = state.blocks.indexOfFirst { it.id == blockId }
        val previous = state.blocks.getOrNull(index - 1)
        mutate { s -> s.copy(blocks = s.blocks.filterNot { it.id == blockId }) }
        previous?.let { requestFocus(it.id) }
    }

    fun deleteBlock(blockId: String) {
        val state = _uiState.value
        if (state.blocks.size <= 1) return
        val index = state.blocks.indexOfFirst { it.id == blockId }
        val previous = state.blocks.getOrNull(index - 1)
        mutate { s -> s.copy(blocks = s.blocks.filterNot { it.id == blockId }) }
        previous?.let { requestFocus(it.id) }
    }

    fun toggleTodoChecked(blockId: String) {
        mutate { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    val content = block.content
                    if (block.id == blockId && content is BlockContent.Todo) {
                        block.copy(content = content.copy(isChecked = !content.isChecked))
                    } else {
                        block
                    }
                },
            )
        }
    }

    fun applyInlineMark(style: MarkStyle) {
        val state = _uiState.value
        val blockId = state.focusedBlockId ?: return
        val block = state.blocks.firstOrNull { it.id == blockId } ?: return
        val content = block.content as? BlockContent.Paragraph ?: return
        val range = selectionRanges[blockId]
        val textLength = content.text.length
        val (start, end) = if (range == null || range.first == range.second) {
            0 to textLength
        } else {
            range.first.coerceIn(0, textLength) to range.second.coerceIn(0, textLength)
        }
        val hasSameStyle = content.inlineMarks.any { it.style == style && it.start <= start && it.end >= end && end > start }
        val updatedMarks = if (hasSameStyle) {
            content.inlineMarks.filterNot { it.style == style && it.start <= end && it.end >= start }
        } else {
            content.inlineMarks + com.fuso.core.model.InlineMark(start = start, end = end, style = style)
        }
        mutate { s ->
            s.copy(
                blocks = s.blocks.map { b ->
                    if (b.id == blockId) b.copy(content = content.copy(inlineMarks = updatedMarks)) else b
                },
            )
        }
    }

    fun dismissSaveIndicator() = Unit

    fun flushSave(onDone: () -> Unit) {
        if (_uiState.value.saveState == SaveState.Idle) {
            onDone()
            return
        }
        viewModelScope.launch {
            withContext(NonCancellable) {
                persistNow()
            }
            onDone()
        }
    }

    fun deleteEntry(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { entryRepository.softDeleteEntry(entryId) }
            onDone()
        }
    }

    private fun mutate(transform: (EditorUiState) -> EditorUiState) {
        if (!loaded) return
        _uiState.update { transform(it).copy(saveState = SaveState.Dirty) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val state = _uiState.value
        if (!loaded) return
        _uiState.update { it.copy(saveState = SaveState.Saving) }
        runCatching {
            entryRepository.saveEntry(
                entryId = entryId,
                type = state.type,
                title = state.title,
                blocks = state.blocks.map { it.content },
                tags = state.tags,
                isPinned = state.isPinned,
                colorIndex = state.colorIndex,
                createdAt = createdAt,
            )
        }
        _uiState.update { it.copy(saveState = SaveState.Saved) }
    }

    private fun requestFocus(blockId: String) {
        _uiState.update { it.copy(focusRequest = FocusRequest(blockId, System.nanoTime())) }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MILLIS = 700L
        const val MAX_TITLE_LENGTH = 120

        fun newBlockId(): String = "b-" + UUID.randomUUID().toString()
    }
}

private fun BlockContent.withText(text: String): BlockContent = when (this) {
    is BlockContent.Paragraph -> copy(text = text)
    is BlockContent.Heading -> copy(text = text)
    is BlockContent.Todo -> copy(text = text)
    is BlockContent.Bullet -> copy(text = text)
    is BlockContent.Numbered -> copy(text = text)
    is BlockContent.Quote -> copy(text = text)
    BlockContent.Divider -> this
}
