package com.ttech.runtracker

import android.app.Application
import android.content.Context
import com.ttech.runtracker.data.BodyRepository
import com.ttech.runtracker.data.RunRepository
import com.ttech.runtracker.data.SettingsStore
import com.ttech.runtracker.domain.LiveRun
import com.ttech.runtracker.domain.RunRecorder
import com.ttech.runtracker.map.RunImages
import com.ttech.track.data.TrackFiles
import com.ttech.track.location.GpsStatus
import com.ttech.track.map.PlaceNamer
import com.ttech.track.map.RouteImageRenderer
import com.ttech.track.map.TileBytesCache
import com.ttech.track.map.TileRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class RunApplication : Application() {
    lateinit var container: RunContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = RunContainer(this)
    }
}

/** アプリ全体で共有する部品 */
class RunContainer(context: Context) {
    val files = TrackFiles(File(context.filesDir, "runs"))
    val repository = RunRepository(files)
    val settings = SettingsStore(context)
    val body = BodyRepository(File(context.filesDir, "body.json"))

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 地図タイルは、端末の「キャッシュ」に保存する(容量が足りなくなったら、OSが消してよい) */
    val tiles = TileRepository(
        TileBytesCache(
            dir = File(context.cacheDir, "tiles"),
            client = http,
            userAgent = "T-tech-RunTracker/${BuildConfig.VERSION_NAME} (Android; contact: kawamonn91@gmail.com)",
        ),
    )
    val images = RunImages(files, RouteImageRenderer(tiles))
    val places = PlaceNamer(context)

    /** 前回、計測の途中でアプリが終了していた記録を、点から復旧する。計測中のサービスがあれば、何もしない */
    suspend fun recoverUnfinished() {
        if (RunState.serviceRunning.value) return
        withContext(Dispatchers.IO) {
            val min = settings.settings.first().minDistanceM
            RunRecorder.recover(files, min, weightAt = body::weightAt)
        }
        repository.refresh()
    }
}

/** サービスと画面のあいだで、計測中の状況をやり取りする */
object RunState {
    val live = MutableStateFlow<LiveRun?>(null)
    val gps = MutableStateFlow(GpsStatus())
    val serviceRunning = MutableStateFlow(false)

    /** 画面に出す短いお知らせ(GPSが切れている、記録に残さなかった、など) */
    val notice = MutableStateFlow<String?>(null)

    /** 計測を終えて保存した記録の ID。画面が受け取ったら、詳細を開いて null に戻す */
    val finishedId = MutableStateFlow<String?>(null)
}
