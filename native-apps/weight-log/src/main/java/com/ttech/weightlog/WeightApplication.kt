package com.ttech.weightlog

import android.app.Application
import com.ttech.weightlog.data.WeightRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeightApplication : Application() {
    lateinit var repository: WeightRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        repository = WeightRepository(File(filesDir, "weight.json"))
        appScope.launch { repository.load() }
    }
}
