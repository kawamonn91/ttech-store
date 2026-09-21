package com.kawamonn.store

import android.app.Application
import android.content.Context
import com.kawamonn.store.auth.AuthRepository
import com.kawamonn.store.auth.SharedPreferencesAuthSession
import com.kawamonn.store.auth.SupabaseAuthApi
import com.kawamonn.store.data.InstalledApps
import com.kawamonn.store.data.api.HttpStoreApi
import com.kawamonn.store.data.api.PostgrestApi
import com.kawamonn.store.data.api.StoreApi
import com.kawamonn.store.install.ApkDownloader
import com.kawamonn.store.install.ApkInstaller
import com.kawamonn.store.install.InstallController
import com.kawamonn.store.install.InstallEvent
import com.kawamonn.store.update.UpdateChecker
import com.kawamonn.store.update.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/** アプリ全体で共有するオブジェクトの置き場(手動DI) */
class AppContainer(private val app: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: StoreApi = HttpStoreApi(http, BuildConfig.STORE_API_BASE)
    val installedApps = InstalledApps(app)
    val updatePrefs = UpdatePrefs(app)
    val installEvents = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 32)

    val installController = InstallController(
        context = app,
        api = api,
        downloader = ApkDownloader(http),
        installer = ApkInstaller(app),
        deviceId = ::deviceId,
        scope = appScope,
        events = installEvents,
    )

    /** ホーム画面のバナー・アップデートタブのバッジ用。ストアアプリを開くたびに [UpdateChecker.refresh] を呼ぶ */
    val updateChecker = UpdateChecker(api = api, installedVersion = installedApps::versionCode, scope = appScope)

    private val authApi = SupabaseAuthApi(http, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val authRepository = AuthRepository(authApi, SharedPreferencesAuthSession(app), appScope)

    /** マイページ用。Webの管理コンソールと同じテーブル・同じRLSでデータを読み書きする */
    val postgrest = PostgrestApi(http, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        (authRepository.state.value as? com.kawamonn.store.auth.AuthState.SignedIn)?.accessToken
    }

    /**
     * マイページからのリリース承認・却下。ロジックはWeb側の /api/admin/releases/:id/decision に
     * 集約されている(公開時にアプリ本体も合わせて公開する処理を含む)ため、Kotlin側では再実装せず
     * Bearerトークン付きでそのAPIを呼ぶ。
     */
    suspend fun adminReleaseDecision(releaseId: String, action: String) {
        val token = (authRepository.state.value as? com.kawamonn.store.auth.AuthState.SignedIn)?.accessToken
            ?: throw IllegalStateException("ログインしてください")
        withContext(Dispatchers.IO) {
            val body = """{"action":"$action"}""".toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("${BuildConfig.STORE_WEB_BASE}/api/admin/releases/$releaseId/decision")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body.string()
                    val json = runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                    }.getOrNull()
                    fun field(name: String) = (json?.get(name) as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (field("code") == "totp_required") throw com.kawamonn.store.auth.TotpRequiredException()
                    throw IllegalStateException(field("error") ?: "操作に失敗しました (${response.code})")
                }
            }
        }
    }

    /** マイページの2段階認証コード入力から呼ぶ。成功すればaal2セッションが保存され、直後の管理操作が通るようになる */
    suspend fun verifyTotp(code: String): Result<Unit> = authRepository.verifyTotpCode(code)

    /**
     * ダウンロード数の重複カウント防止に使う、端末ごとのランダムID。
     * 個人を特定する情報ではなく、アンインストールすると作り直される。
     */
    private fun deviceId(): String {
        val prefs = app.getSharedPreferences("device", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
}
