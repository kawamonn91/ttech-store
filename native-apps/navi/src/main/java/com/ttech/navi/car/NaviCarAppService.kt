package com.ttech.navi.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto(車の画面)側の入り口。Google Playを通さない配布なので、車のホストの署名を
 * あらかじめ知りようがなく、[HostValidator.ALLOW_ALL_HOSTS_VALIDATOR] を使う(支払い・通信など、
 * 位置情報より踏み込んだ機能は扱わないアプリなので、これで十分と判断)。
 */
class NaviCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NaviCarSession()
}
