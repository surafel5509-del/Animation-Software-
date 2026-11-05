package com.animationstudio.app

import android.app.Application
import com.animationstudio.audio.AudioPlaybackController
import com.animationstudio.importer.AssetImporter
import com.animationstudio.projectmanager.ProjectDatabase
import com.animationstudio.projectmanager.ProjectRepository
import com.animationstudio.settings.StudioSettings

class AppContainer(application: Application) {
    private val database = ProjectDatabase.create(application)
    val repository = ProjectRepository(application, database)
    val settings = StudioSettings(application)
    val importer = AssetImporter(application, repository.assets)
    val audio = AudioPlaybackController()
}

class AnimationStudioApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
