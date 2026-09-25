package com.ttech.tripshiori.domain

/**
 * 共有用の文書。テキスト共有とPDFが同じ内容になるよう、しおりをいったんこの「ブロック」の並びにしてから、
 * それぞれの形(文字列・PDFの描画)にする。
 */
sealed interface Block {
    /** 表紙のタイトル。[subtitle] は行き先・日程など(複数行可) */
    data class Title(val text: String, val subtitle: List<String>) : Block

    /** 見出し(「1日目 10/10(土)」「持ち物」など) */
    data class Heading(val text: String) : Block

    /** 予定・宿泊先などの1項目。[lead] は時刻や記号、[details] は補足(場所・メモ・費用) */
    data class Row(val lead: String, val text: String, val details: List<String> = emptyList()) : Block

    /** 持ち物のチェック項目 */
    data class Check(val text: String, val checked: Boolean) : Block

    /** 説明の文章 */
    data class Line(val text: String) : Block
}

/** しおりを共有用の文書にする。空の区分(宿泊先・連絡先・持ち物・メモ)は出さない */
fun buildDocument(trip: Trip): List<Block> {
    val blocks = mutableListOf<Block>()
    val subtitle = buildList {
        val head = listOf(trip.destination, "${trip.periodLabel()}(${trip.durationLabel()})").filter { it.isNotEmpty() }
        add(head.joinToString("  "))
        if (trip.travelers.isNotEmpty()) add("メンバー: " + trip.travelers.joinToString("・"))
    }
    blocks += Block.Title(trip.title, subtitle)

    for (day in 0 until trip.dayCount()) {
        blocks += Block.Heading(trip.dayLabel(day))
        val items = trip.itemsOn(day)
        if (items.isEmpty()) blocks += Block.Line("予定はまだありません")
        for (it in items) {
            val details = buildList {
                if (it.place.isNotEmpty()) add("場所: ${it.place}")
                if (it.memo.isNotEmpty()) add(it.memo)
                if (it.cost > 0) add("費用: ${yen(it.cost)}")
            }
            blocks += Block.Row(lead = it.time.ifEmpty { "・" }, text = "${it.title}(${it.kind.label})", details = details)
        }
    }

    if (trip.lodgings.isNotEmpty()) {
        blocks += Block.Heading("宿泊先")
        for (l in trip.lodgings) {
            val details = buildList {
                if (l.address.isNotEmpty()) add("住所: ${l.address}")
                if (l.phone.isNotEmpty()) add("電話: ${l.phone}")
                if (l.times.isNotEmpty()) add(l.times)
                if (l.reservation.isNotEmpty()) add("予約番号: ${l.reservation}")
                if (l.note.isNotEmpty()) add(l.note)
            }
            blocks += Block.Row(lead = "・", text = l.name, details = details)
        }
    }

    if (trip.contacts.isNotEmpty()) {
        blocks += Block.Heading("連絡先")
        for (c in trip.contacts) {
            val details = buildList {
                if (c.phone.isNotEmpty()) add("電話: ${c.phone}")
                if (c.note.isNotEmpty()) add(c.note)
            }
            blocks += Block.Row(lead = "・", text = c.name, details = details)
        }
    }

    if (trip.packing.isNotEmpty()) {
        blocks += Block.Heading("持ち物")
        for (p in trip.packing) blocks += Block.Check(p.name, p.checked)
    }

    if (trip.notes.isNotEmpty()) {
        blocks += Block.Heading("メモ")
        blocks += Block.Line(trip.notes)
    }

    val total = trip.totalCost()
    if (total > 0) {
        blocks += Block.Heading("費用の目安")
        val per = trip.costPerPerson()
        blocks += Block.Line(
            "合計 ${yen(total)}" + if (per != null) "(1人あたり ${yen(per)}・${trip.travelers.size}人)" else "",
        )
    }
    return blocks
}

/** LINE・メールなどに貼り付けられるテキストにする */
fun List<Block>.toPlainText(): String {
    val out = StringBuilder()
    for (b in this) {
        when (b) {
            is Block.Title -> {
                out.append("【旅のしおり】").append(b.text).append('\n')
                b.subtitle.forEach { out.append(it).append('\n') }
            }
            is Block.Heading -> out.append('\n').append("■").append(b.text).append('\n')
            is Block.Row -> {
                out.append(b.lead).append(' ').append(b.text).append('\n')
                b.details.forEach { out.append("　").append(it).append('\n') }
            }
            is Block.Check -> out.append(if (b.checked) "☑ " else "□ ").append(b.text).append('\n')
            is Block.Line -> out.append(b.text).append('\n')
        }
    }
    return out.toString().trimEnd() + "\n"
}

fun Trip.toShareText(): String = buildDocument(this).toPlainText()
