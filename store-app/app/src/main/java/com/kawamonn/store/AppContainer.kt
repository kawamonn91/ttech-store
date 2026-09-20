package com.kawamonn.store

import android.app.Application
import android.content.Context
import com.kawamonn.store.data.InstalledApps
import com.kawamonn.store.data.api.HttpStoreApi
import com.kawamonn.store.data.api.StoreApi
import com.kawamonn.store.install.ApkDownloader
import com.kawamonn.store.install.ApkInstaller
import com.kawamonn.store.install.InstallController
import com.kawamonn.store.install.InstallEvent
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
