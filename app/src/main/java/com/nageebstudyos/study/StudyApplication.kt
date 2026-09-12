package com.nageebstudyos.study

import android.app.Application
import androidx.room.Room
import com.nageebstudyos.study.data.local.StudyDatabase
import com.nageebstudyos.study.data.repository.RoomStudyRepository
import com.nageebstudyos.study.data.repository.RoomV2Repository
import com.nageebstudyos.study.data.storage.DocumentStorage
import com.nageebstudyos.study.domain.StudyActions
import com.nageebstudyos.study.domain.V2Actions
import com.nageebstudyos.study.focus.FocusController
import com.nageebstudyos.study.notifications.StudyNotifier
import com.nageebstudyos.study.work.StudyScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    // The V1 -> V2 migration is additive and validated; no destructive fallback is ever used.
    private val database =
        Room.databaseBuilder(app, StudyDatabase::class.java, "nageeb-study.db")
            .addCallback(StudyDatabase.constraints)
            .addMigrations(StudyDatabase.MIGRATION_1_2)
            .build()

    val repository = RoomStudyRepository(database)
    val v2Repository = RoomV2Repository(database)
    val actions = StudyActions(repository)
    val v2Actions = V2Actions(v2Repository)
    val storage = DocumentStorage(app)

    val focusController = FocusController()
    val notifier = StudyNotifier(app)
    val scheduler = StudyScheduler(app)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Seed built-in focus presets and re-arm local alarms after an app update.
        appScope.launch {
            runCatching {
                v2Repository.ensureBuiltInPresets()
                scheduler.rescheduleAll()
            }
        }
    }
}
