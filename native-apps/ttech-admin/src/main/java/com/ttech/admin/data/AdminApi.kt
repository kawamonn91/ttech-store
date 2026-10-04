package com.ttech.admin.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 管理APIが返したエラー。message は管理者にそのまま見せてよい文言 */
class AdminApiException(message: String, val status: Int, val code: String? = null) : Exception(message)

/** 2段階認証が済んでいないトークンで呼んだとき(通常は起きない。起きたらログインし直してもらう) */
class TotpRequiredException : Exception("2段階認証が必要です。ログインし直してください")

/**
 * 管理API(web の /api/admin 以下)のクライアント。
 * 認証は Authorization: Bearer <アクセストークン>。サーバーが 401 を返したら、トークンを更新して1回だけやり直す。
 */
class AdminApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val tokens: TokenSource,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun overview(): Overview = AdminJson.decodeFromString(get("api/admin/overview"))

    suspend fun users(query: String, bannedOnly: Boolean, offset: Int = 0, limit: Int = 30): UsersPage =
        AdminJson.decodeFromString(
            get("api/admin/users", "q" to query.ifBlank { null }, "banned" to if (bannedOnly) "1" else null, "offset" to offset.toString(), "limit" to limit.toString()),
        )

    suspend fun userDetail(id: String): UserDetail = AdminJson.decodeFromString(get("api/admin/users/$id"))

    suspend fun ban(userId: String, reason: String) {
        send("POST", "api/admin/users/$userId/ban", buildJsonObject { put("reason", reason) })
    }

    suspend fun unban(userId: String) {
        send("POST", "api/admin/users/$userId/unban", JsonObject(emptyMap()))
    }

    suspend fun reports(includeResolved: Boolean): List<Report> =
        AdminJson.decodeFromString<ReportsResponse>(get("api/admin/reports", "status" to if (includeResolved) "all" else null)).items

    suspend fun resolveReport(kind: String, id: String, action: String, reason: String? = null) {
        send("POST", "api/admin/reports/$kind/$id", buildJsonObject { put("action", action); if (!reason.isNullOrBlank()) put("reason", reason) })
    }

    suspend fun diaryEntries(query: String, userId: String? = null, offset: Int = 0): List<DiaryEntry> =
        AdminJson.decodeFromString<DiaryEntriesResponse>(
            get("api/admin/diary/entries", "q" to query.ifBlank { null }, "userId" to userId, "offset" to offset.toString()),
        ).items

    suspend fun deleteDiaryEntry(id: String) {
        send("DELETE", "api/admin/diary/entries/$id", null)
    }

    suspend fun setReviewStatus(id: String, status: String) {
        send("POST", "api/admin/reviews/$id", buildJsonObject { put("status", status) })
    }

    suspend fun developers(): List<Developer> = AdminJson.decodeFromString<DevelopersResponse>(get("api/admin/developers")).items

    suspend fun setDeveloperStatus(userId: String, status: String) {
        send("POST", "api/admin/developers/$userId", buildJsonObject { put("status", status) })
    }

    suspend fun apps(): List<AppRow> = AdminJson.decodeFromString<AppsResponse>(get("api/admin/apps")).items

    suspend fun setAppStatus(id: String, status: String) {
        send("POST", "api/admin/apps/$id/status", buildJsonObject { put("status", status) })
    }

    /** 公開・公開停止の承認待ちリリース(他の開発者が申請したアプリを含む) */
    suspend fun pendingReleases(): List<PendingRelease> = AdminJson.decodeFromString<PendingReleasesResponse>(get("api/admin/releases")).items

    /** [action] は "publish"(公開する)・"reject"(却下する)・"unpublish"(公開停止する) */
    suspend fun decideRelease(id: String, action: String) {
        send("POST", "api/admin/releases/$id/decision", buildJsonObject { put("action", action) })
    }

    suspend fun audit(): List<AuditRow> = AdminJson.decodeFromString<AuditResponse>(get("api/admin/audit")).items

    private suspend fun get(path: String, vararg query: Pair<String, String?>): String {
        val url = baseUrl.trimEnd('/').plus("/").plus(path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        return call("GET", url, null)
    }

    private suspend fun send(method: String, path: String, body: JsonElement?): String =
        call(method, baseUrl.trimEnd('/').plus("/").plus(path).toHttpUrl(), body)

    private suspend fun call(method: String, url: HttpUrl, body: JsonElement?): String {
        var token = tokens.validToken()
        var retried = false
        while (true) {
            val (status, text) = execute(method, url, body, token)
            if (status in 200..299) return text
            val (message, code) = errorOf(text)
            if (status == 401 && code == "totp_required") throw TotpRequiredException()
            if (status == 401 && !retried) {
                retried = true
                token = tokens.forceRefresh() ?: throw SessionExpiredException()
                continue
            }
            throw AdminApiException(message ?: "エラーが発生しました ($status)", status, code)
        }
    }

    private suspend fun execute(method: String, url: HttpUrl, body: JsonElement?, token: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val requestBody: RequestBody? = when {
                method == "GET" -> null
                body != null -> body.toString().toRequestBody(jsonType)
                else -> "".toRequestBody(jsonType)
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").method(method, requestBody).build()
            try {
                client.newCall(request).execute().use { it.code to it.body.string() }
            } catch (e: IOException) {
                throw AdminApiException("通信に失敗しました。ネットワーク接続を確認してください", 0)
            }
        }

    private fun errorOf(text: String): Pair<String?, String?> = runCatching {
        val obj = AdminJson.parseToJsonElement(text) as JsonObject
        (obj["error"] as? JsonPrimitive)?.contentOrNull to (obj["code"] as? JsonPrimitive)?.contentOrNull
    }.getOrDefault(null to null)
}
