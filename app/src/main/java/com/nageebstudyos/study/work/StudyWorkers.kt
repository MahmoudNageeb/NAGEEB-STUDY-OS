package com.nageebstudyos.study.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nageebstudyos.study.StudyApplication
import com.nageebstudyos.study.domain.StudyTime
import com.nageebstudyos.study.domain.TaskStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Fires a local notification when a review becomes due. */
class ReviewDueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val container = (applicationContext as StudyApplication).container
        val review = container.v2Repository.review(id) ?: return Result.success()
        if (review.status == com.nageebstudyos.study.domain.ReviewStatus.PENDING) {
            container.notifier.notifyReviewDue(review.lessonTitle.ifBlank { applicationContext.getString(com.nageebstudyos.study.R.string.lesson) })
        }
        return Result.success()
    }
}

/** Fires a local notification on a task's due date. */
class TaskDueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val container = (applicationContext as StudyApplication).container
        val task = container.v2Repository.task(id) ?: return Result.success()
        if (task.status != TaskStatus.DONE) container.notifier.notifyTaskDue(task.title)
        return Result.success()
    }
}

/** Daily "time to study" reminder. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as StudyApplication).container.notifier.notifyReminder()
        return Result.success()
    }
}

/** Re-arms every pending alarm (WorkManager jobs are wiped on some reboots). */
class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as StudyApplication).container
        container.scheduler.rescheduleAll()
        return Result.success()
    }
}

/** Central scheduling API — all local, all unique-per-item, cancels when work is deleted. */
class StudyScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleReview(id: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(5_000L)
        val request =
            OneTimeWorkRequestBuilder<ReviewDueWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to id))
                .build()
        workManager.enqueueUniqueWork(reviewWork(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReview(id: String) {
        workManager.cancelUniqueWork(reviewWork(id))
    }

    fun scheduleTask(id: String, dueDay: Int?) {
        cancelTask(id)
        if (dueDay == null) return
        val at = StudyTime.at(dueDay, 9, 0)
        val delay = (at - System.currentTimeMillis())
        if (delay <= 0) return // already due; no future alarm
        val request =
            OneTimeWorkRequestBuilder<TaskDueWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to id))
                .build()
        workManager.enqueueUniqueWork(taskWork(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelTask(id: String) {
        workManager.cancelUniqueWork(taskWork(id))
    }

    fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(DAILY_REMINDER)
            return
        }
        val now = java.time.ZonedDateTime.now(StudyTime.zone())
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(StudyTime.zone())
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request =
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(next.toInstant().toEpochMilli() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniquePeriodicWork(
            DAILY_REMINDER,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun rescheduleAll() {
        val container = (context as StudyApplication).container
        val repo = container.v2Repository
        repo.pendingReviewReminders().forEach { (id, at) -> scheduleReview(id, at) }
        repo.pendingTaskReminders().forEach { (id, at) ->
            val day = StudyTime.dayOf(at)
            scheduleTask(id, day)
        }
        val settings = repo.settings().first()
        if (settings["reminderEnabled"] == "1") {
            setDailyReminder(
                true,
                settings["reminderHour"]?.toIntOrNull() ?: 20,
                settings["reminderMinute"]?.toIntOrNull() ?: 0,
            )
        }
    }

    private fun reviewWork(id: String) = "review-$id"

    private fun taskWork(id: String) = "task-$id"

    companion object {
        const val DAILY_REMINDER = "daily-study-reminder"
        const val KEY_ID = "id"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val request = OneTimeWorkRequestBuilder<RescheduleWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("reschedule-all", ExistingWorkPolicy.KEEP, request)
        }
    }
}
