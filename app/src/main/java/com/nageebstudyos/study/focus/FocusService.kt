package com.nageebstudyos.study.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.nageebstudyos.study.StudyApplication
import com.nageebstudyos.study.domain.FocusConfig
import com.nageebstudyos.study.domain.FocusPhase
import com.nageebstudyos.study.domain.FocusResult
import com.nageebstudyos.study.domain.FocusStatus
import com.nageebstudyos.study.domain.FocusSnapshot
import com.nageebstudyos.study.domain.SessionResult
import com.nageebstudyos.study.domain.StudySession
import com.nageebstudyos.study.domain.StudyTime
import com.nageebstudyos.study.notifications.StudyNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Foreground owner of a [FocusEngine]. Keeps ticking with the screen off, shows the ongoing
 * notification, plays local alerts, and auto-saves the session when it ends. The UI never
 * owns timing — it only renders [FocusController] and sends user intents here.
 */
class FocusService : Service() {

    private lateinit var controller: FocusController
    private lateinit var notifier: StudyNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var engine: FocusEngine? = null
    private var config: FocusConfig? = null
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var startWallClock: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as StudyApplication).container
        controller = container.focusController
        notifier = container.notifier
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_PAUSE, ACTION_RESUME, ACTION_SKIP, ACTION_STOP -> {
                // A control intent can arrive after the process died: there is no engine and
                // no notification, so promote ourselves to foreground briefly (required after
                // startForegroundService) then stop instead of crashing.
                val current = engine
                if (current == null) {
                    runCatching {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notifier.idleNotification(),
                            if (android.os.Build.VERSION.SDK_INT >= 34)
                                ServiceCompat.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            else 0,
                        )
                    }
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                when (intent.action) {
                    ACTION_PAUSE -> current.pause()
                    ACTION_RESUME -> current.resume()
                    ACTION_SKIP -> current.skip()
                    ACTION_STOP -> {
                        finishNow(stoppedByUser = true)
                        return START_NOT_STICKY
                    }
                }
                publish()
            }
        }
        return START_STICKY
    }

    private fun start(intent: Intent) {
        val cfg = readConfig(intent) ?: run {
            stopSelf()
            return
        }
        this.config = cfg
        controller.start(cfg)
        startWallClock = System.currentTimeMillis()
        val e = FocusEngine(cfg) { SystemClock.elapsedRealtime() }
        engine = e
        e.start(startWallClock)
        acquireWakeLock()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notifier.focusNotification(e.snapshot()!!, cfg),
                if (android.os.Build.VERSION.SDK_INT >= 34)
                    ServiceCompat.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else 0,
            )
        }
        tickJob?.cancel()
        tickJob =
            scope.launch {
                while (isActive) {
                    // One-second cadence keeps the notification countdown fresh without
                    // hammering the main thread for hours while the wake lock is held.
                    delay(1000)
                    mutex.withLock {
                        val current = engine ?: return@withLock
                        val events = current.advance()
                        handleEvents(events)
                        publish()
                        if (current.status == FocusStatus.FINISHED) {
                            finishNow(stoppedByUser = false)
                            return@launch
                        }
                    }
                }
            }
    }

    private fun handleEvents(events: List<FocusEvent>) {
        val cfg = config ?: return
        // When the break starts paused (auto-start off) it carries its own event; avoid
        // chiming twice for the round that preceded it.
        events.firstOrNull { it is FocusEvent.BreakStarted || it is FocusEvent.AwaitingNext }
            ?.let { event ->
                val long =
                    when (event) {
                        is FocusEvent.BreakStarted -> event.long
                        is FocusEvent.AwaitingNext -> event.phase == FocusPhase.LONG_BREAK
                        else -> false
                    }
                notifier.notifyPhaseChange(long, cfg)
                return
            }
        events.firstOrNull { it is FocusEvent.FocusRoundFinished }?.let {
            notifier.notifyPhaseChange((it as FocusEvent.FocusRoundFinished).longBreakNext, cfg)
        }
    }

    private fun publish() {
        val e = engine ?: return
        val cfg = config ?: return
        val snap: FocusSnapshot = e.snapshot() ?: return
        controller.publish(snap)
        runCatching {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID, notifier.focusNotification(snap, cfg))
        }
        // Hold the wake lock only while a phase is actually counting down.
        if (snap.status == FocusStatus.RUNNING) {
            if (wakeLock?.isHeld != true) acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun finishNow(stoppedByUser: Boolean) {
        tickJob?.cancel()
        val e = engine ?: return
        val cfg = config ?: return
        val result: FocusResult = e.stop(System.currentTimeMillis()) ?: return
        var savedId: String? = null
        // Only auto-persist meaningful sessions (>=1 minute). Users can still save manually.
        if (result.focusedSeconds >= 60) {
            savedId =
                runCatching {
                    val container = (application as StudyApplication).container
                    withContext(Dispatchers.IO) {
                        val session =
                            StudySession(
                                id = com.nageebstudyos.study.domain.newId(),
                                subjectId = cfg.subjectId,
                                folderId = cfg.folderId,
                                lessonId = cfg.lessonId,
                                startTime = result.startWallClock,
                                endTime = result.endWallClock,
                                durationSeconds = result.focusedSeconds,
                                goal = cfg.goal,
                                result = null,
                                notes = "",
                                lastPage = null,
                                dayIndex = StudyTime.dayOf(result.startWallClock),
                                createdAt = System.currentTimeMillis(),
                            )
                        container.v2Actions.saveSession(session)
                    }
                }.getOrNull()
            notifier.notifyFocusFinished(result.focusedSeconds)
        }
        controller.finished(result, savedId)
        controller.publish(null)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        engine = null
        config = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NageebStudyOS:focus").apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L)
                }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun readConfig(intent: Intent): FocusConfig? =
        try {
            FocusConfig(
                presetId = intent.getStringExtra(EXTRA_PRESET_ID),
                focusMinutes = intent.getIntExtra(EXTRA_FOCUS, 50),
                shortBreakMinutes = intent.getIntExtra(EXTRA_SHORT, 10),
                longBreakMinutes = intent.getIntExtra(EXTRA_LONG, 25),
                sessionsCount = intent.getIntExtra(EXTRA_ROUNDS, 4),
                longBreakInterval = intent.getIntExtra(EXTRA_INTERVAL, 2),
                autoStart = intent.getBooleanExtra(EXTRA_AUTO, false),
                sound = intent.getBooleanExtra(EXTRA_SOUND, true),
                vibration = intent.getBooleanExtra(EXTRA_VIBRATION, true),
                subjectId = intent.getStringExtra(EXTRA_SUBJECT),
                folderId = intent.getStringExtra(EXTRA_FOLDER),
                lessonId = intent.getStringExtra(EXTRA_LESSON),
                goal = intent.getStringExtra(EXTRA_GOAL).orEmpty(),
            )
        } catch (_: Exception) {
            null
        }

    companion object {
        const val ACTION_START = "com.nageebstudyos.focus.START"
        const val ACTION_PAUSE = "com.nageebstudyos.focus.PAUSE"
        const val ACTION_RESUME = "com.nageebstudyos.focus.RESUME"
        const val ACTION_SKIP = "com.nageebstudyos.focus.SKIP"
        const val ACTION_STOP = "com.nageebstudyos.focus.STOP"

        private const val EXTRA_PRESET_ID = "presetId"
        private const val EXTRA_FOCUS = "focus"
        private const val EXTRA_SHORT = "short"
        private const val EXTRA_LONG = "long"
        private const val EXTRA_ROUNDS = "rounds"
        private const val EXTRA_INTERVAL = "interval"
        private const val EXTRA_AUTO = "auto"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_VIBRATION = "vibration"
        private const val EXTRA_SUBJECT = "subject"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_LESSON = "lesson"
        private const val EXTRA_GOAL = "goalText"
        private const val NOTIFICATION_ID = 30

        fun start(context: Context, config: FocusConfig) {
            val intent =
                Intent(context, FocusService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PRESET_ID, config.presetId)
                    putExtra(EXTRA_FOCUS, config.focusMinutes)
                    putExtra(EXTRA_SHORT, config.shortBreakMinutes)
                    putExtra(EXTRA_LONG, config.longBreakMinutes)
                    putExtra(EXTRA_ROUNDS, config.sessionsCount)
                    putExtra(EXTRA_INTERVAL, config.longBreakInterval)
                    putExtra(EXTRA_AUTO, config.autoStart)
                    putExtra(EXTRA_SOUND, config.sound)
                    putExtra(EXTRA_VIBRATION, config.vibration)
                    putExtra(EXTRA_SUBJECT, config.subjectId)
                    putExtra(EXTRA_FOLDER, config.folderId)
                    putExtra(EXTRA_LESSON, config.lessonId)
                    putExtra(EXTRA_GOAL, config.goal)
                }
            context.startForegroundService(intent)
        }

        fun action(context: Context, action: String) {
            context.startForegroundService(Intent(context, FocusService::class.java).apply {
                this.action = action
            })
        }
    }
}
