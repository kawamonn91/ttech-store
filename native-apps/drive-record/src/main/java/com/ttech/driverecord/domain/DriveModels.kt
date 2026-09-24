package com.ttech.driverecord.domain

import com.ttech.track.domain.*

import kotlinx.serialization.Serializable

/** 記録を始めたきっかけ */
object Trigger {
    const val ANDROID_AUTO = "android_auto"
    const val MANUAL = "manual"
}

/**
 * 1回のドライブの概要(一覧に出す情報)。点ごとの詳細は別ファイル(TrackCodec)に持つ。
 * [finished] が false のものは、記録の途中でアプリが終了した記録で、次に起動したときに復旧する。
 */
@Serializable
data class DriveSummary(
    /** 記録の開始時刻(エポックms)を文字列にしたもの。ファイル名にもなる */
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long = startTimeMs,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    /** 出発地・到着地の名前(住所)。取得できたときだけ */
    val startLabel: String? = null,
    val endLabel: String? = null,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0,
    val movingMs: Long = 0,
    val avgMovingSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    val minAltitudeM: Double? = null,
    val maxAltitudeM: Double? = null,
    val stopCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardAccelCount: Int = 0,
    val sharpCornerCount: Int = 0,
    val gapCount: Int = 0,
    val pointCount: Int = 0,
    val avgAccuracyM: Double? = null,
    val trigger: String = Trigger.MANUAL,
    val finished: Boolean = false,
) {
    /** 記録開始からのおおよその経過(記録中の画面用) */
    val startLatLon: LatLon get() = LatLon(startLat, startLon)
    val endLatLon: LatLon get() = LatLon(endLat, endLon)

    companion object {
        fun from(id: String, stats: DriveStats, points: List<TrackPoint>, trigger: String, finished: Boolean): DriveSummary {
            val first = points.firstOrNull()
            val last = points.lastOrNull()
            return DriveSummary(
                id = id,
                startTimeMs = first?.timeMs ?: id.toLongOrNull() ?: 0L,
                endTimeMs = last?.timeMs ?: first?.timeMs ?: 0L,
                startLat = first?.lat ?: 0.0,
                startLon = first?.lon ?: 0.0,
                endLat = last?.lat ?: 0.0,
                endLon = last?.lon ?: 0.0,
                distanceM = stats.distanceM,
                durationMs = stats.durationMs,
                movingMs = stats.movingMs,
                avgMovingSpeedMps = stats.avgMovingSpeedMps,
                maxSpeedMps = stats.maxSpeedMps,
                elevationGainM = stats.elevationGainM,
                elevationLossM = stats.elevationLossM,
                minAltitudeM = stats.minAltitudeM,
                maxAltitudeM = stats.maxAltitudeM,
                stopCount = stats.stops.size,
                hardBrakeCount = stats.hardBrakeCount,
                hardAccelCount = stats.hardAccelCount,
                sharpCornerCount = stats.sharpCornerCount,
                gapCount = stats.gapCount,
                pointCount = stats.pointCount,
                avgAccuracyM = stats.avgAccuracyM,
                trigger = trigger,
                finished = finished,
            )
        }
    }
}
