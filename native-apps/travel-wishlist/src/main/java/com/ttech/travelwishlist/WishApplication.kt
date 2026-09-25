package com.ttech.travelwishlist

import android.app.Application
import com.ttech.travelwishlist.data.WishRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WishApplication : Application() {
    lateinit var repository: WishRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        repository = WishRepository(File(filesDir, "places.json"))
        appScope.launch { repository.load() }
    }
}
