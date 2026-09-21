package com.kawamonn.store.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleIdTokenResult(val idToken: String, val nonce: String)

/** Credential Manager 経由で Google の IDトークンを取得する */
class GoogleSignInHelper(private val context: Context, private val webClientId: String) {

    /**
     * IDトークンを取得する。ユーザーがキャンセルした場合などは null。
     * 返す nonce は、リプレイ攻撃を防ぐため呼び出し元が Supabase 側の検証にも渡すこと。
     */
    suspend fun getIdToken(): GoogleIdTokenResult? {
        if (webClientId.isBlank()) throw IllegalStateException("Googleログインが設定されていません")

        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (_: GetCredentialException) {
            return null // ユーザーがキャンセルした、選べるアカウントが無い等
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleIdTokenResult(googleCredential.idToken, nonce)
    }
}
