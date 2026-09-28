package com.ttech.navi.nav

import com.ttech.navi.domain.ArrivalSummary
import com.ttech.navi.domain.Maneuver
import com.ttech.navi.domain.Place
import com.ttech.navi.domain.Route
import com.ttech.track.domain.LatLon
import kotlinx.coroutines.flow.MutableStateFlow

/** 画面から案内のサービスへ渡す、出発の指示(ルートは受け渡しできない型なので、ここで橋渡しする) */
data class PendingStart(
    val route: Route,
    val destination: Place,
    /** 画面で先に作った天気の案内の文。null なら、サービスが作る */
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
    /** 到着予定の時刻(エポックms) */
    val etaMs: Long?,
    val next: Maneuver?,
    val distToNextM: Double?,
    val afterNext: Maneuver?,
    val gapM: Double?,
    /** いまの都道府県・市区町村(分かったとき) */
    val regionText: String?,
    /** 最後に声で伝えた内容(声が出せない端末でも、内容が分かるように画面にも出す) */
    val lastSpoken: String?,
    val offRoute: Boolean,
    val rerouting: Boolean,
    /** 位置がまだ取れていない */
    val gpsWaiting: Boolean,
    val arrival: ArrivalSummary?,
    val simulated: Boolean,
)

/** サービスと画面のあいだで、案内の状況をやり取りする */
object NavState {
    /** null なら、案内していない */
    val view = MutableStateFlow<NavView?>(null)

    /** 画面に出す短いお知らせ(GPSを使えない、再検索できない、など) */
    val notice = MutableStateFlow<String?>(null)

    /** 音声合成(日本語)が使えるか。null は確認中 */
    val voiceAvailable = MutableStateFlow<Boolean?>(null)

    /** 案内を始める指示。画面が入れて、サービスが取り出す */
    @Volatile
    var pending: PendingStart? = null
}
