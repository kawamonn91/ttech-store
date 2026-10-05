package com.ttech.navi

import android.app.Application
import android.content.Context
import com.ttech.navi.data.BriefingService
import com.ttech.navi.data.GsiClient
import com.ttech.navi.data.NaviHttp
import com.ttech.navi.data.NominatimClient
import com.ttech.navi.data.OpenMeteoClient
import com.ttech.navi.data.OsrmClient
import com.ttech.navi.data.ValhallaClient
import com.ttech.navi.data.PlaceSearch
import com.ttech.navi.data.RecentPlaces
import com.ttech.navi.data.SettingsStore
import com.ttech.navi.domain.MuniTable
import com.ttech.track.map.TileBytesCache
import com.ttech.track.map.TileRepository
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class NaviApplication : Application() {
    lateinit var container: NaviContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = NaviContainer(this)
    }
}

/** アプリ全体で共有する部品 */
class NaviContainer(context: Context) {
    private val appContext = context.applicationContext

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** どの外部サービスにも、アプリを名乗る User-Agent を付ける(各サービスの利用規約の求め) */
    val userAgent = "T-tech-Navi/${BuildConfig.VERSION_NAME} (Android; contact: kawamonn91@gmail.com)"
    private val naviHttp = NaviHttp(http, userAgent)

    val osrm = OsrmClient(naviHttp)
    val valhalla = ValhallaClient(naviHttp)
    val meteo = OpenMeteoClient(naviHttp)
    val gsi = GsiClient(naviHttp)
    val search = PlaceSearch(NominatimClient(naviHttp), gsi)

    /** 市区町村コード表(国土地理院)。初めて使うときに読み込む */
    val muni: MuniTable by lazy { MuniTable.parse(appContext.assets.open("muni.csv").bufferedReader().use { it.readText() }) }

    val briefing = BriefingService(meteo, gsi) { muni }
    val settings = SettingsStore(appContext)
    val recents = RecentPlaces(appContext)

    /** 走行中の地図は、回転ぶん多くのタイルを描くので、メモリに持つ枚数を増やしておく。タイルは端末の「キャッシュ」に保存する */
    val tiles = TileRepository(
        TileBytesCache(dir = File(appContext.cacheDir, "tiles"), client = http, userAgent = userAgent),
        memoryTiles = 240,
    )
}
