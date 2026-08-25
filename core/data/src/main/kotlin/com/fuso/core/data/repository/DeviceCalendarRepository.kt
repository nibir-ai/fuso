package com.fuso.core.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import com.fuso.core.model.DeviceCalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceCalendarRepository {

    suspend fun eventsBetween(start: LocalDate, endExclusive: LocalDate): List<DeviceCalendarEvent>
}

@Singleton
class DeviceCalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCalendarRepository {

    override suspend fun eventsBetween(start: LocalDate, endExclusive: LocalDate): List<DeviceCalendarEvent> {
        val zone = ZoneId.systemDefault()
        val beginMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val uri = Instances.CONTENT_URI.buildUpon()
            .appendPath(beginMillis.toString())
            .appendPath(endMillis.toString())
            .build()

        val projection = arrayOf(
            Instances._ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.CALENDAR_DISPLAY_NAME,
        )

        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${Instances.VISIBLE}=1",
                null,
                "${Instances.BEGIN} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Instances._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(Instances.TITLE)
                val beginIdx = cursor.getColumnIndexOrThrow(Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(Instances.ALL_DAY)
                val calNameIdx = cursor.getColumnIndexOrThrow(Instances.CALENDAR_DISPLAY_NAME)

                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DeviceCalendarEvent(
                                id = cursor.getLong(idIdx),
                                title = cursor.getString(titleIdx) ?: "",
                                beginMillis = cursor.getLong(beginIdx),
                                endMillis = cursor.getLong(endIdx),
                                allDay = cursor.getInt(allDayIdx) != 0,
                                calendarDisplayName = cursor.getString(calNameIdx) ?: "Calendar",
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}

fun DeviceCalendarEvent.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    java.time.Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate()

fun DeviceCalendarEvent.timeLabel(zone: ZoneId = ZoneId.systemDefault()): String {
    if (allDay) return "All day"
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val begin = java.time.Instant.ofEpochMilli(beginMillis).atZone(zone)
    val end = java.time.Instant.ofEpochMilli(endMillis).atZone(zone)
    return if (begin.toLocalDate() == end.toLocalDate()) {
        "${begin.format(formatter)} – ${end.format(formatter)}"
    } else {
        begin.format(formatter) + "+"
    }
}
