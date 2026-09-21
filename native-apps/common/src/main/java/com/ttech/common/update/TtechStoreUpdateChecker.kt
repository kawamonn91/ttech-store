package com.ttech.common.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * T-tech Store に登録されている、このアプリ自身の最新バージョンを確認する。
 * どのアプリからも1ファイルコピーするだけで使えるよう、依存を Android SDK 標準の
 * org.json / HttpURLConnection だけに絞ってある(OkHttp 等の追加ライブラリ不要)。
 *
 * サーバー側には「このアプリが更新を確認した」という記録は残らない
 * (全アプリの一覧を取得して、自分の applicationId をこちら側で探すだけなので)。
 */
object TtechStoreUpdateChecker {
    private const val INDEX_URL = "https://store.kawamonn.com/api/v1/index"

    data class UpdateInfo(val slug: String, val latestVersionName: String, val latestVersionCode: Long)

    /** 更新があれば [UpdateInfo] を返す。無ければ(オフライン・エラー時も含めて)null */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val current = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0),
            )
            val items = fetchIndex()
            val mine = items.firstOrNull { it.packageName == context.packageName } ?: return@withContext null
            if (mine.versionCode > current) UpdateInfo(mine.slug, mine.versionName, mine.versionCode) else null
        } catch (_: Exception) {
            null
        }
    }

    /** T-tech Store アプリでこのアプリの詳細ページを開く(未インストールならブラウザにフォールバック) */
    fun openInStore(context: Context, slug: String) {
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ttechstore://apps/$slug"))
        val resolved = storeIntent.resolveActivity(context.packageManager) != null
        val intent = if (resolved) storeIntent else Intent(Intent.ACTION_VIEW, Uri.parse("https://store.kawamonn.com/apps/$slug"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private data class IndexEntry(val slug: String, val packageName: String, val versionName: String, val versionCode: Long)

    private fun fetchIndex(): List<IndexEntry> {
        val connection = URL(INDEX_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            if (connection.responseCode != 200) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val items: JSONArray = JSONObject(body).getJSONArray("items")
            return buildList {
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val latest = obj.getJSONObject("latest")
                    add(
                        IndexEntry(
                            slug = obj.getString("slug"),
                            packageName = obj.getString("packageName"),
                            versionName = latest.getString("versionName"),
                            versionCode = latest.getLong("versionCode"),
                        ),
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
