package com.ttech.tripshiori

import android.app.Application
import com.ttech.tripshiori.data.TripRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShioriApplication : Application() {
    lateinit var repository: TripRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        repository = TripRepository(File(filesDir, "trips.json"))
        appScope.launch { repository.load() }
    }
}
