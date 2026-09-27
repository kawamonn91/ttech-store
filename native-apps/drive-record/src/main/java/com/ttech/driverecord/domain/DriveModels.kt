package com.ttech.driverecord.domain

import com.ttech.track.domain.*

import kotlinx.serialization.Serializable

/** 1日ぶんの記録をまとめたもの(一覧の「日付ごと」表示用) */
data class DailyDriveSummary(
    val dayKey: String,
    val drives: List<DriveSummary>,
) {
    val driveCount: Int get() = drives.size
    val totalDistanceM: Double get() = drives.sumOf { it.distanceM }
    val totalDurationMs: Long get() = drives.sumOf { it.durationMs }
    val totalMovingMs: Long get() = drives.sumOf { it.movingMs }
    val maxSpeedMps: Double get() = drives.maxOf { it.maxSpeedMps }
    /** 一覧の見出しに使う代表の時刻(その日でいちばん新しい記録の開始時刻) */
    val representativeTimeMs: Long get() = drives.maxOf { it.startTimeMs }
}

/** 記録を、開始日([Format.dayKey])ごとにまとめる。新しい日が先 */
fun dailySummaries(drives: List<DriveSummary>): List<DailyDriveSummary> =
    drives.groupBy { Format.dayKey(it.startTimeMs) }
        .map { (key, list) -> DailyDriveSummary(key, list.sortedByDescending { it.startTimeMs }) }
        .sortedByDescending { it.dayKey }

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
    /** Android Auto の切断・再接続から検知した休憩の区間。詳細画面で統計を作り直すときに使う */
    val carBreaks: List<CarBreakSpan> = emptyList(),
) {
    /** 記録開始からのおおよその経過(記録中の画面用) */
    val startLatLon: LatLon get() = LatLon(startLat, startLon)
    val endLatLon: LatLon get() = LatLon(endLat, endLon)

    companion object {
        fun from(id: String, stats: DriveStats, points: List<TrackPoint>, trigger: String, finished: Boolean, carBreaks: List<CarBreakSpan> = emptyList()): DriveSummary {
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
                carBreaks = carBreaks,
            )
        }
    }
}
