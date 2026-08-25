package com.fuso.app.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fuso.core.data.repository.EntryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val WIDGET_OPEN_EXTRA = "fuso.widget.open"
const val OPEN_NEW_NOTE = "new_note"
const val OPEN_NEW_JOURNAL = "new_journal"
const val OPEN_APP = "app"

fun widgetIntent(target: String): Intent =
    Intent()
        .setClassName("com.fuso.app", "com.fuso.app.MainActivity")
        .putExtra(WIDGET_OPEN_EXTRA, target)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun entryRepository(): EntryRepository
}

internal fun repository(context: Context): EntryRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).entryRepository()

object WidgetDeepLink {
    var pendingTarget: String? = null
}

private val WarmSurface = Color(0xFFFBF7F0)
private val InkSurface = Color(0xFF1E1B16)
private val Clay = Color(0xFFB4572F)
private val InkText = Color(0xFF2A2620)
private val MutedText = Color(0xFF7A7163)

private fun textColor(dark: Boolean, muted: Boolean = false): Color = when {
    dark && muted -> Color(0xFF9C948A)
    dark -> Color(0xFFE8E1D5)
    muted -> MutedText
    else -> InkText
}

private fun launch(target: String): Action = actionStartActivity(widgetIntent(target))

class QuickCaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(WarmSurface).cornerRadius(20.dp).padding(10.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.glance.Button(
                        text = "New note",
                        onClick = launch(OPEN_NEW_NOTE),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.glance.Button(
                        text = "New journal",
                        onClick = launch(OPEN_NEW_JOURNAL),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()
}

class TodayStatsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = repository(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayCount = runBlocking {
            runCatching {
                repo.observeEntriesBetween(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
                    .first().size
            }.getOrDefault(0)
        }
        val totalCount = runBlocking {
            runCatching { repo.observeEntries().first().size }.getOrDefault(0)
        }
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(InkSurface).cornerRadius(22.dp).padding(16.dp),
            ) {
                Text(
                    text = LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                        .uppercase(Locale.getDefault()),
                    style = TextStyle(color = ColorProvider(textColor(true, muted = true)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = if (todayCount == 1) "1 entry today" else "$todayCount entries today",
                    style = TextStyle(color = ColorProvider(textColor(true)), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatChip("$totalCount total")
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    StatChip("Keep going")
                }
            }
        }
    }

    @Composable
    private fun StatChip(label: String) {
        Box(
            modifier = GlanceModifier.background(Color(0x33B4572F)).cornerRadius(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(Clay), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

class TodayStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayStatsWidget()
}

class RecentNotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = repository(context)
        val notes = runBlocking {
            runCatching {
                repo.observeEntries().first()
                    .filter { it.type == com.fuso.core.model.EntryType.NOTE }
                    .take(5)
            }.getOrDefault(emptyList())
        }
        provideContent { Content(notes) }
    }

    @Composable
    private fun Content(notes: List<com.fuso.core.model.Entry>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(WarmSurface).cornerRadius(22.dp).padding(12.dp),
        ) {
            Text(
                text = "NOTES",
                style = TextStyle(color = ColorProvider(Clay), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (notes.isEmpty()) {
                androidx.glance.Button(
                    text = "Create your first note",
                    onClick = launch(OPEN_NEW_NOTE),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp),
                )
            } else {
                LazyColumn {
                    items(notes.size) { index ->
                        val note = notes[index]
                        androidx.glance.Button(
                            text = note.title.ifBlank { "Untitled" },
                            onClick = launch(OPEN_APP),
                            style = TextStyle(color = ColorProvider(InkText), fontSize = 13.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

class RecentNotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentNotesWidget()
}
