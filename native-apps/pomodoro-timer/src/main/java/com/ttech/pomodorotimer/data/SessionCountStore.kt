package com.ttech.pomodorotimer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("pomodoro")
private val COUNT_KEY = intPreferencesKey("completed_focus_sessions")

/** 完了した集中セッション数を端末内に保存する(Webアプリ版の localStorage 保存に相当) */
class SessionCountStore(private val context: Context) {
    val completedCount: Flow<Int> = context.dataStore.data.map { it[COUNT_KEY] ?: 0 }

    suspend fun save(count: Int) {
        context.dataStore.edit { it[COUNT_KEY] = count }
    }
}
