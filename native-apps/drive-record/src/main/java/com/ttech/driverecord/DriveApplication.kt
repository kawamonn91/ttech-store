package com.ttech.driverecord

import android.app.Application
import android.content.Context
import com.ttech.driverecord.data.DriveRepository
import com.ttech.driverecord.data.SettingsStore
import com.ttech.driverecord.domain.LiveDrive
import com.ttech.driverecord.map.DriveImages
import com.ttech.track.map.PlaceNamer
import com.ttech.track.data.TrackFiles
import com.ttech.track.location.GpsStatus
import com.ttech.track.map.RouteImageRenderer
import com.ttech.track.map.TileBytesCache
import com.ttech.track.map.TileRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

class DriveApplication : Application() {
    lateinit var container: DriveContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DriveContainer(this)
    }
}

/** アプリ全体で共有する部品 */
class DriveContainer(context: Context) {
    val files = TrackFiles(File(context.filesDir, "drives"))
    val repository = DriveRepository(files)
    val settings = SettingsStore(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 地図タイルは、端末の「キャッシュ」に保存する(容量が足りなくなったら、OSが消してよい) */
    val tiles = TileRepository(
        TileBytesCache(
            dir = File(context.cacheDir, "tiles"),
            client = http,
            userAgent = "T-tech-DriveRecord/${BuildConfig.VERSION_NAME} (Android; contact: kawamonn91@gmail.com)",
        ),
    )
    val images = DriveImages(files, RouteImageRenderer(tiles))
    val places = PlaceNamer(context)
}

/** サービスと画面のあいだで、記録中の状況をやり取りする */
object DriveState {
    val live = MutableStateFlow<LiveDrive?>(null)
    val gps = MutableStateFlow(GpsStatus())
    val carConnected = MutableStateFlow(false)
    val serviceRunning = MutableStateFlow(false)

    /** 画面に出す短いお知らせ(GPSが切れている、など) */
    val notice = MutableStateFlow<String?>(null)
}
