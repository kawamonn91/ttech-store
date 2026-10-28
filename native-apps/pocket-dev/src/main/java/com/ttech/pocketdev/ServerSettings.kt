package com.ttech.pocketdev

import android.content.Context

/**
 * 接続先の保存。
 * トークンはアプリ専用領域(MODE_PRIVATE の SharedPreferences)に置く。
 * 複数の接続先プロファイルの管理に対応。
 */
class ServerSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pocket-dev", Context.MODE_PRIVATE)

    /** 現在アクティブな接続先を取得する（旧設定からの移行も行う） */
    fun load(): ConnectionTarget? {
        val activeJson = prefs.getString(KEY_ACTIVE_TARGET, null)
        if (!activeJson.isNullOrBlank()) {
            ConnectionTarget.fromJson(activeJson)?.let { return it }
        }

        // 旧形式（base_url / token）の互換読み込み
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        if (baseUrl.isBlank() || token.isBlank()) return null
        return ConnectionTarget(baseUrl, token)
    }

    /** 保存されている全接続先のリストを取得する */
    fun loadAll(): List<ConnectionTarget> {
        val raw = prefs.getString(KEY_TARGETS_LIST, null)
        if (!raw.isNullOrBlank()) {
            val list = ConnectionTarget.listFromJson(raw)
            if (list.isNotEmpty()) return list
        }
        // リストが無ければアクティブなターゲットを1件返す
        return listOfNotNull(load())
    }

    /** アクティブな接続先として保存し、リストにも追加/更新する */
    fun save(target: ConnectionTarget) {
        val list = loadAll().toMutableList()
        val index = list.indexOfFirst { it.id == target.id || (it.baseUrl == target.baseUrl && it.name == target.name) }
        if (index >= 0) {
            list[index] = target
        } else {
            list.add(0, target)
        }

        prefs.edit()
            .putString(KEY_ACTIVE_TARGET, target.toJson())
            .putString(KEY_TARGETS_LIST, ConnectionTarget.listToJson(list))
            // 旧コードとの互換性用
            .putString(KEY_BASE_URL, target.baseUrl)
            .putString(KEY_TOKEN, target.token)
            .apply()
    }

    /** 接続先をリストから削除する */
    fun remove(targetId: String) {
        val list = loadAll().filterNot { it.id == targetId }
        val editor = prefs.edit().putString(KEY_TARGETS_LIST, ConnectionTarget.listToJson(list))
        if (load()?.id == targetId) {
            val nextActive = list.firstOrNull()
            if (nextActive != null) {
                editor.putString(KEY_ACTIVE_TARGET, nextActive.toJson())
                    .putString(KEY_BASE_URL, nextActive.baseUrl)
                    .putString(KEY_TOKEN, nextActive.token)
            } else {
                editor.remove(KEY_ACTIVE_TARGET).remove(KEY_BASE_URL).remove(KEY_TOKEN)
            }
        }
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_ACTIVE_TARGET = "active_target_json"
        const val KEY_TARGETS_LIST = "targets_list_json"
    }
}
