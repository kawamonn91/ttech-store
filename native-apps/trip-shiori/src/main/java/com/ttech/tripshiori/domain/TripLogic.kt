package com.ttech.tripshiori.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 1つのしおりに入れられる日数・件数の上限(端末を重くしない・不正なファイルで固まらないため) */
object Limits {
    const val MAX_DAYS = 60
    const val MAX_ITEMS = 1500
    const val MAX_PACKING = 500
    const val MAX_LODGINGS = 60
    const val MAX_CONTACTS = 60
    const val MAX_TRAVELERS = 30
    const val MAX_TRIPS = 200
    const val MAX_TEXT = 2000
    const val MAX_SHORT = 100
    const val MAX_COST = 100_000_000
}

/** ISO の日付。読めなければ null */
fun parseDateOrNull(text: String): LocalDate? =
    try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }

private val WEEKDAYS = mapOf(
    DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水", DayOfWeek.THURSDAY to "木",
    DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土", DayOfWeek.SUNDAY to "日",
)

fun LocalDate.weekdayJa(): String = WEEKDAYS.getValue(dayOfWeek)

/** "10/10(土)" */
fun LocalDate.shortLabel(): String = "$monthValue/$dayOfMonth(${weekdayJa()})"

/** "2026/10/10(土)" */
fun LocalDate.fullLabel(): String = "$year/$monthValue/$dayOfMonth(${weekdayJa()})"

fun Trip.start(): LocalDate = parseDateOrNull(startDate) ?: LocalDate.of(2000, 1, 1)

fun Trip.end(): LocalDate = (parseDateOrNull(endDate) ?: start()).let { if (it.isBefore(start())) start() else it }

/** 旅行の日数(1〜[Limits.MAX_DAYS]) */
fun Trip.dayCount(): Int = (ChronoUnit.DAYS.between(start(), end()) + 1).toInt().coerceIn(1, Limits.MAX_DAYS)

fun Trip.dateOf(day: Int): LocalDate = start().plusDays(day.toLong())

/** "2泊3日"。1日だけなら「日帰り」 */
fun Trip.durationLabel(): String = dayCount().let { if (it == 1) "日帰り" else "${it - 1}泊${it}日" }

/** "2026/10/10(土)〜10/12(月)"。1日だけなら1日ぶんだけ */
fun Trip.periodLabel(): String =
    if (dayCount() == 1) start().fullLabel() else "${start().fullLabel()}〜${end().shortLabel()}"

/** "1日目 10/10(土)" */
fun Trip.dayLabel(day: Int): String = "${day + 1}日目 ${dateOf(day).shortLabel()}"

/** その日の予定。時刻のあるものを時刻順に、時刻が未定のものはその後ろに、追加した順で並べる */
fun Trip.itemsOn(day: Int): List<ScheduleItem> =
    items.filter { it.day == day }
        .withIndex()
        .sortedWith(compareBy({ it.value.time.isEmpty() }, { it.value.time }, { it.index }))
        .map { it.value }

/** 旅行の日程を変える。日数が減って範囲外になった予定は、消さずに最終日へ寄せる */
fun Trip.withDates(newStart: LocalDate, newEnd: LocalDate): Trip {
    val end = if (newEnd.isBefore(newStart)) newStart else newEnd
    val days = (ChronoUnit.DAYS.between(newStart, end) + 1).toInt().coerceIn(1, Limits.MAX_DAYS)
    return copy(
        startDate = newStart.toString(),
        endDate = newStart.plusDays((days - 1).toLong()).toString(),
        items = items.map { if (it.day >= days) it.copy(day = days - 1) else it },
    )
}

// ---------------------------------------------------------------- 旅の状態(今日との関係)

sealed interface TripPhase {
    /** これから。[daysUntil] は出発まであと何日か */
    data class Upcoming(val daysUntil: Int) : TripPhase

    /** 旅行中。[day] は 0 始まりの何日目か */
    data class Ongoing(val day: Int) : TripPhase

    data object Finished : TripPhase
}

fun Trip.phase(today: LocalDate): TripPhase = when {
    today.isBefore(start()) -> TripPhase.Upcoming(ChronoUnit.DAYS.between(today, start()).toInt())
    today.isAfter(end()) -> TripPhase.Finished
    else -> TripPhase.Ongoing(ChronoUnit.DAYS.between(start(), today).toInt().coerceIn(0, dayCount() - 1))
}

fun TripPhase.label(): String = when (this) {
    is TripPhase.Upcoming -> "あと${daysUntil}日"
    is TripPhase.Ongoing -> "旅行中 ${day + 1}日目"
    TripPhase.Finished -> "終了"
}

// ---------------------------------------------------------------- 費用

