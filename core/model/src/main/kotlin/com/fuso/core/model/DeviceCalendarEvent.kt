package com.fuso.core.model

data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarDisplayName: String,
)
