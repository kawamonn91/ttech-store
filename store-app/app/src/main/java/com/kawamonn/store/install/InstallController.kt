package com.kawamonn.store.install

import android.content.Context
import android.content.pm.PackageInstaller
import com.kawamonn.store.data.api.StoreApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface InstallState {
    data class Downloading(val downloaded: Long, val total: Long?) : InstallState
    data object Verifying : InstallState
    data object Installing : InstallState
    data object AwaitingUser : InstallState
    data object Success : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * 「ダウンロード → 検証 → インストール」の一連の流れを管理する。
 * 画面遷移で中断されないよう、アプリ全体のスコープで実行する。
 */
class InstallController(
    private val context: Context,
    private val api: StoreApi,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val deviceId: () -> String,
    private val scope: CoroutineScope,
    events: SharedFlow<InstallEvent>,
) {
    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())

    /** packageName → 現在の状態。何も進行していないアプリは含まれない */
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    /** インストール完了のたびに増える。画面がインストール済み状態を再取得するために使う */
    private val _installedTick = MutableStateFlow(0)
    val installedTick: StateFlow<Int> = _installedTick.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        scope.launch {
            events.collect { event ->
                when (event) {
                    is InstallEvent.AwaitingUser -> set(event.packageName, InstallState.AwaitingUser)
                    is InstallEvent.Finished -> onFinished(event)
                }
            }
        }
    }

    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun dismiss(packageName: String) = _states.update { it - packageName }

    fun cancel(packageName: String) {
        jobs.remove(packageName)?.cancel()
        dismiss(packageName)
    }

    /** 複数アプリを1つずつ順番に入れる(確認画面が同時に何枚も出ないように) */
    fun installSequentially(items: List<Pair<String, String>>) {
        scope.launch {
            for ((packageName, releaseId) in items) {
                install(packageName, releaseId)
                states.first { all ->
                    val s = all[packageName]
                    s == null || s is InstallState.Success || s is InstallState.Failed
                }
            }
        }
    }

    fun install(packageName: String, releaseId: String) {
        if (jobs[packageName]?.isActive == true) return
        // 呼び出し直後から「進行中」に見えるよう、状態は同期的にセットする
        set(packageName, InstallState.Downloading(0, null))
        jobs[packageName] = scope.launch {
            try {
                run(packageName, releaseId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                set(packageName, InstallState.Failed(e.message ?: "インストールに失敗しました"))
            }
        }
    }

    private suspend fun run(packageName: String, releaseId: String) {
        val info = api.downloadInfo(releaseId, deviceId())

        val dir = File(context.cacheDir, "apks")
        val file = File(dir, "$packageName-${info.versionCode}.apk")
        downloader.download(info.url, file) { done, total ->
            set(packageName, InstallState.Downloading(done, total))
        }

        set(packageName, InstallState.Verifying)
        val result = withContext(Dispatchers.IO) {
            val actual = ApkVerifier.inspect(context.packageManager, file)
            ApkVerifier.check(
                ExpectedApk(info.packageName, info.versionCode, info.sha256, info.signingCertSha256),
                actual,
            )
        }
        if (result is VerifyResult.Failed) {
            file.delete()
            set(packageName, InstallState.Failed(result.reason))
            return
        }

        set(packageName, InstallState.Installing)
        withContext(Dispatchers.IO) { installer.install(file, packageName) }
        // 結果は InstallResultReceiver → events 経由で届く
    }

    private fun onFinished(event: InstallEvent.Finished) {
        val pkg = event.packageName
        when (event.status) {
            PackageInstaller.STATUS_SUCCESS -> {
                File(context.cacheDir, "apks").listFiles { f -> f.name.startsWith("$pkg-") }?.forEach { it.delete() }
                set(pkg, InstallState.Success)
                _installedTick.update { it + 1 }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> dismiss(pkg) // ユーザーが確認画面でキャンセル
            else -> set(pkg, InstallState.Failed(failureMessage(event.status, event.message)))
        }
        jobs.remove(pkg)
    }

    private fun set(packageName: String, state: InstallState) = _states.update { it + (packageName to state) }

    private fun failureMessage(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "端末の設定によりインストールがブロックされました"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "インストール済みのアプリと署名が異なるため更新できません。先にアンインストールしてください"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "この端末には対応していないアプリです"
        PackageInstaller.STATUS_FAILURE_INVALID -> "APKが不正です"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "端末の空き容量が不足しています"
        else -> "インストールに失敗しました" + (detail?.let { " ($it)" } ?: "")
    }
}
