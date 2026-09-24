package com.ttech.admin.domain

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * アクセストークン(JWT)の中身を読むだけの道具。署名の検証はサーバー(Supabase)が行うので、
 * ここでは「有効期限」と「認証の強さ(aal1=パスワードのみ / aal2=2段階認証済み)」を知るためだけに読む。
 */
object Jwt {
    private val decoder = Base64.getUrlDecoder()

    fun payload(token: String): JsonObject? = runCatching {
        val part = token.split('.').getOrNull(1) ?: return null
        Json.parseToJsonElement(String(decoder.decode(part), Charsets.UTF_8)).jsonObject
    }.getOrNull()

    fun expiresAt(token: String): Long? = payload(token)?.get("exp")?.jsonPrimitive?.longOrNull

    fun aal(token: String): String? = payload(token)?.get("aal")?.jsonPrimitive?.contentOrNull
}
