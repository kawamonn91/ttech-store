package com.ttech.bikenavi

import android.app.Application
import android.content.Context
import com.ttech.bikenavi.data.BRouterClient
import com.ttech.bikenavi.data.BikeHttp
import com.ttech.bikenavi.data.BriefingService
import com.ttech.bikenavi.data.NominatimClient
import com.ttech.bikenavi.data.OpenMeteoClient
import com.ttech.bikenavi.data.OverpassClient
import com.ttech.bikenavi.data.PlaceSearch
import com.ttech.bikenavi.data.RecentPlaces
import com.ttech.bikenavi.data.SettingsStore
import com.ttech.track.map.TileBytesCache
import com.ttech.track.map.TileRepository
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class BikeApplication : Application() {
    lateinit var container: BikeContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = BikeContainer(this)
    }
}

/** アプリ全体で共有する部品 */
class BikeContainer(context: Context) {
    private val appContext = context.applicationContext

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** どの外部サービスにも、アプリを名乗る User-Agent を付ける(各サービスの利用規約の求め) */
    val userAgent = "T-tech-BikeNavi/${BuildConfig.VERSION_NAME} (Android; contact: kawamonn91@gmail.com)"
    private val bikeHttp = BikeHttp(http, userAgent)

    val brouter = BRouterClient(bikeHttp)
    val meteo = OpenMeteoClient(bikeHttp)
    val overpass = OverpassClient(bikeHttp)
    val search = PlaceSearch(NominatimClient(bikeHttp))
    val briefing = BriefingService(meteo, overpass)

    val settings = SettingsStore(appContext)
    val recents = RecentPlaces(appContext)

    /** 走行中の地図は、回転ぶん多くのタイルを描くので、メモリに持つ枚数を増やしておく。タイルは端末の「キャッシュ」に保存する */
    val tiles = TileRepository(
        TileBytesCache(dir = File(appContext.cacheDir, "tiles"), client = http, userAgent = userAgent),
        memoryTiles = 240,
    )
}
