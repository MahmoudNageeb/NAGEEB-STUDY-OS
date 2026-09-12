package com.nageebstudyos.study.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nageebstudyos.study.MainActivity
import com.nageebstudyos.study.R
import com.nageebstudyos.study.domain.FocusConfig
import com.nageebstudyos.study.domain.FocusPhase
import com.nageebstudyos.study.domain.FocusSnapshot
import com.nageebstudyos.study.focus.FocusService

/** All local notifications. Nothing here leaves the device. */
class StudyNotifier(private val context: Context) {

    init {
        createChannels()
    }

    private fun manager(): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun createChannels() {
        val mgr = manager()
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_focus_desc)
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS_DONE, context.getString(R.string.channel_focus_done), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_focus_done_desc)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEWS, context.getString(R.string.channel_reviews), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reviews_desc)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_tasks_desc)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_reminder_desc)
            }
        )
    }

    private fun openApp(route: String?): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (route != null) putExtra(EXTRA_ROUTE, route)
            }
        return PendingIntent.getActivity(
            context,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, FocusService::class.java).apply { this.action = action }
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun focusNotification(snapshot: FocusSnapshot, config: FocusConfig): Notification {
        val paused = snapshot.status == com.nageebstudyos.study.domain.FocusStatus.PAUSED
        val builder =
            NotificationCompat.Builder(context, CHANNEL_FOCUS)
                .setSmallIcon(R.drawable.ic_app)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(ROUTE_FOCUS))
                .setContentTitle(
                    context.getString(
                        when (snapshot.phase) {
                            FocusPhase.FOCUS -> R.string.focus_notif_title
                            FocusPhase.LONG_BREAK -> R.string.long_break
                            FocusPhase.SHORT_BREAK -> R.string.short_break
                            else -> R.string.focus_notif_title
                        }
                    )
                )
                .setContentText(
                    context.getString(
                        R.string.focus_notif_text,
                        formatClock(snapshot.remainingMs),
                        snapshot.round,
                        snapshot.totalRounds,
                    )
                )
        if (paused) {
            builder.addAction(
                R.drawable.ic_app,
                context.getString(R.string.resume),
                serviceAction(FocusService.ACTION_RESUME, 21),
            )
        } else {
            builder.addAction(
                R.drawable.ic_app,
                context.getString(R.string.pause),
                serviceAction(FocusService.ACTION_PAUSE, 20),
            )
        }
        builder.addAction(
            R.drawable.ic_app,
            context.getString(R.string.end_session),
            serviceAction(FocusService.ACTION_STOP, 22),
        )
        return builder.build()
    }

    /** Minimal ongoing notification used when a stale control intent restarts the service. */
    fun idleNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_app)
            .setOngoing(true)
            .setSilent(true)
            .setContentTitle(context.getString(R.string.focus_notif_title))
            .setContentIntent(openApp(ROUTE_FOCUS))
            .build()

    fun notifyFocusFinished(focusedSeconds: Long) {
        post(
            NOTIF_FINISHED,
            NotificationCompat.Builder(context, CHANNEL_FOCUS_DONE)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(context.getString(R.string.focus_finished_title))
                .setContentText(context.getString(R.string.focus_finished_text, formatDuration(focusedSeconds)))
                .setAutoCancel(true)
                .setContentIntent(openApp(ROUTE_FOCUS)),
        )
        alert(config = null, longBreak = false, force = true)
    }

    fun notifyPhaseChange(longBreak: Boolean, config: FocusConfig?) {
        alert(config, longBreak = longBreak, force = false)
    }

    fun notifyReviewDue(lessonTitle: String) {
        post(
            NOTIF_REVIEW,
            NotificationCompat.Builder(context, CHANNEL_REVIEWS)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(context.getString(R.string.review_due_title))
                .setContentText(context.getString(R.string.review_due_text, lessonTitle))
                .setAutoCancel(true)
                .setContentIntent(openApp(ROUTE_HOME)),
        )
    }

    fun notifyTaskDue(title: String) {
        post(
            NOTIF_TASK,
            NotificationCompat.Builder(context, CHANNEL_TASKS)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(context.getString(R.string.task_due_title))
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openApp(ROUTE_PLANNER)),
        )
    }

    fun notifyReminder() {
        post(
            NOTIF_REMINDER,
            NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_body))
                .setAutoCancel(true)
                .setContentIntent(openApp(ROUTE_HOME)),
        )
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked after launch; the foreground timer itself is unaffected.
        }
    }

    /** Short, local, configurable feedback when a focus/break phase ends. */
    fun alert(config: FocusConfig?, longBreak: Boolean, force: Boolean) {
        val enabled = config?.sound ?: true
        val vibrate = config?.vibration ?: true
        if (enabled) {
            runCatching {
                val uri =
                    RingtoneManager.getActualDefaultRingtoneUri(
                        context,
                        RingtoneManager.TYPE_NOTIFICATION,
                    )
                if (uri != null) RingtoneManager.getRingtone(context, uri)?.play()
            }
        }
        if (vibrate || force) {
            val pattern =
                if (longBreak) longArrayOf(0, 220, 140, 220) else longArrayOf(0, 160, 110, 160)
            vibrate(pattern)
        }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr =
                    ContextCompat.getSystemService(context, VibratorManager::class.java) ?: return
                mgr.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator =
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
                }
            }
        }
    }

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "$h:$m" else m.toString()
    }

    companion object {
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_FOCUS_DONE = "focus_done"
        const val CHANNEL_REVIEWS = "reviews"
        const val CHANNEL_TASKS = "tasks"
        const val CHANNEL_REMINDER = "reminder"

        const val NOTIF_FINISHED = 40
        const val NOTIF_REVIEW = 41
        const val NOTIF_TASK = 42
        const val NOTIF_REMINDER = 43

        const val EXTRA_ROUTE = "route"
        const val ROUTE_HOME = "home"
        const val ROUTE_FOCUS = "focus"
        const val ROUTE_PLANNER = "planner"
    }
}
