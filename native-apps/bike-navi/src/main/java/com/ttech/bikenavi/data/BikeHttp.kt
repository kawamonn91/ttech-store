package com.ttech.bikenavi.data

import com.ttech.bikenavi.domain.BikeException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 応答。エラーの応答も、本文(BRouterは、ルート無しを400と本文(プレーンテキスト)で返す)を読めるように、そのまま返す */
data class HttpText(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

/**
 * 外部のサービス(経路検索・地名検索・天気・補給スポット検索)への GET。
 * どのサービスも、アプリを名乗る User-Agent を付ける(利用規約の求め)。通信できないときは、画面に出せる文言の例外にする。
 */
open class BikeHttp(private val client: OkHttpClient, private val userAgent: String) {
    open suspend fun get(url: String): HttpText = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept-Language", "ja").build()
        try {
            client.newCall(request).execute().use { response ->
                val text = HttpText(response.code, response.body.string())
                if (response.code == 429) throw BikeException("サービスが混み合っています。少し待ってから、もう一度お試しください")
                if (response.code >= 500) throw BikeException("サービスが一時的に使えません(${response.code})。しばらくしてからお試しください")
                text
            }
        } catch (e: BikeException) {
            throw e
        } catch (e: IOException) {
            throw BikeException("通信に失敗しました。ネットワーク接続を確認してください", e)
        }
    }

    /** 成功(2xx)の応答の本文だけを返す。それ以外は例外 */
    suspend fun getOk(url: String, what: String): String {
        val r = get(url)
        if (!r.ok) throw BikeException("${what}を取得できませんでした(${r.status})")
        return r.body
    }
}

/** 地名検索(Nominatim)の利用規約は「1秒に1回まで」。呼び出しの間隔をあける */
class RateLimiter(private val minIntervalMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var last = Long.MIN_VALUE / 2 // 最初の1回は待たない

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - now()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            last = now()
        }
    }
}
