package com.ttech.admin.data

import com.ttech.admin.domain.Jwt
import com.ttech.admin.domain.Session
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthApiException(message: String, val status: Int = 0) : Exception(message)

/** Supabase Auth が返す、ログイン成功時の応答 */
@Serializable
data class AuthTokens(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long = 3600,
    val expires_at: Long? = null,
    val user: AuthUser? = null,
)

@Serializable data class AuthUser(val id: String, val email: String? = null)

data class TotpFactor(val id: String, val verified: Boolean)

interface AuthApi {
    suspend fun signInWithPassword(email: String, password: String): AuthTokens
    suspend fun refresh(refreshToken: String): AuthTokens
    suspend fun listTotpFactors(accessToken: String): List<TotpFactor>
    suspend fun challengeTotp(accessToken: String, factorId: String): String
    suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthTokens
    suspend fun signOut(accessToken: String)
}

/** 応答を、保存・判定に使う Session にする。expires_at があればそれを、無ければ今+expires_in を期限とする */
fun AuthTokens.toSession(nowEpochSec: Long): Session {
    val id = user?.id ?: Jwt.payload(access_token)?.get("sub")?.let { (it as? JsonPrimitive)?.contentOrNull }
        ?: throw AuthApiException("サーバーの応答にユーザー情報がありません")
    return Session(
        accessToken = access_token,
        refreshToken = refresh_token,
        expiresAtEpochSec = expires_at ?: (nowEpochSec + expires_in),
        userId = id,
        email = user?.email,
    )
}

/** Supabase Auth(GoTrue)のREST。SDKは使わず OkHttp で直接呼ぶ(ストアアプリと同じ方針) */
class SupabaseAuthApi(
    private val client: OkHttpClient,
    private val supabaseUrl: String,
    private val anonKey: String,
) : AuthApi {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun signInWithPassword(email: String, password: String): AuthTokens =
        tokens(post("auth/v1/token?grant_type=password", buildJsonObject { put("email", email); put("password", password) }.toString(), null))

    override suspend fun refresh(refreshToken: String): AuthTokens =
        tokens(post("auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }.toString(), null))

    override suspend fun listTotpFactors(accessToken: String): List<TotpFactor> {
        val obj = parse(get("auth/v1/user", accessToken))
        val factors = obj["factors"] as? JsonArray ?: return emptyList()
        return factors.mapNotNull { element ->
            val f = element as? JsonObject ?: return@mapNotNull null
            if ((f["factor_type"] as? JsonPrimitive)?.contentOrNull != "totp") return@mapNotNull null
            val id = (f["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            TotpFactor(id, (f["status"] as? JsonPrimitive)?.contentOrNull == "verified")
        }
    }

    override suspend fun challengeTotp(accessToken: String, factorId: String): String {
        val obj = parse(post("auth/v1/factors/$factorId/challenge", "{}", accessToken))
        return (obj["id"] as? JsonPrimitive)?.contentOrNull ?: throw AuthApiException("サーバーの応答を解釈できませんでした")
    }

    override suspend fun verifyTotp(accessToken: String, factorId: String, challengeId: String, code: String): AuthTokens =
        tokens(
            post(
                "auth/v1/factors/$factorId/verify",
                buildJsonObject { put("challenge_id", challengeId); put("code", code) }.toString(),
                accessToken,
            ),
        )

    override suspend fun signOut(accessToken: String) {
        // サーバー側の失効に失敗しても、端末側のセッションはどのみち捨てるので致命的ではない
        runCatching { post("auth/v1/logout", "{}", accessToken) }
    }

    private fun tokens(text: String): AuthTokens = try {
        json.decodeFromString<AuthTokens>(text)
    } catch (e: Exception) {
        throw AuthApiException("サーバーの応答を解釈できませんでした")
    }

    private fun parse(text: String): JsonObject = try {
        json.parseToJsonElement(text) as JsonObject
    } catch (e: Exception) {
        throw AuthApiException("サーバーの応答を解釈できませんでした")
    }

    private suspend fun post(path: String, body: String, accessToken: String?): String = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/$path")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer ${accessToken ?: anonKey}")
                .post(body.toRequestBody(jsonType))
                .build(),
        )
    }

    private suspend fun get(path: String, accessToken: String): String = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/$path")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build(),
        )
    }

    private fun execute(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw AuthApiException(errorMessage(text) ?: "認証に失敗しました (${response.code})", response.code)
                return text
            }
        } catch (e: AuthApiException) {
            throw e
        } catch (e: IOException) {
            throw AuthApiException("通信に失敗しました。ネットワーク接続を確認してください")
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
        (obj["msg"] ?: obj["error_description"] ?: obj["error"])?.let { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrNull()
}
