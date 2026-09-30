package com.ttech.navi.car

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import com.ttech.navi.domain.Maneuver
import com.ttech.navi.domain.ManeuverKind

/** ドメインの動作(曲がる方向など)を、車の画面が矢印の絵を選ぶための種類(Maneuver.TYPE_*)に変える */
internal fun carManeuverType(m: Maneuver?): Int {
    if (m == null) return CarManeuver.TYPE_STRAIGHT
    val left = m.modifier?.contains("left") == true
    val right = m.modifier?.contains("right") == true
    val sharp = m.modifier?.startsWith("sharp") == true
    val slight = m.modifier?.startsWith("slight") == true
    return when (m.kind) {
        ManeuverKind.Turn -> when {
            m.modifier == "uturn" -> CarManeuver.TYPE_U_TURN_LEFT
            left && sharp -> CarManeuver.TYPE_TURN_SHARP_LEFT
            right && sharp -> CarManeuver.TYPE_TURN_SHARP_RIGHT
            left && slight -> CarManeuver.TYPE_TURN_SLIGHT_LEFT
            right && slight -> CarManeuver.TYPE_TURN_SLIGHT_RIGHT
            left -> CarManeuver.TYPE_TURN_NORMAL_LEFT
            right -> CarManeuver.TYPE_TURN_NORMAL_RIGHT
            else -> CarManeuver.TYPE_STRAIGHT
        }
        ManeuverKind.Fork -> when {
            left -> CarManeuver.TYPE_FORK_LEFT
            right -> CarManeuver.TYPE_FORK_RIGHT
            else -> CarManeuver.TYPE_STRAIGHT
        }
        ManeuverKind.OnRamp -> when {
            left && sharp -> CarManeuver.TYPE_ON_RAMP_SHARP_LEFT
            right && sharp -> CarManeuver.TYPE_ON_RAMP_SHARP_RIGHT
            left && slight -> CarManeuver.TYPE_ON_RAMP_SLIGHT_LEFT
            right && slight -> CarManeuver.TYPE_ON_RAMP_SLIGHT_RIGHT
            left -> CarManeuver.TYPE_ON_RAMP_NORMAL_LEFT
            else -> CarManeuver.TYPE_ON_RAMP_NORMAL_RIGHT
        }
        // Maneuver.TYPE_OFF_RAMP には「急」の種類が無いので、普通の出口として扱う
        ManeuverKind.OffRamp -> when {
            left && slight -> CarManeuver.TYPE_OFF_RAMP_SLIGHT_LEFT
            right && slight -> CarManeuver.TYPE_OFF_RAMP_SLIGHT_RIGHT
            left -> CarManeuver.TYPE_OFF_RAMP_NORMAL_LEFT
            else -> CarManeuver.TYPE_OFF_RAMP_NORMAL_RIGHT
        }
        // 日本は左側通行なので、環状交差点は時計回り(CW)
        ManeuverKind.Roundabout -> CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
        ManeuverKind.Arrive -> CarManeuver.TYPE_DESTINATION
    }
}

/** ドメインの距離(m)を、車の画面の単位付き距離に変える(1km未満はメートル、それ以上は小数1桁のキロ) */
internal fun carDistance(m: Double): Distance {
    val v = m.coerceAtLeast(0.0)
    return if (v < 1000) Distance.create(v, Distance.UNIT_METERS) else Distance.create(v / 1000.0, Distance.UNIT_KILOMETERS_P1)
}
