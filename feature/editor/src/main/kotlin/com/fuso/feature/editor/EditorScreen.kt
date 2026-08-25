package com.fuso.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.model.BlockContent
import com.fuso.core.model.MarkStyle
import com.fuso.core.ui.heroBounds

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.flushSave(onBack)
    }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Move to bin?") },
            text = { Text("This entry will be kept in the bin for 30 days in case you change your mind.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteEntry(onDone = onBack)
                }) { Text("Move to bin") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) { Text("Keep writing") }
            },
        )
    }

    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }

    LaunchedEffect(state.focusRequest) {
        val target = state.focusRequest ?: return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        focusRequesters[target.blockId]?.requestFocus()
    }

    Scaffold(
        modifier = modifier
            .heroBounds(heroKeyFor(state))
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                saveState = state.saveState,
                onBack = { viewModel.flushSave(onBack) },
                onDelete = { showDeleteDialog = true },
            )
        },
        bottomBar = {
            Column {
                androidx.compose.animation.AnimatedVisibility(visible = !state.isLoading && state.type == com.fuso.core.model.EntryType.NOTE) {
                    NoteSwatchRow(
                        selected = state.colorIndex,
                        onPick = viewModel::setColorIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                EditorToolbar(
                activeKind = activeBlockKind(state),
                enabled = !state.isLoading,
                onConvertHeading1 = { convertFocused(state, viewModel) { text -> BlockContent.Heading(1, text) } },
                onConvertHeading2 = { convertFocused(state, viewModel) { text -> BlockContent.Heading(2, text) } },
                onBold = { viewModel.applyInlineMark(MarkStyle.BOLD) },
                onItalic = { viewModel.applyInlineMark(MarkStyle.ITALIC) },
                onBullet = { convertFocused(state, viewModel) { text -> BlockContent.Bullet(text) } },
                onTodo = { convertFocused(state, viewModel) { text -> BlockContent.Todo(text) } },
                onQuote = { convertFocused(state, viewModel) { text -> BlockContent.Quote(text) } },
                onTidy = { viewModel.tidyFocusedBlock() },
                onAddBlock = { viewModel.insertBlockAfter(state.focusedBlockId) },
            )
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TitleField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (state.tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        items(items = state.tags, key = { it }) { tag ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    blocksWithEditors(
                        blocks = state.blocks,
                        focusRequesters = focusRequesters,
                        slashMenuBlockId = state.slashMenuBlockId,
                        onTextChange = viewModel::onBlockTextChanged,
                        onFocusChange = { blockId, focused -> viewModel.onFocused(if (focused) blockId else null) },
                        onSelectionChange = viewModel::onSelectionChanged,
                        onBackspaceAtStart = viewModel::deleteBlockIfEmpty,
                        onToggleCheck = viewModel::toggleTodoChecked,
                        onSlashSelect = { blockId, option -> viewModel.convertBlock(blockId, option.make) },
                        onForceDelete = viewModel::deleteBlock,
                    )
                }
            }
        }
    }
}

private fun convertFocused(
    state: EditorUiState,
    viewModel: EditorViewModel,
    make: (String) -> BlockContent,
) {
    state.focusedBlockId?.let { blockId -> viewModel.convertBlock(blockId, make) }
}

private fun heroKeyFor(state: EditorUiState): String? =
    if (!state.isLoading && state.isExistingEntry) "entry-${state.entryId}" else null

private fun activeBlockKind(state: EditorUiState): String? {
    val focused = state.blocks.firstOrNull { it.id == state.focusedBlockId }?.content ?: return null
    return when (focused) {
        is BlockContent.Paragraph -> "p"
        is BlockContent.Heading -> "h${focused.level}"
        is BlockContent.Todo -> "todo"
        is BlockContent.Bullet -> "bullet"
        is BlockContent.Numbered -> "numbered"
        is BlockContent.Quote -> "quote"
        BlockContent.Divider -> "divider"
    }
}

private fun LazyListScope.blocksWithEditors(
    blocks: List<EditorBlockUi>,
    focusRequesters: MutableMap<String, FocusRequester>,
    slashMenuBlockId: String?,
    onTextChange: (String, String) -> Unit,
    onFocusChange: (String, Boolean) -> Unit,
    onSelectionChange: (String, Int, Int) -> Unit,
    onBackspaceAtStart: (String) -> Unit,
    onToggleCheck: (String) -> Unit,
    onSlashSelect: (String, SlashOption) -> Unit,
    onForceDelete: (String) -> Unit,
) {
    items(items = blocks, key = { it.id }, contentType = { "block" }) { block ->
        val requester = remember(block.id) { FocusRequester() }.also { focusRequesters[block.id] = it }
        Box(modifier = Modifier.animateItem()) {
            EditorBlockRow(
                block = block,
                focusRequester = requester,
                onTextChange = { text -> onTextChange(block.id, text) },
                onFocusChange = { focused -> onFocusChange(block.id, focused) },
                onSelectionChange = { start, end -> onSelectionChange(block.id, start, end) },
                onBackspaceAtStart = { onBackspaceAtStart(block.id) },
                onToggleCheck = { onToggleCheck(block.id) },
                onDelete = { onForceDelete(block.id) },
            )
            if (slashMenuBlockId == block.id) {
                SlashPopup(
                    query = block.content.text.removePrefix("/"),
                    onSelect = { option -> onSlashSelect(block.id, option) },
                )
            }
        }
    }
}

@Composable
private fun SlashPopup(query: String, onSelect: (SlashOption) -> Unit) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 46),
        properties = PopupProperties(focusable = false),
    ) {
        SlashMenuPanel(query = query, onSelect = onSelect)
    }
}

