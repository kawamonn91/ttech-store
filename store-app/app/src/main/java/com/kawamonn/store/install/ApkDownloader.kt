package com.kawamonn.store.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * APK を Range リクエストで再開しながらダウンロードする。
 * 途中経過は `<name>.part` に保存し、完了したら `<name>` にリネームする。
 */
class ApkDownloader(private val client: OkHttpClient) {

    suspend fun download(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val resumeFrom = if (part.exists()) part.length() else 0L

        val request = Request.Builder().url(url).apply {
            if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
        }.build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 416 -> { // 範囲外: 既に完全に落ちている or 壊れている。やり直す
                    part.delete()
                    throw IOException("ダウンロードを再開できませんでした。もう一度お試しください")
                }
                !response.isSuccessful -> throw IOException("ダウンロードに失敗しました (${response.code})")
            }
            val resumed = response.code == 206
            if (!resumed) part.delete()
            val offset = if (resumed) resumeFrom else 0L
            val body = response.body
            val total = body.contentLength().takeIf { it >= 0 }?.plus(offset)

            RandomAccessFile(part, "rw").use { out ->
                out.seek(offset)
                var downloaded = offset
                val buffer = ByteArray(64 * 1024)
                val source = body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                if (total != null && downloaded != total) {
                    throw IOException("ダウンロードが途中で切れました。もう一度お試しください")
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("ファイルを保存できませんでした")
        target
    }
}