fun Trip.totalCost(): Int = items.sumOf { it.cost.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** 種類ごとの費用(0円のものは含めない) */
fun Trip.costByKind(): Map<ItemKind, Int> =
    items.filter { it.cost > 0 }.groupBy { it.kind }.mapValues { (_, list) -> list.sumOf { it.cost } }

/** 日ごとの費用(日数ぶん) */
fun Trip.costByDay(): List<Int> = List(dayCount()) { day -> items.filter { it.day == day }.sumOf { it.cost } }

/** 1人あたり(端数は切り上げ)。人数が2人未満なら null */
fun Trip.costPerPerson(): Int? =
    if (travelers.size < 2) null else (totalCost() + travelers.size - 1) / travelers.size

fun yen(amount: Int): String = "¥" + String.format(Locale.US, "%,d", amount)

// ---------------------------------------------------------------- 持ち物

/** (チェック済み, 全体) */
fun Trip.packingProgress(): Pair<Int, Int> = packing.count { it.checked } to packing.size

// ---------------------------------------------------------------- リストの更新(id で置き換え・追加)

fun <T> List<T>.upsertBy(item: T, idOf: (T) -> String): List<T> {
    val id = idOf(item)
    return if (any { idOf(it) == id }) map { if (idOf(it) == id) item else it } else this + item
}

fun Trip.upsertItem(item: ScheduleItem): Trip = copy(items = items.upsertBy(item) { it.id })
fun Trip.removeItem(id: String): Trip = copy(items = items.filterNot { it.id == id })
fun Trip.upsertLodging(lodging: Lodging): Trip = copy(lodgings = lodgings.upsertBy(lodging) { it.id })
fun Trip.removeLodging(id: String): Trip = copy(lodgings = lodgings.filterNot { it.id == id })
fun Trip.upsertContact(contact: Contact): Trip = copy(contacts = contacts.upsertBy(contact) { it.id })
fun Trip.removeContact(id: String): Trip = copy(contacts = contacts.filterNot { it.id == id })
fun Trip.togglePacking(id: String): Trip =
    copy(packing = packing.map { if (it.id == id) it.copy(checked = !it.checked) else it })
fun Trip.removePacking(id: String): Trip = copy(packing = packing.filterNot { it.id == id })

/** 持ち物を追加する。すでにある名前(前後の空白・大文字小文字を無視)は加えない */
fun Trip.addPacking(names: List<String>, newId: () -> String): Trip {
    val existing = packing.map { it.name.trim().lowercase() }.toMutableSet()
    val added = names.map { it.trim() }.filter { it.isNotEmpty() && existing.add(it.lowercase()) }
        .take((Limits.MAX_PACKING - packing.size).coerceAtLeast(0))
        .map { PackingItem(id = newId(), name = it.take(Limits.MAX_SHORT)) }
    return copy(packing = packing + added)
}

fun Trip.resetPacking(): Trip = copy(packing = packing.map { it.copy(checked = false) })

// ---------------------------------------------------------------- 入力の検査・整形

/** "9:5" "0905" のような入力を "09:05" にそろえる。読めなければ null。空は空のまま */
fun normalizeTime(input: String): String? {
    val t = input.trim()
    if (t.isEmpty()) return ""
    // "9:05" "09:5" "0905" "905" を受け付ける(全角のコロンも可)
    val m = Regex("""^(\d{1,2})[:：](\d{1,2})$""").matchEntire(t) ?: Regex("""^(\d{1,2})(\d{2})$""").matchEntire(t) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (h !in 0..23 || min !in 0..59) return null
    return String.format(Locale.ROOT, "%02d:%02d", h, min)
}

/**
 * しおり全体を、保存・読み込みしても安全な形にそろえる。
 * 日付が読めない・終了が開始より前・日数が多すぎる、予定の日が範囲外、文字数や件数が多すぎる、を直す。
 */
fun Trip.sanitized(nowMs: Long = updatedAtMs): Trip {
    val s = parseDateOrNull(startDate) ?: LocalDate.of(2000, 1, 1)
    val e = (parseDateOrNull(endDate) ?: s).let { if (it.isBefore(s)) s else it }
    val days = (ChronoUnit.DAYS.between(s, e) + 1).toInt().coerceIn(1, Limits.MAX_DAYS)
    fun String.clip(max: Int = Limits.MAX_SHORT) = trim().take(max)
    fun String.clipText() = trim().take(Limits.MAX_TEXT)
    return Trip(
        id = id.clip(80),
        title = title.clip().ifEmpty { "無題のしおり" },
        destination = destination.clip(),
        startDate = s.toString(),
        endDate = s.plusDays((days - 1).toLong()).toString(),
        travelers = travelers.map { it.clip(40) }.filter { it.isNotEmpty() }.take(Limits.MAX_TRAVELERS),
        notes = notes.clipText(),
        items = items.take(Limits.MAX_ITEMS).map {
            it.copy(
                id = it.id.clip(80),
                day = it.day.coerceIn(0, days - 1),
                time = normalizeTime(it.time) ?: "",
                title = it.title.clip().ifEmpty { "(無題)" },
                place = it.place.clip(),
                memo = it.memo.clipText(),
                cost = it.cost.coerceIn(0, Limits.MAX_COST),
            )
        },
        packing = packing.take(Limits.MAX_PACKING).map { it.copy(id = it.id.clip(80), name = it.name.clip().ifEmpty { "(無題)" }) },
        lodgings = lodgings.take(Limits.MAX_LODGINGS).map {
            it.copy(
                id = it.id.clip(80), name = it.name.clip().ifEmpty { "(無題)" }, address = it.address.clip(200),
                phone = it.phone.clip(40), reservation = it.reservation.clip(), times = it.times.clip(), note = it.note.clipText(),
            )
        },
        contacts = contacts.take(Limits.MAX_CONTACTS).map {
            it.copy(id = it.id.clip(80), name = it.name.clip().ifEmpty { "(無題)" }, phone = it.phone.clip(40), note = it.note.clip(200))
        },
        updatedAtMs = nowMs,
    )
}
