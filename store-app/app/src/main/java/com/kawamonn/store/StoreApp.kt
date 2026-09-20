package com.kawamonn.store

import android.app.Application
import com.kawamonn.store.update.UpdateWorker

class StoreApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        UpdateWorker.schedule(this)
    }
}
