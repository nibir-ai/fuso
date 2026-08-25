package com.fuso.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class SlashOption(
    val label: String,
    val icon: ImageVector,
    val make: (String) -> BlockContentAlias,
)

typealias BlockContentAlias = com.fuso.core.model.BlockContent

private val slashOptions = listOf(
    SlashOption("Text", Icons.Rounded.ShortText) { com.fuso.core.model.BlockContent.Paragraph(it) },
    SlashOption("Heading 1", Icons.Rounded.Title) { com.fuso.core.model.BlockContent.Heading(1, it) },
    SlashOption("Heading 2", Icons.Rounded.Title) { com.fuso.core.model.BlockContent.Heading(2, it) },
    SlashOption("To-do", Icons.Rounded.Checklist) { com.fuso.core.model.BlockContent.Todo(it) },
    SlashOption("Bullet list", Icons.Rounded.FormatListBulleted) { com.fuso.core.model.BlockContent.Bullet(it) },
    SlashOption("Numbered list", Icons.Rounded.FormatListNumbered) { com.fuso.core.model.BlockContent.Numbered(it, 1) },
    SlashOption("Quote", Icons.Rounded.FormatQuote) { com.fuso.core.model.BlockContent.Quote(it) },
    SlashOption("Divider", Icons.Rounded.HorizontalRule) { com.fuso.core.model.BlockContent.Divider },
)

@Composable
fun SlashMenuPanel(
    query: String,
    onSelect: (SlashOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) slashOptions else slashOptions.filter { it.label.contains(q, ignoreCase = true) }
    }

    Surface(
        modifier = modifier.width(236.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = "No matching blocks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            filtered.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
