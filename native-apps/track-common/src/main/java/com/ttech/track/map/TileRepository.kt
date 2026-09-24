package com.ttech.track.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.ttech.track.domain.TileUrls
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 地図タイルの画像(バイト列)を、端末に保存しながら取得する。
 * 一度取った地域は端末に残り、次からは通信しない(通信量と、地図サービスへの負荷を減らす)。
 * 通信するときは、アプリを名乗る User-Agent を付ける(地図サービスの利用規約の求め)。
 */
class TileBytesCache(
    private val dir: File,
    private val client: OkHttpClient,
    private val userAgent: String,
    private val urlFor: (Int, Int, Int) -> String = TileUrls::url,
    private val maxAgeMs: Long = 30L * 24 * 3600 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val limiter = Semaphore(6)

    fun file(z: Int, x: Int, y: Int) = File(dir, "osm/$z/$x/$y.png")

    /** 端末に保存済み(期限内)ならそれを、無ければ通信して取得して保存する。取れなければ null */
    suspend fun bytes(z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(z, x, y)
        if (f.exists() && f.length() > 0 && now() - f.lastModified() < maxAgeMs) {
            runCatching { return@withContext f.readBytes() }
        }
        limiter.withPermit {
            try {
                val request = Request.Builder().url(urlFor(z, x, y)).header("User-Agent", userAgent).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withPermit staleOrNull(f)
                    val data = response.body.bytes()
                    if (data.isEmpty()) return@withPermit staleOrNull(f)
                    f.parentFile?.mkdirs()
                    val tmp = File(f.path + ".tmp")
                    tmp.writeBytes(data)
                    if (!tmp.renameTo(f)) f.writeBytes(data)
                    tmp.delete()
                    data
                }
            } catch (_: IOException) {
                staleOrNull(f) // 通信できないときは、期限切れでも保存済みのものを使う
            }
        }
    }

    private fun staleOrNull(f: File): ByteArray? = if (f.exists() && f.length() > 0) runCatching { f.readBytes() }.getOrNull() else null
}

/** 地図タイルをビットマップにして、メモリにも持つ */
class TileRepository(private val bytes: TileBytesCache) {
    private val memory = object : LruCache<String, Bitmap>(128) {}

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    /** メモリにあるものだけ(描画の途中で待たずに使う) */
    fun cached(z: Int, x: Int, y: Int): Bitmap? {
        val (nx, ny) = TileUrls.normalize(z, x, y) ?: return null
        return memory.get(key(z, nx, ny))
    }

    suspend fun bitmap(z: Int, x: Int, y: Int): Bitmap? {
        val (nx, ny) = TileUrls.normalize(z, x, y) ?: return null
        val k = key(z, nx, ny)
        memory.get(k)?.let { return it }
        val data = bytes.bytes(z, nx, ny) ?: return null
        val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size) } ?: return null
        memory.put(k, bmp)
        return bmp
    }
}
