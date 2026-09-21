package com.kawamonn.store.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Supabase Auth(GoTrue)の REST エンドポイントへの薄いクライアント。SDKを増やさず、既存の OkHttp を使う */
class SupabaseAuthApi(
    private val client: OkHttpClient,
    private val supabaseUrl: String,
    private val anonKey: String,
) : AuthApi {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** [nonce] は Credential Manager に渡したのと同じ値。リプレイ攻撃を防ぐため Supabase 側でも検証させる */
    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String): AuthSessionData =
        post("auth/v1/token?grant_type=id_token", json.encodeToString(mapOf("provider" to "google", "id_token" to idToken, "nonce" to nonce)))

    override suspend fun signInWithPassword(email: String, password: String): AuthSessionData =
        post("auth/v1/token?grant_type=password", json.encodeToString(mapOf("email" to email, "password" to password)))

    override suspend fun refresh(refreshToken: String): AuthSessionData =
        post("auth/v1/token?grant_type=refresh_token", json.encodeToString(mapOf("refresh_token" to refreshToken)))

    override suspend fun signOut(accessToken: String) {
        try {
            postRaw("auth/v1/logout", "{}", accessToken)
        } catch (_: Exception) {
            // ローカルのセッションは呼び出し元でどのみち破棄するので、サーバー側の失効に失敗しても致命的ではない
        }
    }

    override suspend fun listFactors(accessToken: String): List<MfaFactor> {
        val text = getRaw("auth/v1/user", accessToken)
        return try {
            val obj = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
            val factors = obj?.get("factors") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            factors.map { json.decodeFromJsonElement(MfaFactor.serializer(), it) }
        } catch (e: Exception) {
            throw AuthApiException("サーバーの応答を解釈できませんでした")
        }
    }

    override suspend fun challengeTotp(accessToken: String, factorId: String): String {
        val text = postRaw("auth/v1/factors/$factorId/challenge", "{}", accessToken)
        return try {
            (json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)["id"]!!.let {
                (it as kotlinx.serialization.json.JsonPrimitive).content
            }
        } catch (e: Exception) {
            throw AuthApiException("サーバーの応答を解釈できませんでした")
        }
    }

    override suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthSessionData {
        val body = json.encodeToString(mapOf("challenge_id" to challengeId, "code" to code))
        val text = postRaw("auth/v1/factors/$factorId/verify", body, accessToken)
        return try {
            json.decodeFromString<AuthSessionData>(text)
        } catch (e: Exception) {
            throw AuthApiException("サーバーの応答を解釈できませんでした")
        }
    }

    private suspend fun post(path: String, body: String): AuthSessionData {
        val text = postRaw(path, body, null)
        return try {
            json.decodeFromString<AuthSessionData>(text)
        } catch (e: Exception) {
            throw AuthApiException("サーバーの応答を解釈できませんでした")
        }
    }

    private suspend fun postRaw(path: String, body: String, accessToken: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${supabaseUrl.trimEnd('/')}/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${accessToken ?: anonKey}")
            .post(body.toRequestBody(jsonType))
            .build()
        execute(request)
    }

    private suspend fun getRaw(path: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${supabaseUrl.trimEnd('/')}/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        execute(request)
    }

    private fun execute(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body.string()
                if (!response.isSuccessful) throw AuthApiException(errorMessage(bodyText) ?: "認証に失敗しました (${response.code})")
                return bodyText
            }
        } catch (e: AuthApiException) {
            throw e
        } catch (e: IOException) {
            throw AuthApiException("通信に失敗しました。ネットワーク接続を確認してください")
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return null
        (obj["msg"] ?: obj["error_description"] ?: obj["error"])?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull()
}
