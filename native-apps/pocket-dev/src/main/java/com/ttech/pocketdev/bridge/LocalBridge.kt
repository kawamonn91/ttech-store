package com.ttech.pocketdev.bridge

import android.content.Context
import com.ttech.pocketdev.ConnectionTarget
import com.ttech.pocketdev.TargetType
import com.ttech.pocketdev.drive.Drive
import com.ttech.pocketdev.drive.DriveBackend
import com.ttech.pocketdev.ssh.SshBackend
import com.ttech.pocketdev.ssh.SshClient
import java.io.Closeable
import java.io.InputStream

/**
 * SSH・Google ドライブの接続先につなぐ、アプリ内の中継サーバーと、その裏の接続（SSH・rclone）。
 * WebView は [baseUrl] を [token] 付きで開く。トークンはつなぐたびに作る。
 */
class LocalBridge private constructor(
    private val server: BridgeServer,
    val token: String,
    private val resources: List<AutoCloseable>,
) : Closeable {

    val baseUrl: String get() = "http://${BridgeServer.LOCALHOST}:${server.listeningPort}"

    /** 止める（rclone が終わるまで待つことがあるので、メインスレッドで呼ばない） */
    override fun close() {
        runCatching { server.stop() }
        resources.forEach { runCatching { it.close() } }
    }

    companion object {
        /** つなぐ（時間がかかるので、IO のスレッドで呼ぶ） */
        fun start(context: Context, target: ConnectionTarget): LocalBridge {
            val assets = context.applicationContext.assets
            val openAsset: (String) -> InputStream? = { name -> runCatching { assets.open("pocket-dev/web/$name") }.getOrNull() }
            val token = randomToken(24)
            return when (target.type) {
                TargetType.SSH -> {
                    val client = SshClient(target)
                    try {
                        client.connect()
                        val backend = SshBackend(client, target)
                        launch(BridgeServer(token, backend, backend, openAsset), token, listOf(client))
                    } catch (e: Exception) {
                        client.close()
                        throw e
                    }
                }

                TargetType.GOOGLE_DRIVE -> {
                    val rclone = Drive.rclone(context)
                    if (!rclone.available) throw BridgeException("この端末の CPU では、Google ドライブ直結を使えません")
                    if (!rclone.isLoggedIn) {
                        throw BridgeException("Google にログインしていません。接続先の設定の「Drive」でログインしてください")
                    }
                    val daemon = Drive.daemon(context)
                    daemon.ensureStarted()
                    val backend = DriveBackend(daemon, target.driveRoot)
                    backend.check()
                    // ターミナルは無い（IDE は実行のボタンを隠す）。daemon は Drive シングルトンで共有する
                    launch(BridgeServer(token, backend, null, openAsset), token, emptyList())
                }

                else -> throw IllegalArgumentException("中継サーバーを使わない接続先です: ${target.type}")
            }
        }

        private fun launch(server: BridgeServer, token: String, resources: List<AutoCloseable>): LocalBridge {
            server.startServer()
            return LocalBridge(server, token, resources)
        }
    }
}
