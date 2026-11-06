package com.ttech.pocketdev.bridge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.io.InputStream
import java.security.SecureRandom
import java.util.Base64

/** URL セーフな乱数トークンを作る（ブリッジ認証・rclone rcd・ローカルプロキシで共通利用） */
fun randomToken(byteLength: Int = 18): String {
    val bytes = ByteArray(byteLength).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** JSON オブジェクトから文字列フィールドを取り出す（無ければ null） */
fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

/** IDE（web/ide.html）のファイル一覧の1件。pocket-dev のサーバー（agent/files.py）と同じ形で返す */
data class FileEntry(
    val name: String,
    /** 基準フォルダからの相対パス（区切りは "/"） */
    val path: String,
    val isDir: Boolean,
    val size: Long,
    /** 更新日時（UNIX 秒） */
    val mtime: Long,
)

/** 利用者にそのまま見せてよいエラー。[status] は IDE に返す HTTP のステータス */
class BridgeException(message: String, val status: Int = 400) : Exception(message)

/**
 * 中継サーバー（[BridgeServer]）が IDE の `/api/fs/...` を任せる先（SSH・Google ドライブ）。
 * パスは [BridgeServer.normalizePath] で整えた、基準フォルダからの相対パス（"" が基準フォルダ）。
 * 中継サーバーは複数のスレッドから呼ぶ。
 */
interface FileBackend {
    /** `/api/info` の root に載せる、基準フォルダの表示名 */
    val rootLabel: String

    /** `/api/info` の hostname に載せる、接続先の表示名 */
    val hostLabel: String

    fun list(path: String): List<FileEntry>

    /** 中身を読む。[maxBytes] より大きいときは [BridgeException] にする */
    fun read(path: String, maxBytes: Long): ByteArray

    /** 中身を丸ごと書く（無ければ作る。親のフォルダも作る） */
    fun write(path: String, data: ByteArray)

    fun makeDir(path: String)

    fun delete(path: String, recursive: Boolean)

    fun rename(from: String, to: String)
}

/** IDE のターミナル（`/ws/pty`）を任せる先。無い接続先（Google ドライブ）では IDE が実行のボタンを隠す */
interface PtyBackend {
    fun open(cols: Int, rows: Int): PtySession
}

interface PtySession : Closeable {
    /** シェルの出力。終わると -1 を返す */
    val output: InputStream

    fun write(data: ByteArray)

    fun resize(cols: Int, rows: Int)
}
