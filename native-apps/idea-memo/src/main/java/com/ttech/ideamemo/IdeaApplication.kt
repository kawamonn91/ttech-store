package com.ttech.ideamemo

import android.app.Application
import com.ttech.ideamemo.data.IdeaRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IdeaApplication : Application() {
    lateinit var repository: IdeaRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        repository = IdeaRepository(File(filesDir, "ideas.json"))
        appScope.launch { repository.load() }
    }
}
