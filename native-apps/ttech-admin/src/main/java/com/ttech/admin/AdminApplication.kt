package com.ttech.admin

import android.app.Application
import android.content.Context
import com.ttech.admin.data.AdminApi
import com.ttech.admin.data.AuthRepository
import com.ttech.admin.data.KeystoreSessionStore
import com.ttech.admin.data.SupabaseAuthApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

class AdminApplication : Application() {
    lateinit var container: AdminContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AdminContainer(this)
    }
}

/** アプリ全体で共有する部品(通信・ログイン状態・管理API)をまとめて持つ */
class AdminContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val auth = AuthRepository(
        api = SupabaseAuthApi(http, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
        store = KeystoreSessionStore(context),
    )

    val api = AdminApi(http, BuildConfig.ADMIN_WEB_BASE, auth)

    /** ログイン画面に出す知らせ(例: 管理者権限が無いアカウントだった)。画面が読んだら消す */
    val notice = MutableStateFlow<String?>(null)
}
