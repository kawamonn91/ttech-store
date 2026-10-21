package com.kawamonn.store

import android.app.Application
import android.content.Context
import com.kawamonn.store.auth.AuthRepository
import com.kawamonn.store.auth.AuthState
import com.kawamonn.store.auth.SharedPreferencesAuthSession
import com.kawamonn.store.auth.SupabaseAuthApi
import com.kawamonn.store.data.InstalledApps
import com.kawamonn.store.data.api.AdminApi
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
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/** アプリ全体で共有するオブジェクトの置き場(手動DI) */
class AppContainer(private val app: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: StoreApi = HttpStoreApi(http, BuildConfig.STORE_API_BASE) { authRepository.ensureFreshToken() }
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
        (authRepository.state.value as? AuthState.SignedIn)?.accessToken
    }

    /** マイページの管理者向けの欄(リリースの承認・却下、管理者専用アプリ)。Web の管理APIを管理者のトークンで呼ぶ */
    val adminApi = AdminApi(http, BuildConfig.STORE_WEB_BASE) { authRepository.ensureFreshToken() }

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
