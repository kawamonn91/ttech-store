package com.ttech.track.domain

/**
 * GPSの1回分の測位(1秒に1回)。端末が返した値を、加工せずそのまま残す。
 * 値が取れなかった項目は null。
 *
 * @property speed 対地速度(m/s)。ドップラー由来で、位置の差から求めるより正確
 * @property bearing 進行方位(度、北=0)。ほぼ停止しているときは無効
 * @property hAcc 水平方向の精度(m)。真の位置が、この半径の円に約68%の確率で入る
 * @property vAcc 垂直方向(標高)の精度(m)
 * @property sAcc 速度の精度(m/s)
 * @property satellites 測位に使った衛星の数
 */
data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    val hAcc: Double? = null,
    val vAcc: Double? = null,
    val sAcc: Double? = null,
    val satellites: Int? = null,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

/** 記録するかどうかの判断結果 */
/** 除いた理由の種類。[JUMP] は「直前の点から現実的でない距離を移動した」場合で、基準の取り直しの判断に使う */
enum class RejectKind { RANGE, ACCURACY, TIME, JUMP }

sealed interface FixDecision {
    data object Accept : FixDecision
    data class Reject(val reason: String, val kind: RejectKind) : FixDecision
}

/**
 * 明らかにおかしい測位を取り除く。取り除くのは「あり得ない」ものだけで、
 * 位置の平滑化などの加工はしない(記録そのものは正確に残し、表示のときに必要なら整える)。
 *  - 精度が悪すぎる(トンネル・ビル街での測位開始直後など)
 *  - 時刻が戻っている・同時刻
 *  - 直前の点からの移動が、現実の車の速度を超えている(位置が飛んだ)
 */
class TrackFilter(
    private val maxAccuracyM: Double = 30.0,
    private val maxSpeedMps: Double = 80.0,
) {
    fun decide(prev: TrackPoint?, p: TrackPoint): FixDecision {
        if (p.lat !in -90.0..90.0 || p.lon !in -180.0..180.0) return FixDecision.Reject("座標が範囲外", RejectKind.RANGE)
        if (p.hAcc != null && p.hAcc > maxAccuracyM) return FixDecision.Reject("精度が悪い(${p.hAcc.toInt()}m)", RejectKind.ACCURACY)
        if (prev == null) return FixDecision.Accept
        if (p.timeMs <= prev.timeMs) return FixDecision.Reject("時刻が戻っている", RejectKind.TIME)
        val dtSec = (p.timeMs - prev.timeMs) / 1000.0
        val distance = GeoMath.distanceMeters(prev.latLon, p.latLon)
        // 精度の分だけ余裕を見る(誤差の範囲内で位置がずれることは普通にある)
        val slack = (p.hAcc ?: 0.0) + (prev.hAcc ?: 0.0)
        if (distance - slack > maxSpeedMps * dtSec) return FixDecision.Reject("位置が飛んだ", RejectKind.JUMP)
        return FixDecision.Accept
    }
}
