package com.ttech.salonbooking.domain

import kotlinx.serialization.Serializable

@Serializable
data class Booking(
    val id: String,
    val customerName: String,
    val service: String,
    /** ISO8601 (YYYY-MM-DD) */
    val date: String,
    /** "HH:mm" */
    val time: String,
)

/** 入力値から予約を作る。お客様名が空なら作らない(null)。名前・メニューは前後の空白を除く。 */
fun buildBooking(id: String, customerName: String, service: String, date: String, time: String): Booking? {
    if (customerName.isBlank()) return null
    return Booking(id, customerName.trim(), service.trim(), date, time)
}

/**
 * 今後の予約(今日以降の日付)を日時の早い順に並べる。今日の予約は時刻が過ぎていても表示する
 * (Webアプリ版と同じく「今日の0時以降」で判定)。
 */
fun List<Booking>.upcoming(today: String): List<Booking> =
    filter { "${it.date}T${it.time}" >= "${today}T00:00" }.sortedBy { "${it.date}${it.time}" }

/** 一覧の補足行(「2026-09-24 10:00 ・ カット」、メニュー未入力なら日時のみ)。 */
fun Booking.whenLabel(): String = "$date $time" + if (service.isNotEmpty()) " ・ $service" else ""
