package com.ttech.driverecord.domain

/**
 * Android Auto の接続が「切れている→つながった」に変わった瞬間だけを検知する。
 * CarConnection の通知は、同じ値のまま何度も呼ばれることがあるため、[DriveController] とは別に、
 * 「アプリを自動で開く」機能のためだけに、単純な立ち上がり検知を用意する
 * (記録の開始・終了の判断は [DriveController] の責務のまま変えない)。
 */
class ConnectionEdge {
    private var connected = false

    /** いまの接続状態を渡す。「切れている→つながった」の瞬間だけ true を返す */
    fun onConnected(now: Boolean): Boolean {
        val rising = now && !connected
        connected = now
        return rising
    }
}
