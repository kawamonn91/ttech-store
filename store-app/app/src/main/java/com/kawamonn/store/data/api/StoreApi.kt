package com.kawamonn.store.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiException(message: String, val status: Int? = null, cause: Throwable? = null) : IOException(message, cause)

interface StoreApi {
    suspend fun home(): HomeDto
    suspend fun categories(): CategoriesDto
    suspend fun apps(query: String?, category: String?, sort: String, limit: Int, offset: Int): AppListDto
    suspend fun app(slug: String): AppDetailDto
    suspend fun index(): IndexDto
    suspend fun downloadInfo(releaseId: String, deviceId: String): DownloadInfoDto
}

/**
 * [authToken] はログイン中のアクセストークン(未ログインなら null)。ダウンロード情報の取得にだけ付ける。
 * 通常のアプリでは使われないが、管理者専用アプリ(公開カタログに出ないもの)は管理者のトークンが無いと発行されない。
 */
class HttpStoreApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val authToken: () -> String? = { null },
) : StoreApi {
    private val base: HttpUrl = baseUrl.trimEnd('/').plus("/").toHttpUrl()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun home() = get<HomeDto>("home")

    override suspend fun categories() = get<CategoriesDto>("categories")

    override suspend fun apps(query: String?, category: String?, sort: String, limit: Int, offset: Int) =
        get<AppListDto>(
            "apps",
            "q" to query?.takeIf { it.isNotBlank() },
            "category" to category,
            "sort" to sort,
            "limit" to limit.toString(),
            "offset" to offset.toString(),
        )

    override suspend fun app(slug: String) = get<AppDetailDto>("apps/$slug")

    override suspend fun index() = get<IndexDto>("index")

    override suspend fun downloadInfo(releaseId: String, deviceId: String): DownloadInfoDto =
        post("releases/$releaseId/download", json.encodeToString(DownloadRequestDto(deviceId)), authToken())

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String?>): T {
        val url = base.newBuilder().addPathSegments(path).apply {
            for ((k, v) in query) if (v != null) addQueryParameter(k, v)
        }.build()
        return execute(Request.Builder().url(url).get().build())
    }

    private suspend inline fun <reified T> post(path: String, body: String, bearer: String? = null): T {
        val url = base.newBuilder().addPathSegments(path).build()
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonType))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return execute(builder.build())
    }

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        val text = try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body.string()
                if (!response.isSuccessful) {
                    throw ApiException(errorMessage(bodyText) ?: "サーバーエラー (${response.code})", response.code)
                }
                bodyText
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("通信に失敗しました。ネットワーク接続を確認してください", cause = e)
        }
        try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw ApiException("サーバーの応答を解釈できませんでした", cause = e)
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        (json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)
            ?.get("error")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull()
}
