package com.kawamonn.store.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Supabase の PostgREST(テーブルAPI)への薄いクライアント。
 * Web版のブラウザから直接テーブルを読み書きするのと同じ方式(RLSで権限を制御する)。
 * マイページの「作成したアプリ」「承認待ちのリリース」など、Webの管理コンソールと
 * 同じデータ・同じ権限で表示・操作するために使う。
 */
class PostgrestApi(
    private val client: OkHttpClient,
    private val supabaseUrl: String,
    private val anonKey: String,
    /** null なら未ログイン扱い(anonキーのみで読み取れる公開データだけアクセス可能) */
    private val accessToken: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    class PostgrestException(message: String) : IOException(message)

    suspend fun select(table: String, query: String): JsonArray = withContext(Dispatchers.IO) {
        val url = "${supabaseUrl.trimEnd('/')}/rest/v1/$table?$query"
        execute(Request.Builder().url(url).get().build())
    }

    suspend fun update(table: String, query: String, patch: JsonObject) = withContext(Dispatchers.IO) {
        val url = "${supabaseUrl.trimEnd('/')}/rest/v1/$table?$query"
        val body = patch.toString().toRequestBody(jsonType)
        execute<JsonArray>(Request.Builder().url(url).patch(body).header("Prefer", "return=minimal").build())
    }

    private inline fun <reified T> execute(request: Request): T {
        val authed = request.newBuilder()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${accessToken() ?: anonKey}")
            .build()
        val text = try {
            client.newCall(authed).execute().use { response ->
                val bodyText = response.body.string()
                if (!response.isSuccessful) throw PostgrestException(errorMessage(bodyText) ?: "取得に失敗しました (${response.code})")
                bodyText
            }
        } catch (e: PostgrestException) {
            throw e
        } catch (e: IOException) {
            throw PostgrestException("通信に失敗しました。ネットワーク接続を確認してください")
        }
        if (text.isBlank()) return (JsonArray(emptyList())) as T
        return try {
            json.parseToJsonElement(text) as T
        } catch (e: Exception) {
            throw PostgrestException("サーバーの応答を解釈できませんでした")
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        (json.parseToJsonElement(body) as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.content }
    }.getOrNull()
}
