package com.fuso.feature.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.model.BlockContent
import com.fuso.core.model.InlineMark
import com.fuso.core.model.MarkStyle

@Composable
fun EditorBlockRow(
    block: EditorBlockUi,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onToggleCheck: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
) {
    val content = block.content
    when (content) {
        is BlockContent.Paragraph -> BlockTextField(
            value = content.text,
            marks = content.inlineMarks,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            hint = "Start writing…",
            focusRequester = focusRequester,
            onTextChange = onTextChange,
            onFocusChange = onFocusChange,
            onSelectionChange = onSelectionChange,
            onBackspaceAtStart = onBackspaceAtStart,
            modifier = modifier,
        )
        is BlockContent.Heading -> BlockTextField(
            value = content.text,
            textStyle = when (content.level) {
                1 -> MaterialTheme.typography.headlineLarge
                2 -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.headlineSmall
            }.copy(color = MaterialTheme.colorScheme.onSurface),
            hint = "Heading",
            focusRequester = focusRequester,
            onTextChange = onTextChange,
            onFocusChange = onFocusChange,
            onSelectionChange = onSelectionChange,
            onBackspaceAtStart = onBackspaceAtStart,
            modifier = modifier,
        )
        is BlockContent.Todo -> Row(modifier = modifier) {
            FusoTodoCheckbox(
                isChecked = content.isChecked,
                onToggle = onToggleCheck,
                modifier = Modifier.padding(top = 3.dp),
            )
            BlockTextField(
                value = content.text,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (content.isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                hint = "To-do",
                focusRequester = focusRequester,
                onTextChange = onTextChange,
                onFocusChange = onFocusChange,
                onSelectionChange = onSelectionChange,
                onBackspaceAtStart = onBackspaceAtStart,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        is BlockContent.Bullet -> Row(modifier = modifier) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 9.dp)
                    .size(7.dp),
            ) {}
            BlockTextField(
                value = content.text,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                hint = "List item",
                focusRequester = focusRequester,
                onTextChange = onTextChange,
                onFocusChange = onFocusChange,
                onSelectionChange = onSelectionChange,
                onBackspaceAtStart = onBackspaceAtStart,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is BlockContent.Numbered -> Row(modifier = modifier) {
            Text(
                text = "${content.index}.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            BlockTextField(
                value = content.text,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                hint = "List item",
                focusRequester = focusRequester,
                onTextChange = onTextChange,
                onFocusChange = onFocusChange,
                onSelectionChange = onSelectionChange,
                onBackspaceAtStart = onBackspaceAtStart,
            )
        }
        is BlockContent.Quote -> Row(modifier = modifier) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp),
            ) {}
            BlockTextField(
                value = content.text,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                hint = "Quote",
                focusRequester = focusRequester,
                onTextChange = onTextChange,
                onFocusChange = onFocusChange,
                onSelectionChange = onSelectionChange,
                onBackspaceAtStart = onBackspaceAtStart,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        BlockContent.Divider -> Row(
            modifier = modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp),
            ) {}
            Text(
                text = "tap to remove",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
            )
        }
    }
}

@Composable
private fun BlockTextField(
    value: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    marks: List<InlineMark> = emptyList(),
    hint: String = "",
    focusRequester: FocusRequester? = null,
    onTextChange: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> },
    onBackspaceAtStart: () -> Unit = {},
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        val hintVisible = value.isEmpty() && hint.isNotEmpty()
        val hintAlpha by animateFloatAsState(
            targetValue = if (hintVisible) 1f else 0f,
            animationSpec = tween(FusoMotion.DurationShort),
            label = "blockHintAlpha",
        )
        if (value.isEmpty() && !isFocused && hint.isNotEmpty()) {
            Text(
                text = hint,
                style = textStyle.copy(color = MaterialTheme.colorScheme.outline),
                modifier = Modifier.alpha(hintAlpha),
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onSelectionChange(newValue.selection.min, newValue.selection.max)
                onTextChange(newValue.text)
            },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = marksVisualTransformation(marks),
            modifier = Modifier
                .fillMaxWidth()
                .let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base }
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChange(focusState.isFocused)
                }
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace &&
                        fieldValue.selection.collapsed &&
                        fieldValue.selection.start == 0 &&
                        fieldValue.text.isEmpty()
                    ) {
                        onBackspaceAtStart()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

private fun marksVisualTransformation(marks: List<InlineMark>): VisualTransformation =
    VisualTransformation { text ->
        val builder = AnnotatedString.Builder(text)
        marks.forEach { mark ->
            val start = mark.start.coerceIn(0, text.length)
            val end = mark.end.coerceIn(0, text.length)
            if (end > start) {
                builder.addStyle(spanStyleFor(mark.style), start, end)
            }
        }
        TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

private fun spanStyleFor(style: MarkStyle): SpanStyle = when (style) {
    MarkStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    MarkStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    MarkStyle.HIGHLIGHT -> SpanStyle(background = Color(0x55F5BE48))
}

@Composable
fun FusoTodoCheckbox(
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val checkProgress by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = FusoMotion.springSnappy(),
        label = "todoCheck",
    )
    val boxColor by animateColorAsState(
        targetValue = if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = FusoMotion.springSnappy(),
        label = "todoBoxColor",
    )

    Box(
        modifier = modifier
            .size(22.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = boxColor,
            border = BorderStroke(
                width = 1.5.dp,
                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.size(22.dp),
        ) {}
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    scaleX = checkProgress
                    scaleY = checkProgress
                },
        )
    }
}
