package com.ttech.tripshiori.domain

import java.time.LocalDate

/** 持ち物のおすすめセット */
data class PackingPreset(val title: String, val items: List<String>)

val PACKING_PRESETS: List<PackingPreset> = listOf(
    PackingPreset(
        "基本",
        listOf("財布", "スマートフォン", "充電器・ケーブル", "モバイルバッテリー", "身分証明書", "健康保険証", "常備薬", "ハンカチ・ティッシュ", "着替え", "洗面用具", "折りたたみ傘", "エコバッグ"),
    ),
    PackingPreset("電車・新幹線", listOf("切符・予約の画面", "ICカード", "イヤホン", "本・タブレット", "飲み物")),
    PackingPreset("宿泊", listOf("タオル", "歯ブラシ", "スキンケア用品", "パジャマ・部屋着", "ドライヤー(必要なら)", "予備の下着・靴下")),
    PackingPreset("温泉", listOf("フェイスタオル", "入浴セット", "着替えの下着", "小銭(コインロッカー用)")),
    PackingPreset("海外", listOf("パスポート", "現金・クレジットカード", "海外旅行保険の書類", "変換プラグ", "eSIM・Wi-Fiルーター", "航空券・eチケット", "入国書類")),
    PackingPreset("雨・寒さ対策", listOf("レインコート", "上着・羽織るもの", "カイロ", "手袋・マフラー", "防水の靴・靴カバー")),
    PackingPreset("子ども連れ", listOf("母子手帳", "おむつ・おしり拭き", "おやつ・飲み物", "おもちゃ・絵本", "着替えの予備", "ベビーカー")),
    PackingPreset("ドライブ", listOf("運転免許証", "ETCカード", "車載の充電器", "地図・ナビの準備", "サングラス", "ゴミ袋")),
)

/** 「サンプルを見る」で作る、京都2泊3日のしおり。使い方が分かるように、いろいろな項目を入れてある */
fun sampleTrip(today: LocalDate, newId: () -> String, nowMs: Long): Trip {
    // 次の土曜日から2泊3日
    val start = today.plusDays(((6 - today.dayOfWeek.value + 7) % 7).toLong().let { if (it == 0L) 7L else it })
    fun item(day: Int, time: String, title: String, kind: ItemKind, place: String = "", memo: String = "", cost: Int = 0) =
        ScheduleItem(newId(), day, time, title, place, memo, kind, cost)
    return Trip(
        id = newId(),
        title = "京都 2泊3日のんびり旅",
        destination = "京都",
        startDate = start.toString(),
        endDate = start.plusDays(2).toString(),
        travelers = listOf("わたし", "友だち"),
        notes = "雨の日は美術館と錦市場へ。紅葉の時期は混むので、早めの行動がおすすめ。",
        items = listOf(
            item(0, "08:00", "東京駅を出発", ItemKind.MOVE, "東京駅", "のぞみ 11号 / 2号車", 14000),
            item(0, "10:30", "京都駅に到着・荷物を預ける", ItemKind.MOVE, "京都駅", "コインロッカーは中央口が空いている"),
            item(0, "12:00", "湯豆腐のランチ", ItemKind.MEAL, "南禅寺の近く", "予約済み(2名)", 3500),
            item(0, "14:00", "南禅寺・水路閣を散歩", ItemKind.SIGHT, "南禅寺", "", 600),
            item(0, "17:30", "ホテルにチェックイン", ItemKind.STAY, "京都河原町のホテル"),
            item(1, "09:00", "清水寺", ItemKind.SIGHT, "清水寺", "早朝のほうが空いている", 400),
            item(1, "12:30", "祇園でお昼", ItemKind.MEAL, "祇園", "", 2500),
            item(1, "15:00", "伏見稲荷大社", ItemKind.SIGHT, "伏見稲荷大社", "千本鳥居は歩きやすい靴で"),
            item(1, "19:00", "先斗町で夕食", ItemKind.MEAL, "先斗町", "", 6000),
            item(2, "10:00", "嵐山・竹林の小径", ItemKind.SIGHT, "嵐山", "", 0),
            item(2, "13:00", "お土産を買う", ItemKind.OTHER, "京都駅", "八ッ橋・お茶・漬物", 5000),
            item(2, "16:30", "京都駅を出発", ItemKind.MOVE, "京都駅", "のぞみ 26号", 14000),
        ),
        packing = listOf("財布", "スマートフォン", "充電器・ケーブル", "歩きやすい靴", "折りたたみ傘", "エコバッグ")
            .mapIndexed { i, name -> PackingItem(newId(), name, checked = i < 2) },
        lodgings = listOf(
            Lodging(newId(), "京都河原町ホテル", "京都市中京区(河原町通り沿い)", "075-000-0000", "K-12345", "チェックイン 15:00〜 / チェックアウト 11:00まで", "荷物は朝から預かってもらえる"),
        ),
        contacts = listOf(Contact(newId(), "ホテルのフロント", "075-000-0000", "遅くなるときは連絡する")),
        updatedAtMs = nowMs,
    )
}
