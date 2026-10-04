package com.ttech.bikenavi.nav

import com.ttech.bikenavi.domain.ArrivalSummary
import com.ttech.bikenavi.domain.Maneuver
import com.ttech.bikenavi.domain.Place
import com.ttech.bikenavi.domain.Route
import com.ttech.track.domain.LatLon
import kotlinx.coroutines.flow.MutableStateFlow

/** 画面から案内のサービスへ渡す、出発の指示(ルートは受け渡しできない型なので、ここで橋渡しする) */
data class PendingStart(
    val route: Route,
    val destination: Place,
    /** 画面で先に作った天気・補給スポットの案内の文。null なら、サービスが作る */
    val briefing: List<String>?,
    /** デバッグ用: 実際に走らず、ルートの上を [simulateSpeedMps] m/秒で走ったことにする(null なら、GPSで案内する) */
    val simulateSpeedMps: Double? = null,
)

/** 案内中の画面に出す情報。サービスが1秒ごとに更新する */
data class NavView(
    val route: Route,
    val destination: Place,
    val position: LatLon?,
    val bearing: Double?,
    val speedKmh: Double?,
    val progressM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val etaMs: Long?,
    val next: Maneuver?,
    val distToNextM: Double?,
    val lastSpoken: String?,
    val offRoute: Boolean,
    val rerouting: Boolean,
    val gpsWaiting: Boolean,
    val arrival: ArrivalSummary?,
    val simulated: Boolean,
)

/** サービスと画面のあいだで、案内の状況をやり取りする */
object NavState {
    val view = MutableStateFlow<NavView?>(null)
    val notice = MutableStateFlow<String?>(null)
    val voiceAvailable = MutableStateFlow<Boolean?>(null)

    @Volatile
    var pending: PendingStart? = null
}
