package com.ttech.oneononelog.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ttech.oneononelog.domain.Member
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("one_on_one_log")
private val MEMBERS_KEY = stringPreferencesKey("members")
private val json = Json { ignoreUnknownKeys = true }

/** メンバーと1on1の記録を端末内に残す(Webアプリ版の localStorage 保存に相当)。 */
class MemberStore(private val context: Context) {
    val members: Flow<List<Member>> = context.dataStore.data.map { decode(it[MEMBERS_KEY]) }

    suspend fun add(item: Member) {
        update { listOf(item) + it }
    }

    suspend fun remove(id: String) {
        update { list -> list.filter { it.id != id } }
    }

    /** 現在の一覧を受け取って新しい一覧を返す [transform] で、読み込みと書き込みをまとめて行う。 */
    suspend fun update(transform: (List<Member>) -> List<Member>) {
        context.dataStore.edit { prefs ->
            prefs[MEMBERS_KEY] = json.encodeToString(transform(decode(prefs[MEMBERS_KEY])))
        }
    }

    private fun decode(raw: String?): List<Member> =
        raw?.let { runCatching { json.decodeFromString<List<Member>>(it) }.getOrNull() } ?: emptyList()
}
