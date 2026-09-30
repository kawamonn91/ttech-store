package com.ttech.navi.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.Trip
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import androidx.lifecycle.lifecycleScope
import com.ttech.navi.NaviContainer
import com.ttech.navi.domain.Phrases
import com.ttech.navi.nav.NavService
import com.ttech.navi.nav.NavState
import com.ttech.navi.nav.NavView
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * 車の画面での案内中の画面。地図は[CarMapRenderer]がSurfaceに直接描き、次の曲がり角・その次・
 * 残りの距離時間は[NavigationTemplate]で出す(考え方は電話の[com.ttech.navi.ui.NavigationScreen]と同じ)。
 */
class CarNavigationScreen(carContext: CarContext, private val container: NaviContainer) : Screen(carContext) {
    private val navManager = carContext.getCarService(NavigationManager::class.java)
    private val mapRenderer = CarMapRenderer(container.tiles, lifecycleScope)
    private var current: NavView? = NavState.view.value

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(mapRenderer)
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            // 車のホスト側の「ナビを終了」操作(こちらのActionStripのボタンとは別口)からも、同じ止め方で止める
            override fun onStopNavigation() {
                NavService.stop(carContext)
            }
        })
        navManager.navigationStarted()

        lifecycleScope.launch {
            NavState.view.collect { view ->
                current = view
                if (view == null) {
                    runCatching { navManager.navigationEnded() }
                    screenManager.pop()
                    return@collect
                }
                val settings = container.settings.current()
                mapRenderer.render(view, settings.headingUp, settings.mapDark)
                updateTrip(view)
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val view = current
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (view?.arrival != null) "閉じる" else "終了")
                    .setOnClickListener { NavService.stop(carContext) }
                    .build(),
            )
            .build()
        val builder = NavigationTemplate.Builder().setActionStrip(actionStrip)

        if (view == null) {
            return builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build()).build()
        }

        builder.setNavigationInfo(navigationInfo(view))
        if (view.etaMs != null) builder.setDestinationTravelEstimate(travelEstimate(view))
        return builder.build()
    }

    private fun navigationInfo(view: NavView): NavigationTemplate.NavigationInfo {
        val arrival = view.arrival
        if (arrival != null) {
            return MessageInfo.Builder("目的地に到着しました")
                .setText("走行距離 ${Phrases.distanceExact(arrival.distanceM)}・所要時間 ${Phrases.duration(arrival.durationMs / 1000.0)}")
                .build()
        }
        if (view.gpsWaiting) return RoutingInfo.Builder().setLoading(true).build()
        if (view.rerouting) return MessageInfo.Builder("ルートを再検索しています…").build()
        if (view.offRoute) return MessageInfo.Builder("ルートから外れています").build()

        val next = view.next
        val distToNext = view.distToNextM
        if (next == null || distToNext == null) return RoutingInfo.Builder().setLoading(true).build()

        val currentStep = Step.Builder(Phrases.maneuverShort(next))
            .setManeuver(CarManeuver.Builder(carManeuverType(next)).build())
            .apply { if (next.roadName.isNotEmpty()) setRoad(next.roadName) }
            .build()
        val routingInfo = RoutingInfo.Builder().setCurrentStep(currentStep, carDistance(distToNext))

        // その次の曲がり角(先読み)。近い距離のときだけ添える(電話の音声案内と同じ考え方)
        val after = view.afterNext
        val gap = view.gapM
        if (after != null && gap != null && gap <= 2000.0) {
            routingInfo.setNextStep(Step.Builder(Phrases.maneuverShort(after)).setManeuver(CarManeuver.Builder(carManeuverType(after)).build()).build())
        }
        return routingInfo.build()
    }

    private fun travelEstimate(view: NavView): TravelEstimate {
        val eta = requireNotNull(view.etaMs)
        return TravelEstimate.Builder(carDistance(view.remainingM), Instant.ofEpochMilli(eta).atZone(ZoneId.systemDefault()))
            .setRemainingTimeSeconds(view.remainingS.toLong())
            .build()
    }

    /** 他アプリに切り替えたときのミニ案内バー・通知用。地図画面そのものは上の[onGetTemplate]が出す */
    private fun updateTrip(view: NavView) {
        if (view.etaMs == null) return
        val destination = Destination.Builder().setName(view.destination.name).build()
        val trip = Trip.Builder()
            .addDestination(destination, travelEstimate(view))
            .setCurrentRoad(view.next?.roadName.orEmpty())
            .build()
        runCatching { navManager.updateTrip(trip) }
    }
}
