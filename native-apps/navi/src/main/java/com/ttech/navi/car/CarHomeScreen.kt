package com.ttech.navi.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.ttech.navi.NaviContainer
import com.ttech.navi.data.CurrentLocation
import com.ttech.navi.domain.NaviException
import com.ttech.navi.domain.Place
import com.ttech.navi.nav.NavService
import com.ttech.navi.nav.NavState
import com.ttech.navi.nav.PendingStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 車の画面のホーム。運転中の文字入力は避けたいので、目的地の検索はできず、最近の行き先から選ぶだけ。
 * 目的地を決めるのは、あらかじめスマホ側で(検索・地図)行っておく想定。
 */
class CarHomeScreen(carContext: CarContext, private val container: NaviContainer) : Screen(carContext) {
    private var recents: List<Place> = emptyList()
    private var starting = false

    init {
        lifecycleScope.launch {
            container.recents.places.collect {
                recents = it
                invalidate()
            }
        }
        // スマホ側でナビが始まった(既に案内中だった・このあと始まった)ら、案内画面に切り替える。
        // StateFlowは案内中ずっと(1秒ごとに)値を出し続けるので、null→値ありに変わった1回だけ画面を重ねる
        lifecycleScope.launch {
            var navigating = false
            NavState.view.collect { view ->
                if (view != null && !navigating) {
                    navigating = true
                    starting = false
                    screenManager.push(CarNavigationScreen(carContext, container))
                } else if (view == null) {
                    navigating = false
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().apply {
            setNoItemsMessage("最近の行き先がありません。スマホでアプリを開いて、行き先を検索してください")
            for (place in recents) {
                addItem(
                    Row.Builder()
                        .setTitle(place.name)
                        .apply { if (place.detail.isNotEmpty()) addText(place.detail) }
                        .setEnabled(!starting)
                        .setOnClickListener { start(place) }
                        .build(),
                )
            }
        }.build()
        return ListTemplate.Builder()
            .setTitle("先読みナビ")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(starting)
            .setSingleList(list)
            .build()
    }

    private fun start(place: Place) {
        if (starting) return
        starting = true
        invalidate()
        lifecycleScope.launch {
            try {
                val origin = CurrentLocation.get(carContext) ?: throw NaviException("現在地がわかりません。スマホの位置情報の設定を確認してください")
                val route = container.osrm.route(origin, place.latLon)
                NavState.pending = PendingStart(route, place, briefing = null, simulateSpeedMps = null)
                NavService.start(carContext)
                container.recents.add(place)
                // 画面の切り替えは、上の NavState.view の監視が行う。始まらなければ、しばらくして諦める
                delay(8_000)
                if (starting) {
                    starting = false
                    invalidate()
                }
            } catch (e: Exception) {
                starting = false
                invalidate()
                val message = (e as? NaviException)?.message ?: "ナビを始められませんでした"
                CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
            }
        }
    }
}
