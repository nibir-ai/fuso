package com.fuso.feature.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuso.core.data.repository.DeviceCalendarRepository
import com.fuso.core.data.repository.EntryRepository
import com.fuso.core.data.repository.timeLabel
import com.fuso.core.designsystem.motion.FusoMotion
import com.fuso.core.model.DeviceCalendarEvent
import com.fuso.core.model.Entry
import com.fuso.core.ui.EntryCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val CalendarRoute = "calendar"

data class CalendarUiState(
    val isLoading: Boolean = true,
    val markedDates: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val selectedDayEntries: List<Entry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val deviceCalendarRepository: DeviceCalendarRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _monthEvents = MutableStateFlow<Map<LocalDate, List<DeviceCalendarEvent>>>(emptyMap())
    val monthEvents: StateFlow<Map<LocalDate, List<DeviceCalendarEvent>>> = _monthEvents.asStateFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        entryRepository.observeEntries().map { entries ->
            entries.map { it.createdAt.atZone(zone).toLocalDate() }.toSet()
        },
        selectedDate,
        selectedDate.flatMapLatest { date ->
            if (date == null) {
                flowOf(emptyList())
            } else {
                entryRepository.observeEntriesBetween(
                    start = date.atStartOfDay(zone).toInstant(),
                    end = date.plusDays(1).atStartOfDay(zone).toInstant(),
                )
            }
        },
    ) { markedDates, selected, dayEntries ->
        CalendarUiState(
            isLoading = false,
            markedDates = markedDates,
            selectedDate = selected,
            selectedDayEntries = dayEntries,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun onDateSelected(date: LocalDate) {
        selectedDate.value = if (selectedDate.value == date) null else date
    }

    fun loadMonthEvents(month: YearMonth) {
        viewModelScope.launch {
            runCatching {
                val events = deviceCalendarRepository.eventsBetween(
                    start = month.atDay(1),
                    endExclusive = month.plusMonths(1).atDay(1),
                )
                _monthEvents.value = events.groupBy { it.beginMillis.toLocalDate() }
            }
        }
    }

    private fun Long.toLocalDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}

@Composable
fun CalendarScreen(
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthEvents by viewModel.monthEvents.collectAsStateWithLifecycle()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val displayedMonth = YearMonth.parse(monthKey)

    val context = LocalContext.current
    var hasCalendarAccess by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCalendarAccess = granted }

    LaunchedEffect(displayedMonth, hasCalendarAccess) {
        if (hasCalendarAccess) viewModel.loadMonthEvents(displayedMonth)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        MonthPagerHeader(
            month = displayedMonth,
            onPrevious = { monthKey = displayedMonth.minusMonths(1).toString() },
            onNext = { monthKey = displayedMonth.plusMonths(1).toString() },
        )
        Spacer(modifier = Modifier.height(10.dp))
        WeekdayHeader(firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek)
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                val forward = targetState > initialState
                val movement = FusoMotion.DurationMedium
                if (forward) {
                    (
                        slideInHorizontally(
                            animationSpec = tween(movement, easing = FusoMotion.EmphasizedDecelerate),
                        ) { it / 3 } + fadeIn(tween(movement, easing = FusoMotion.EmphasizedDecelerate))
                        ) togetherWith (
                        slideOutHorizontally(animationSpec = tween(movement)) { -it / 3 } +
                            fadeOut(tween(movement))
                        )
                } else {
                    (
                        slideInHorizontally(
                            animationSpec = tween(movement, easing = FusoMotion.EmphasizedDecelerate),
                        ) { -it / 3 } + fadeIn(tween(movement, easing = FusoMotion.EmphasizedDecelerate))
                        ) togetherWith (
                        slideOutHorizontally(animationSpec = tween(movement)) { it / 3 } +
                            fadeOut(tween(movement))
                        )
                }
            },
            label = "monthGrid",
        ) { month ->
            MonthGrid(
                month = month,
                markedDates = uiState.markedDates,
                eventDates = if (hasCalendarAccess) monthEvents.keys else emptySet(),
                selectedDate = uiState.selectedDate,
                onDateClick = viewModel::onDateSelected,
            )
        }
        AnimatedVisibility(visible = !hasCalendarAccess) {
            CalendarInviteCard(onAllow = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) })
        }
        AnimatedVisibility(
            visible = uiState.selectedDate != null,
            enter = expandVertically(animationSpec = FusoMotion.springGentle()) + fadeIn(tween(FusoMotion.DurationShort)),
            exit = shrinkVertically(animationSpec = FusoMotion.springGentle()) + fadeOut(tween(FusoMotion.DurationShort)),
        ) {
            val selected = uiState.selectedDate ?: return@AnimatedVisibility
            DayDetailPanel(
                date = selected,
                entries = uiState.selectedDayEntries,
                events = monthEvents[selected].orEmpty(),
                onEntryClick = onEntryClick,
            )
        }
    }
}

