package com.ttech.navi.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.ttech.navi.NaviApplication

/**
 * 車の画面が開かれたときの入り口。土台は常に[CarHomeScreen](最近の行き先)で、すでに案内中なら
 * その初期化のなかで、すぐ上に[CarNavigationScreen]を重ねる(土台を案内画面にはしない。案内が終わった
 * ときに戻る先がなくなってしまうため)。
 */
class NaviCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val container = (carContext.applicationContext as NaviApplication).container
        return CarHomeScreen(carContext, container)
    }
}
