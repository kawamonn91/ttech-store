package com.kawamonn.store.data.api

import com.kawamonn.store.auth.TotpRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Web の管理API(/api/admin/ 以下)を、管理者のアクセストークン(Bearer)で呼ぶ。マイページの管理者向けの欄で使う。
 * 処理の中身(公開時にアプリ本体も合わせて公開する、など)は Web 側に集約されているので、ここでは再実装しない。
 * 管理者が2段階認証を設定済みで、トークンがまだ2段階認証済み(aal2)でなければ [TotpRequiredException] を投げる。
 *
 * [webBaseUrl] は /api/v1 を含まない、Web のベースURL。[accessToken] は未ログインなら null。
 */
class AdminApi(
    private val client: OkHttpClient,
    webBaseUrl: String,
    private val accessToken: suspend () -> String?,
) {
    private val base = webBaseUrl.trimEnd('/')
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** リリースの公開・却下・公開停止([action] は publish / reject / unpublish) */
    suspend fun releaseDecision(releaseId: String, action: String) {
        val body = buildJsonObject { put("action", action) }.toString().toRequestBody(jsonType)
        call(Request.Builder().url("$base/api/admin/releases/$releaseId/decision").post(body), failure = "操作に失敗しました")
    }

    /** 管理者専用アプリ(公開カタログに出ないもの)と、その最新の公開リリース */
    suspend fun privateApps(): List<PrivateAppDto> {
        val text = call(Request.Builder().url("$base/api/admin/private-apps").get(), failure = "管理者用アプリを読み込めませんでした")
        return try {
            json.decodeFromString<PrivateAppsDto>(text).items
        } catch (e: Exception) {
            throw ApiException("サーバーの応答を解釈できませんでした", cause = e)
        }
    }

    private suspend fun call(request: Request.Builder, failure: String): String {
        val token = accessToken() ?: throw ApiException("ログインしてください", 401)
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request.header("Authorization", "Bearer $token").build()).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) {
                        val error = json.parseObjectOrNull(text)
                        if (error.stringField("code") == "totp_required") throw TotpRequiredException()
                        throw ApiException(error.stringField("error") ?: "$failure (${response.code})", response.code)
                    }
                    text
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException("通信に失敗しました。ネットワーク接続を確認してください", cause = e)
            }
        }
    }
}