@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.outline),
                    )
                }
                innerField()
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditorTopBar(saveState: SaveState, onBack: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        SaveIndicator(saveState = saveState)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Move to bin",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteSwatchRow(selected: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.fuso.core.designsystem.theme.NoteColors.palette.forEach { noteColor ->
            val isSelected = selected == noteColor.index
            Surface(
                shape = CircleShape,
                color = if (dark) noteColor.dark else noteColor.light,
                border = if (isSelected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                },
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onPick(if (isSelected) null else noteColor.index) },
            ) {}
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Colour",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveIndicator(saveState: SaveState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = saveState,
        transitionSpec = {
            (fadeIn(tween(FusoMotion.DurationShort)) togetherWith fadeOut(tween(FusoMotion.DurationShort)))
        },
        label = "saveIndicator",
        modifier = modifier,
    ) { target ->
        when (target) {
            SaveState.Idle -> Box(modifier = Modifier.size(1.dp))
            SaveState.Dirty -> Text(
                text = "Editing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SaveState.Saving -> Text(
                text = "Saving…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SaveState.Saved -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    activeKind: String?,
    enabled: Boolean,
    onConvertHeading1: () -> Unit,
    onConvertHeading2: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onBullet: () -> Unit,
    onTodo: () -> Unit,
    onQuote: () -> Unit,
    onTidy: () -> Unit,
    onAddBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 10.dp) {
        Row(
            modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolTextButton(label = "H1", isActive = activeKind == "h1", isEnabled = enabled, onClick = onConvertHeading1)
            ToolTextButton(label = "H2", isActive = activeKind == "h2", isEnabled = enabled, onClick = onConvertHeading2)
            ToolIconButton(icon = Icons.Rounded.FormatBold, contentDescription = "Bold", isActive = false, isEnabled = enabled, onClick = onBold)
            ToolIconButton(icon = Icons.Rounded.FormatItalic, contentDescription = "Italic", isActive = false, isEnabled = enabled, onClick = onItalic)
            ToolIconButton(icon = Icons.Rounded.FormatListBulleted, contentDescription = "Bullet list", isActive = activeKind == "bullet" || activeKind == "numbered", isEnabled = enabled, onClick = onBullet)
            ToolIconButton(icon = Icons.Rounded.Checklist, contentDescription = "To-do", isActive = activeKind == "todo", isEnabled = enabled, onClick = onTodo)
            ToolIconButton(icon = Icons.Rounded.FormatQuote, contentDescription = "Quote", isActive = activeKind == "quote", isEnabled = enabled, onClick = onQuote)
            ToolTextButton(label = "Tidy", isActive = false, isEnabled = enabled, onClick = onTidy)
            Spacer(modifier = Modifier.weight(1f))
            ToolIconButton(icon = Icons.Rounded.Add, contentDescription = "Add block", isActive = false, isEnabled = enabled, onClick = onAddBlock)
        }
    }
}

@Composable
private fun ToolTextButton(label: String, isActive: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .alpha(if (isEnabled) 1f else 0.35f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