@Composable
private fun CalendarInviteCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "See your Google calendar alongside your writing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllow) { Text("Allow") }
        }
    }
}

@Composable
private fun MonthPagerHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                if (forward) {
                    slideInHorizontally(tween(FusoMotion.DurationMedium)) { it / 4 } + fadeIn(tween(FusoMotion.DurationShort)) togetherWith
                        slideOutHorizontally(tween(FusoMotion.DurationMedium)) { -it / 4 } + fadeOut(tween(FusoMotion.DurationShort))
                } else {
                    slideInHorizontally(tween(FusoMotion.DurationMedium)) { -it / 4 } + fadeIn(tween(FusoMotion.DurationShort)) togetherWith
                        slideOutHorizontally(tween(FusoMotion.DurationMedium)) { it / 4 } + fadeOut(tween(FusoMotion.DurationShort))
                }
            },
            label = "monthTitle",
        ) { target ->
            Text(
                text = target.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    Row(modifier = modifier.fillMaxWidth()) {
        (0 until 7).forEach { offset ->
            val day = DayOfWeek.of(((firstDayOfWeek.value + offset - 1) % 7) + 1)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    eventDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstOfMonth = month.atDay(1)
    val leadingEmpty = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val days = List(leadingEmpty) { null as LocalDate? } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }
    val rows = days.chunked(7)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                hasEntries = markedDates.contains(date),
                                hasEvents = eventDates.contains(date),
                                isSelected = selectedDate == date,
                                onClick = { onDateClick(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    hasEntries: Boolean,
    hasEvents: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .then(
                    when {
                        isToday -> Modifier.background(MaterialTheme.colorScheme.primary)
                        isSelected -> Modifier.border(
                            border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                            shape = CircleShape,
                        )
                        else -> Modifier
                    },
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                DotIndicator(
                    visible = hasEntries || isToday,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                )
                DotIndicator(
                    visible = hasEvents && !isToday,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DotIndicator(visible: Boolean, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(if (visible) 5.dp else 0.dp)
            .alpha(if (visible) 1f else 0f)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun DayDetailPanel(
    date: LocalDate,
    entries: List<Entry>,
    events: List<DeviceCalendarEvent>,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + " · " +
                        date.format(DateTimeFormatter.ofPattern("MMMM d", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${entries.size + events.size} item${if (entries.size + events.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (events.isNotEmpty()) {
                    Text(
                        text = "FROM YOUR CALENDAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    events.take(4).forEach { event ->
                        EventRow(event = event)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (entries.isNotEmpty()) {
                    if (events.isNotEmpty()) {
                        Text(
                            text = "YOUR WRITING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entries.forEach { entry ->
                        EntryCard(
                            entry = entry,
                            showDate = false,
                            onClick = { onEntryClick(entry.id) },
                            heroKey = "entry-${entry.id}",
                        )
                    }
                }
                if (entries.isEmpty() && events.isEmpty()) {
                    Text(
                        text = "Nothing planned. A good day to write.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DeviceCalendarEvent, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(width = 4.dp, height = 30.dp),
            ) {}
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title.ifBlank { "(No title)" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = event.calendarDisplayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = event.timeLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
