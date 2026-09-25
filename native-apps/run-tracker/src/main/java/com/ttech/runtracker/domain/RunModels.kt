package com.ttech.runtracker.domain

import com.ttech.track.domain.LatLon
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** 一時停止していた時間帯(エポックms)。この間の移動は、距離にも時間にも数えない */
@Serializable
data class RunPause(val startMs: Long, val endMs: Long)

/** 自己ベストを取る距離 */
enum class EffortDef(val key: String, val label: String, val meters: Double) {
    K1("1k", "1 km", 1000.0),
    MILE("1mi", "1マイル", 1609.344),
    K3("3k", "3 km", 3000.0),
    K5("5k", "5 km", 5000.0),
    K10("10k", "10 km", 10_000.0),
    HALF("half", "ハーフマラソン", 21_097.5),
    FULL("full", "フルマラソン", 42_195.0),
    ;

    companion object {
        fun byKey(key: String): EffortDef? = entries.firstOrNull { it.key == key }
    }
}

/** ある距離を走った最速の時間(走っていた時間のみ) */
@Serializable
data class Effort(val key: String, val timeMs: Long) {
    val def: EffortDef? get() = EffortDef.byKey(key)
}

/**
 * 1回のランの概要(一覧に出す情報)。点ごとの詳細は別ファイル(TrackCodec)に持つ。
 * [finished] が false のものは、記録の途中でアプリが終了した記録で、次に起動したときに復旧する。
 */
@Serializable
data class RunSummary(
    /** 記録の開始時刻(エポックms)を文字列にしたもの。ファイル名にもなる */
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long = startTimeMs,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    /** 出発地の名前(住所)。取得できたときだけ */
    val startLabel: String? = null,
    /** ユーザーがつけたタイトル。無ければ時間帯から自動でつける([RunTitle]) */
    val title: String? = null,
    val distanceM: Double = 0.0,
    /** 開始から終了までの時間(一時停止・信号待ちを含む) */
    val elapsedMs: Long = 0,
    /** 走っていた時間(一時停止・止まっていた時間を除く)。ペースはこれで求める */
    val movingMs: Long = 0,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    val caloriesKcal: Double = 0.0,
    /** 消費カロリーの計算に使った体重。記録していなかったときは null(標準の体重で計算) */
    val weightKg: Double? = null,
    val efforts: List<Effort> = emptyList(),
    val pauses: List<RunPause> = emptyList(),
    val pointCount: Int = 0,
    val finished: Boolean = false,
) {
    val startLatLon: LatLon get() = LatLon(startLat, startLon)
    val avgPaceSecPerKm: Double get() = if (distanceM > 0 && movingMs > 0) movingMs / 1000.0 / (distanceM / 1000.0) else 0.0
    val avgSpeedMps: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: RunTitle.auto(startTimeMs)

    fun effort(def: EffortDef): Effort? = efforts.firstOrNull { it.key == def.key }

    companion object {
        fun from(
            id: String,
            stats: RunStats,
            points: List<com.ttech.track.domain.TrackPoint>,
            pauses: List<RunPause>,
            weightKg: Double?,
            finished: Boolean,
        ): RunSummary {
            val first = points.firstOrNull()
            val last = points.lastOrNull()
            return RunSummary(
                id = id,
                startTimeMs = first?.timeMs ?: id.toLongOrNull() ?: 0L,
                endTimeMs = last?.timeMs ?: first?.timeMs ?: 0L,
                startLat = first?.lat ?: 0.0,
                startLon = first?.lon ?: 0.0,
                endLat = last?.lat ?: 0.0,
                endLon = last?.lon ?: 0.0,
                distanceM = stats.distanceM,
                elapsedMs = stats.elapsedMs,
                movingMs = stats.movingMs,
                elevationGainM = stats.elevationGainM,
                elevationLossM = stats.elevationLossM,
                caloriesKcal = stats.caloriesKcal,
                weightKg = weightKg,
                efforts = stats.efforts.map { Effort(it.def.key, it.timeMs) },
                pauses = pauses.filter { p -> first != null && last != null && p.endMs >= first.timeMs && p.startMs <= last.timeMs },
                pointCount = stats.pointCount,
                finished = finished,
            )
        }
    }
}

/** 開始した時間帯から、「朝ラン」のような名前をつける */
object RunTitle {
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    fun auto(startMs: Long, zone: ZoneId = JST): String {
        val hour = Instant.ofEpochMilli(startMs).atZone(zone).hour
        return when (hour) {
            in 5..9 -> "朝ラン"
            in 10..15 -> "昼ラン"
            in 16..18 -> "夕方ラン"
            in 19..23 -> "夜ラン"
            else -> "深夜ラン"
        }
    }
}

/** ユーザーが変えられる設定 */
data class RunSettings(
    /** これより短い(m)ランは、記録に残さない(誤って始めたときなど) */
    val minDistanceM: Int = 100,
    val mapStyleDark: Boolean = true,
    /** 計測の画面を見ている間は、画面を消さない */
    val keepScreenOn: Boolean = true,
) {
    companion object {
        val MIN_DISTANCE_CHOICES = listOf(0, 50, 100, 300, 500)
    }
}

/** 体重の記録1件 */
@Serializable
data class WeightEntry(val timeMs: Long, val weightKg: Double)
