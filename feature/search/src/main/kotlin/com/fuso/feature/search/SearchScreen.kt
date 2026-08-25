package com.fuso.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.model.Entry
import com.fuso.core.ui.EntryCard
import com.fuso.core.ui.FadeSlideIn

const val SearchRoute = "search"

@Composable
fun SearchScreen(
    onEntryClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            SearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.query.isBlank() -> EmptyHints(modifier = Modifier.fillMaxSize())
            state.hasSearched && state.results.isEmpty() -> NoResults(
                query = state.query,
                modifier = Modifier.fillMaxSize(),
            )
            else -> ResultsList(
                results = state.results,
                query = state.query,
                onEntryClick = onEntryClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.padding(end = 16.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Search entries, notes, tags…",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline),
                            )
                        }
                        innerField()
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(22.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

@Composable
private fun ResultsList(
    results: List<Entry>,
    query: String,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "count") {
            Text(
                text = if (results.size == 1) "1 result" else "${results.size} results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexedCompat(results = results, query = query, onEntryClick = onEntryClick)
    }
}

@Composable
private fun NoResults(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nothing found",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "No matches for \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHints(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Search everything you've written",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Titles, body text and tags are all searched instantly — try \"rain\", \"books\" or \"Mum\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    results: List<Entry>,
    query: String,
    onEntryClick: (String) -> Unit,
) {
    items(items = results, key = { it.id }, contentType = { "result" }) { entry ->
        val index = results.indexOf(entry)
        FadeSlideIn(index = index.coerceAtMost(FusoMotion.MaxStaggerSteps)) {
            Column {
                EntryCard(
                    entry = entry,
                    onClick = { onEntryClick(entry.id) },
                )
                if (index == 0 && query.isNotBlank()) {
                    HighlightPreview(entry = entry, query = query)
                }
            }
        }
    }
}

@Composable
private fun HighlightPreview(entry: Entry, query: String) {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return
    val preview = entry.preview.ifBlank { entry.title }
    val annotated = remember(preview, tokens) { highlight(preview, tokens) } ?: return
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = 1,
    )
}

internal fun queryTokens(query: String): List<String> =
    query.split(WHITESPACE).filter { it.length >= 2 }

internal fun highlight(text: String, tokens: List<String>): AnnotatedString? {
    if (text.isBlank()) return null
    val lowerText = text.lowercase()
    data class Range(val start: Int, val end: Int)

    val ranges = buildList {
        tokens.forEach { token ->
            val tokenLower = token.lowercase()
            var from = 0
            while (true) {
                val found = lowerText.indexOf(tokenLower, from)
                if (found < 0) break
                add(Range(found, found + tokenLower.length))
                from = found + tokenLower.length
            }
        }
    }.sortedBy { it.start }

    val merged = mutableListOf<Range>()
    for (range in ranges) {
        val last = merged.lastOrNull()
        if (last != null && range.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, range.end))
        } else {
            merged.add(range)
        }
    }
    if (merged.isEmpty()) return null

    val builder = AnnotatedString.Builder()
    var cursor = 0
    merged.forEach { range ->
        if (range.start > cursor) builder.append(text.substring(cursor, range.start))
        builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        builder.append(text.substring(range.start, range.end))
        builder.pop()
        cursor = range.end
    }
    if (cursor < text.length) builder.append(text.substring(cursor))
    return builder.toAnnotatedString()
}

private val WHITESPACE = Regex("\\s+")
