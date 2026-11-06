package com.ttech.pocketdev.drive

import android.util.Log
import com.ttech.pocketdev.bridge.BridgeException
import com.ttech.pocketdev.bridge.FileBackend
import com.ttech.pocketdev.bridge.FileEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

/**
 * Google ドライブ直結の接続先。IDE のファイル操作を rclone の API（[RcloneDaemon]）で行う。
 * [root] はマイドライブの中の基準フォルダ（"" はマイドライブ全体）。
 *
 * 消したファイルとフォルダは、Drive のゴミ箱に入る（rclone の既定）。
 * ターミナルは無い（実行は Pocket Linux で、Drive と同期したフォルダを使う）。
 */
class DriveBackend(private val daemon: RcloneDaemon, root: String) : FileBackend {

    private val root = root.trim('/')

    override val rootLabel: String = if (this.root.isEmpty()) "マイドライブ" else "マイドライブ/${this.root}"
    override val hostLabel: String = "Google Drive"

    /** 接続できるか（ログインが生きているか・基準フォルダがあるか）を確かめる */
    fun check() {
        drive {
            if (root.isEmpty()) {
                daemon.call("operations/about", fsParams())
            } else if (stat("")?.isDir != true) {
                throw BridgeException("Google ドライブに「$root」というフォルダがありません", 404)
            }
            Unit
        }
    }

    override fun list(path: String): List<FileEntry> = drive {
        val out = daemon.call("operations/list", fsParams(remote(path)) { put("noMimeType", true) })
        out["list"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj.string("Name") ?: return@mapNotNull null
            val isDir = obj["IsDir"]?.jsonPrimitive?.booleanOrNull == true
            FileEntry(
                name = name,
                path = join(path, name),
                isDir = isDir,
                size = if (isDir) 0 else (obj["Size"]?.jsonPrimitive?.longOrNull ?: 0).coerceAtLeast(0),
                mtime = parseModTime(obj.string("ModTime")),
            )
        }
    }

    /** [path] の下のフォルダの名前（フォルダを選ぶ画面用。名前順） */
    fun listFolders(path: String): List<String> = drive {
        val out = daemon.call("operations/list", fsParams(remote(path)) { put("dirsOnly", true); put("noModTime", true); put("noMimeType", true) })
        out["list"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.string("Name") }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    override fun read(path: String, maxBytes: Long): ByteArray = drive {
        try {
            daemon.download(remote(path), maxBytes)
        } catch (e: TooLargeException) {
            throw BridgeException("大きすぎて開けません（%,d バイトまで）".format(e.limit), 413)
        }
    }

    override fun write(path: String, data: ByteArray) = drive {
        daemon.upload(remote(parentOf(path)), nameOf(path), data)
    }

    override fun makeDir(path: String) = drive {
        daemon.call("operations/mkdir", fsParams(remote(path)))
        Unit
    }

    override fun delete(path: String, recursive: Boolean) = drive {
        val item = stat(path) ?: throw BridgeException("存在しません: $path", 404)
        val method = when {
            !item.isDir -> "operations/deletefile"
            recursive -> "operations/purge"
            else -> "operations/rmdir"
        }
        daemon.call(method, fsParams(remote(path)))
        Unit
    }

    override fun rename(from: String, to: String): Unit = drive {
        val item = stat(from) ?: throw BridgeException("存在しません: $from", 404)
        if (stat(to) != null) throw BridgeException("既に存在します: $to", 409)
        if (item.isDir) {
            // フォルダはサーバー側で丸ごと移す。フォルダの ID の覚え（キャッシュ）が古くなるので捨てる
            daemon.call("sync/move", buildJsonObject {
                put("srcFs", Rclone.FS + remote(from))
                put("dstFs", Rclone.FS + remote(to))
                put("deleteEmptySrcDirs", true)
            })
            clearCache()
        } else {
            daemon.call("operations/movefile", buildJsonObject {
                put("srcFs", Rclone.FS)
                put("srcRemote", remote(from))
                put("dstFs", Rclone.FS)
                put("dstRemote", remote(to))
            })
        }
        Unit
    }

    private data class Stat(val isDir: Boolean)

    private fun stat(path: String): Stat? {
        val out = daemon.call("operations/stat", fsParams(remote(path)) { put("noModTime", true); put("noMimeType", true) })
        val item = out["item"]?.takeIf { it is JsonObject }?.jsonObject ?: return null
        return Stat(isDir = item["IsDir"]?.jsonPrimitive?.booleanOrNull == true)
    }

    private fun remote(path: String): String = join(root, path)

    private fun fsParams(remote: String = "", opt: (JsonObjectBuilder.() -> Unit)? = null): JsonObject =
        buildJsonObject {
            put("fs", Rclone.FS)
            put("remote", remote)
            if (opt != null) put("opt", buildJsonObject(opt))
        }

    /**
     * rclone のエラーを、利用者に見せる文の [BridgeException] にする。
     * PC など別の場所でフォルダが動かされるとフォルダの ID の覚えが古くなるので、失敗したら捨てておく
     * （次の操作で Drive に問い合わせ直す）。
     */
    private fun <T> drive(block: () -> T): T = try {
        block()
    } catch (e: BridgeException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Drive の操作に失敗しました: ${e.message}")
        clearCache()
        throw BridgeException(friendlyMessage(e), if (e is RcException && e.status == 404) 404 else 502)
    }

    private fun clearCache() {
        runCatching { daemon.call("fscache/clear") }
    }

    companion object {
        private const val TAG = "DriveBackend"

        fun join(a: String, b: String): String = listOf(a, b).filter { it.isNotEmpty() }.joinToString("/")

        fun parentOf(path: String): String = path.substringBeforeLast('/', "")

        fun nameOf(path: String): String = path.substringAfterLast('/')

        /** rclone の ModTime（RFC 3339）を UNIX 秒にする。読めなければ 0 */
        fun parseModTime(value: String?): Long =
            value?.let { runCatching { OffsetDateTime.parse(it).toEpochSecond() }.getOrNull() } ?: 0

        /** rclone の英語のエラーを、利用者に見せる日本語の文にする */
        fun friendlyMessage(e: Exception): String {
            val raw = e.message.orEmpty()
            val lower = raw.lowercase()
            return when {
                "invalid_grant" in lower || "token expired" in lower || "unauthorized_client" in lower ||
                    "empty token" in lower || "didn't find section in config file" in lower ->
                    "Google のログインが切れました。接続先の設定の「Drive」でログインし直してください"

                "ratelimitexceeded" in lower || "userratelimitexceeded" in lower || "quota" in lower ->
                    "Google ドライブの利用の上限に達しました。少し待ってからやり直してください"

                "storagequotaexceeded" in lower -> "Google ドライブの空き容量が足りません"

                "directory not found" in lower -> "フォルダが見つかりません（ほかの端末で移動・削除されたかもしれません）"

                "object not found" in lower -> "ファイルが見つかりません（ほかの端末で移動・削除されたかもしれません）"

                "directory not empty" in lower -> "フォルダが空ではありません"

                "proxyconnect" in lower || "bad gateway" in lower || "no such host" in lower ||
                    "connection refused" in lower || "i/o timeout" in lower || "deadline exceeded" in lower ||
                    "network is unreachable" in lower || e is java.net.SocketTimeoutException ->
                    "Google ドライブにつながりません。ネットワークを確認してください"

                else -> "Google ドライブの操作に失敗しました: $raw"
            }
        }

        private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
    }
}
