package com.fuso.core.intelligence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

const val RHYTHM_CHANNEL_ID = "fuso_rhythm"
private const val RHYTHM_NOTIFICATION_ID = 41
private const val WORK_NAME = "fuso_rhythm_periodic"

fun createRhythmChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        RHYTHM_CHANNEL_ID,
        "Writing rhythm",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Quiet nudges timed to your personal writing rhythm"
        setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
}

fun ensureRhythmWorkScheduled(context: Context) {
    val request = PeriodicWorkRequestBuilder<RhythmWorker>(4, TimeUnit.HOURS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RhythmEntryPoint {
    fun behaviorModel(): BehaviorModel
    fun usageTracker(): UsageTracker
}

class RhythmWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RhythmEntryPoint::class.java,
        )
        val model = entryPoint.behaviorModel()
        val now = LocalDateTime.now()

        model.refresh()
        if (!model.shouldRemindNow(now)) return Result.success()
        if (!notificationsEnabled(applicationContext)) return Result.success()

        val message = runCatching { model.nudgeMessage(now) }.getOrDefault("The page is warm and waiting.")
        showNotification(applicationContext, message)
        model.markReminded(now.toLocalDate())
        return Result.success()
    }

    private fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun showNotification(context: Context, message: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RHYTHM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Fuso")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(RHYTHM_NOTIFICATION_ID, notification)
        }
    }
}
