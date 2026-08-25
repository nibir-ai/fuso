package com.fuso.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsPressedAsState as collectPressed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuso.core.data.nlp.ParsedQuickAdd
import com.fuso.core.data.nlp.QuickAddParser
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.ui.EntryCard
import com.fuso.core.ui.FadeSlideIn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val TodayRoute = "today"

@Composable
fun TodayScreen(
    onEntryClick: (String) -> Unit,
    onQuickCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val insight by viewModel.insight.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    var showMoodDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingEventText by remember { mutableStateOf<String?>(null) }
    fun hasWriteAccess(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_CALENDAR,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val calendarPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allowed = grants[android.Manifest.permission.WRITE_CALENDAR] == true &&
            grants[android.Manifest.permission.READ_CALENDAR] == true
        pendingEventText?.let { text ->
            viewModel.quickAdd(text, createCalendarEvent = allowed)
        }
        pendingEventText = null
    }

    fun handleQuickSubmit(raw: String) {
        val parsed = QuickAddParser.parse(raw)
        val wantsEvent = parsed.isEvent && parsed.targetDate != null && parsed.time != null
        if (wantsEvent && !hasWriteAccess()) {
            pendingEventText = raw
            calendarPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_CALENDAR,
                    android.Manifest.permission.WRITE_CALENDAR,
                ),
            )
        } else {
            viewModel.quickAdd(raw, createCalendarEvent = wantsEvent)
        }
    }

    if (showMoodDialog) {
        MoodCheckInDialog(
            onDismiss = { showMoodDialog = false },
            onPick = { value ->
                viewModel.checkIn(value)
                showMoodDialog = false
            },
        )
    }

    TodayContent(
        uiState = uiState,
        weeklyInsight = insight,
        justSaved = justSaved,
        mood = mood,
        onMoodClick = { showMoodDialog = true },
        onSubmitQuickAdd = { raw, _ -> handleQuickSubmit(raw) },
        onEntryClick = onEntryClick,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
private fun MoodCheckInDialog(
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "How's today feeling?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Only you will ever see this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple(Icons.Rounded.SentimentVeryDissatisfied, "Rough", Color(0xFFC96A5A)),
                        Triple(Icons.Rounded.SentimentDissatisfied, "Low", Color(0xFFD08B4F)),
                        Triple(Icons.Rounded.SentimentNeutral, "Okay", Color(0xFF9A917F)),
                        Triple(Icons.Rounded.SentimentSatisfied, "Good", Color(0xFF7E9455)),
                        Triple(Icons.Rounded.SentimentVerySatisfied, "Bright", Color(0xFF5E8C61)),
                    ).forEach { (icon, label, tint) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPick(moodIndexFor(label))
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun moodIndexFor(label: String): Int = when (label) {
    "Rough" -> 1
    "Low" -> 2
    "Okay" -> 3
    "Good" -> 4
    else -> 5
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    weeklyInsight: String?,
    justSaved: Boolean,
    mood: TodayViewModel.MoodUiState,
    onMoodClick: () -> Unit,
    onSubmitQuickAdd: (String, Boolean) -> Unit,
    onEntryClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { LocalDateTime.now() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            FadeSlideIn(index = 0) { HeaderSection(now, weeklyInsight, onOpenSettings = onOpenSettings) }
        }
        item(key = "capture") {
            FadeSlideIn(index = 1) {
                QuickAddCard(
                    justSaved = justSaved,
                    onSubmit = onSubmitQuickAdd,
                )
            }
        }
        item(key = "status") {
            FadeSlideIn(index = 2) { StatusRow(streak = uiState.streak, mood = mood, onMoodClick = onMoodClick) }
        }
        if (uiState.recentEntries.isNotEmpty()) {
            item(key = "recent-title") {
                FadeSlideIn(index = 3) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item(key = "recent-rail") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        items = uiState.recentEntries,
                        key = { _, entry -> entry.id },
                    ) { index, entry ->
                        FadeSlideIn(index = index + 4) {
                            EntryCard(
                                entry = entry,
                                onClick = { onEntryClick(entry.id) },
                                modifier = Modifier.width(292.dp),
                                heroKey = "entry-${entry.id}",
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "empty-invite") {
                FadeSlideIn(index = 3) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "A blank page is a good sign.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Try typing \"call mum tomorrow at 6pm\" above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(now: LocalDateTime, weeklyInsight: String?, onOpenSettings: () -> Unit) {
    val dateLabel = now.format(DateTimeFormatter.ofPattern("EEEE · MMMM d", Locale.getDefault()))
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = dateLabel.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = greetingFor(now),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(3.dp))
            AnimatedContent(
                targetState = weeklyInsight ?: "What's on your mind?",
                transitionSpec = {
                    (fadeIn(tween(FusoMotion.DurationMedium)) togetherWith fadeOut(tween(FusoMotion.DurationShort)))
                },
                label = "insight",
            ) { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun greetingFor(now: LocalDateTime): String = when (now.hour) {
    in 5..11 -> "Good morning."
    in 12..17 -> "Good afternoon."
    in 18..22 -> "Good evening."
    else -> "Up late?"
}

@Composable
private fun StatusRow(
    streak: Int,
    mood: TodayViewModel.MoodUiState,
    onMoodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakPill(streakDays = streak)
            MoodPill(moodValue = mood.todayMood, onClick = onMoodClick)
        }
        if (mood.weekMoods.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            WeekTrail(weekMoods = mood.weekMoods)
        }
    }
}

@Composable
private fun MoodPill(moodValue: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val (label, icon) = when (moodValue) {
        1 -> "Rough" to Icons.Rounded.SentimentVeryDissatisfied
        2 -> "Low" to Icons.Rounded.SentimentDissatisfied
        3 -> "Okay" to Icons.Rounded.SentimentNeutral
        4 -> "Good" to Icons.Rounded.SentimentSatisfied
        5 -> "Bright" to Icons.Rounded.SentimentVerySatisfied
        else -> "Mood check-in" to Icons.Rounded.Mood
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = label,
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekTrail(weekMoods: Map<java.time.LocalDate, Int>, modifier: Modifier = Modifier) {
    val today = java.time.LocalDate.now()
    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Your week",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until 7).forEach { back ->
                val date = today.minusDays((6 - back).toLong())
                val value = weekMoods[date]
                val tint = when (value) {
                    1 -> Color(0xFFC96A5A)
                    2 -> Color(0xFFD08B4F)
                    3 -> Color(0xFF9A917F)
                    4 -> Color(0xFF7E9455)
                    5 -> Color(0xFF5E8C61)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(if (date == today) 16.dp else 12.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = if (value == null) 0.35f else 1f)),
                    )
                    Text(
                        text = labels[date.dayOfWeek.value % 7],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakPill(streakDays: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (streakDays > 0) "$streakDays-day streak" else "Start a streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun GhostPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Mood,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAddCard(
    justSaved: Boolean,
    onSubmit: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.autoSaver()) {
        mutableStateOf(TextFieldValue(""))
    }
    val parsed = remember(fieldValue.text) { QuickAddParser.parse(fieldValue.text) }
    val interpretation = remember(parsed) { QuickAddParser.describe(parsed) }
    val haptics = LocalHapticFeedback.current
    val spanHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val spanTransformation = androidx.compose.runtime.remember(parsed.spans, spanHighlight) {
        androidx.compose.ui.text.input.VisualTransformation { textValue ->
            val builder = androidx.compose.ui.text.AnnotatedString.Builder(textValue)
            parsed.spans.forEach { span ->
                val start = span.startInOriginal.coerceIn(0, textValue.length)
                val end = span.endInOriginal.coerceIn(0, textValue.length)
                if (end > start) {
                    builder.addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            background = spanHighlight,
                            fontWeight = FontWeight.Bold,
                        ),
                        start,
                        end,
                    )
                }
            }
            androidx.compose.ui.text.input.TransformedText(
                builder.toAnnotatedString(),
                androidx.compose.ui.text.input.OffsetMapping.Identity,
            )
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .glowBorder(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = justSaved,
                            label = "captureIcon",
                        ) { saved ->
                            Icon(
                                imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = spanTransformation,
                        decorationBox = { inner ->
                            Box {
                                if (fieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Write, or try \"lunch tomorrow at 1pm\"",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                                        ),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                val canSubmit = fieldValue.text.isNotBlank() && !justSaved
                val submitInteraction = remember { MutableInteractionSource() }
                val submitPressed by submitInteraction.collectIsPressedAsState()
                val submitScale by animateFloatAsState(
                    targetValue = if (submitPressed) 0.88f else 1f,
                    animationSpec = FusoMotion.springSnappy(),
                    label = "submitScale",
                )
                Surface(
                    shape = CircleShape,
                    color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(34.dp)
                        .alpha(if (canSubmit) 1f else 0.5f)
                        .graphicsLayer {
                            scaleX = submitScale
                            scaleY = submitScale
                        }
                        .clickable(
                            interactionSource = submitInteraction,
                            indication = null,
                            enabled = canSubmit,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val p = QuickAddParser.parse(fieldValue.text)
                            val wantsEvent = p.isEvent && p.targetDate != null && p.time != null
                            onSubmit(fieldValue.text, wantsEvent)
                            fieldValue = TextFieldValue("")
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = interpretation,
                transitionSpec = { (fadeIn(tween(FusoMotion.DurationShort)) togetherWith fadeOut(tween(FusoMotion.DurationShort))) },
                label = "interpretation",
                modifier = Modifier.padding(start = 44.dp, top = 4.dp),
            ) { text ->
                if (text != null) {
                    Text(
                        text = "→ $text",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Box(Modifier.height(0.dp))
                }
            }
        }
    }
}

@Composable
private fun Modifier.glowBorder(): Modifier {
    val transition = rememberInfiniteTransition(label = "glow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "glowAngle",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toPx() }
    return this.then(
        Modifier.drawBehindGlow(angle, radiusPx, listOf(primary, tertiary, primary)),
    )
}

private fun Modifier.drawBehindGlow(angle: Float, cornerRadiusPx: Float, colors: List<Color>): Modifier =
    this.drawBehind {
        val center = Offset(size.width / 2f, size.height / 2f)
        rotate(degrees = angle, pivot = center) {
            drawRoundRect(
                brush = Brush.sweepGradient(colors = colors, center = center),
                cornerRadius = CornerRadius(cornerRadiusPx),
                style = Stroke(width = 6f),
            )
        }
    }
