package com.kawamonn.store.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/** [nonce] は、Supabase に渡す「元の値」。Google に渡したのは、そのSHA-256(→ [GoogleNonce]) */
data class GoogleIdTokenResult(val idToken: String, val nonce: String)

/**
 * リプレイ攻撃を防ぐための nonce。
 * Supabase は「IDトークンに入っている nonce == SHA-256(リクエストで受け取った nonce)」を検証する。
 * そのため Google には**ハッシュ化した値**を渡し、Supabase には**元の値**を渡す。
 * (同じ値を両方に渡すと、「Nonces mismatch」になる)
 */
object GoogleNonce {
    fun newRaw(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Credential Manager 経由で Google の IDトークンを取得する。アカウントが未登録の場合は、Supabase側で新規登録される(ひとこと日記と共通のアカウント) */
class GoogleSignInHelper(private val context: Context, private val webClientId: String) {

    /** 取得できなければ(利用者が閉じた場合)null。失敗は、原因が分かる文言の例外にする */
    suspend fun getIdToken(): GoogleIdTokenResult? {
        if (webClientId.isBlank()) throw IllegalStateException("Googleログインが設定されていません")

        val rawNonce = GoogleNonce.newRaw()
        val hashedNonce = GoogleNonce.sha256Hex(rawNonce)
        val manager = CredentialManager.create(context)

        val credential = try {
            manager.getCredential(context, requestOf(idOption(hashedNonce))).credential
        } catch (_: GetCredentialCancellationException) {
            return null // 利用者が自分で閉じた場合は、何も表示しない
        } catch (_: NoCredentialException) {
            // 端末に使えるGoogleアカウントが見当たらない(初めて使う・アカウント未追加)場合は、
            // アカウントの追加・新規登録までできる「Googleでログイン」の画面に切り替える
            try {
                manager.getCredential(context, requestOf(signInOption(hashedNonce))).credential
            } catch (_: GetCredentialCancellationException) {
                return null
            } catch (e: GetCredentialException) {
                throw failure(e)
            }
        } catch (e: GetCredentialException) {
            // 握りつぶすと「選んだのに何も起きない」ように見えるので、原因が分かる形で利用者に見せる
            throw failure(e)
        }

        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleIdTokenResult(googleCredential.idToken, rawNonce)
    }

    private fun idOption(hashedNonce: String) = GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false) // 過去にログインしていないアカウントも選べる(=新規登録できる)
        .setNonce(hashedNonce)
        .build()

    private fun signInOption(hashedNonce: String) = GetSignInWithGoogleOption.Builder(webClientId)
        .setNonce(hashedNonce)
        .build()

    private fun requestOf(option: CredentialOption) = GetCredentialRequest.Builder().addCredentialOption(option).build()

    private fun failure(e: GetCredentialException) =
        IllegalStateException("Googleログインに失敗しました(${e.type.substringAfterLast('.')}: ${e.message ?: "詳細なし"})", e)
}
