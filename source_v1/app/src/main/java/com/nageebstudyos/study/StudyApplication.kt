package com.nageebstudyos.study

import android.app.Application
import androidx.room.Room
import com.nageebstudyos.study.data.local.StudyDatabase
import com.nageebstudyos.study.data.repository.RoomStudyRepository
import com.nageebstudyos.study.data.storage.DocumentStorage
import com.nageebstudyos.study.domain.StudyActions

class StudyApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    private val database =
        Room.databaseBuilder(app, StudyDatabase::class.java, "nageeb-study.db")
            .addCallback(StudyDatabase.constraints)
            .build()
    val repository = RoomStudyRepository(database)
    val actions = StudyActions(repository)
    val storage = DocumentStorage(app)
}
