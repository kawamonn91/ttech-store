package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasesTest {
    private fun jst(h: Int, m: Int): Long = LocalDateTime.of(2026, 9, 28, h, m, 20).atZone(Phrases.JST).toInstant().toEpochMilli()
    private fun turn(mod: String) = Maneuver(ManeuverKind.Turn, mod, 0.0, "", LatLon(0.0, 0.0))

    @Test
    fun `距離は近いほど細かく、遠いほど大きな単位で言う`() {
        assertEquals("50メートル", Phrases.distance(52.0))
        assertEquals("10メートル", Phrases.distance(3.0))
        assertEquals("300メートル", Phrases.distance(320.0))
        assertEquals("250メートル", Phrases.distance(240.0))
        assertEquals("1キロ", Phrases.distance(980.0))
        assertEquals("1キロ", Phrases.distance(1200.0))
        assertEquals("1.5キロ", Phrases.distance(1600.0))
        assertEquals("2キロ", Phrases.distance(1800.0))
        assertEquals("12キロ", Phrases.distance(12_300.0))
    }

    @Test
    fun `ルート全体の距離は小数1桁のキロで言う`() {
        assertEquals("154.8キロ", Phrases.distanceExact(154_841.4))
        assertEquals("6.1キロ", Phrases.distanceExact(6069.0))
        assertEquals("800メートル", Phrases.distanceExact(801.0))
    }

    @Test
    fun `所要時間の言い方`() {
        assertEquals("1分未満", Phrases.duration(20.0))
        assertEquals("8分", Phrases.duration(458.8))
        assertEquals("2時間", Phrases.duration(7200.0))
        assertEquals("2時間9分", Phrases.duration(7759.6))
    }

    @Test
    fun `時刻は5分刻み、天気の時刻は10分刻みで言う`() {
        assertEquals("8時5分", Phrases.clock(jst(8, 3)))
        assertEquals("11時ちょうど", Phrases.clock(jst(10, 58)))
        assertEquals("9時半ごろ", Phrases.clockAround(jst(9, 28)))
        assertEquals("10時ごろ", Phrases.clockAround(jst(9, 57)))
        assertEquals("9時40分ごろ", Phrases.clockAround(jst(9, 41)))
    }

    @Test
    fun `曲がる方向の言い方`() {
        assertEquals("右折", Phrases.maneuver(turn("right")))
        assertEquals("左折", Phrases.maneuver(turn("left")))
        assertEquals("斜め右方向", Phrases.maneuver(turn("slight right")))
        assertEquals("急な左折", Phrases.maneuver(turn("sharp left")))
        assertEquals("Uターン", Phrases.maneuver(turn("uturn")))
        assertEquals("分岐、左方向", Phrases.maneuver(Maneuver(ManeuverKind.Fork, "slight left", 0.0, "", LatLon(0.0, 0.0))))
        assertEquals("出口、右方向", Phrases.maneuver(Maneuver(ManeuverKind.OffRamp, "slight right", 0.0, "", LatLon(0.0, 0.0))))
        assertEquals("目的地", Phrases.maneuver(Maneuver(ManeuverKind.Arrive, null, 0.0, "", LatLon(0.0, 0.0))))
    }

    @Test
    fun `案内の文に、もう一つ先の曲がる方向を添える`() {
        // ご要望の例: 「200メートル先、右折です。その後、300メートル先、左折です。」
        assertEquals("200メートル先、右折です。", Phrases.announce(200.0, turn("right"), near = false))
        assertEquals("その後、300メートル先、左折です。", Phrases.then(300.0, turn("left")))
        assertEquals("その後すぐ、左折です。", Phrases.then(60.0, turn("left")))
        assertEquals("まもなく、右折です。", Phrases.announce(90.0, turn("right"), near = true))
    }

    @Test
    fun `到着の案内は、走った距離とかかった時間を言う`() {
        assertEquals(
            "目的地に到着しました。走行距離は154.8キロ、所要時間は2時間10分でした。おつかれさまでした。",
            Phrases.arrival(154_841.4, 7_800_000),
        )
    }

    @Test
    fun `県や市に入ったときの案内`() {
        assertEquals("宮城県、白石市に入りました。", Phrases.enteredRegion(true, "宮城県", "白石市"))
        assertEquals("宮城県に入りました。", Phrases.enteredRegion(true, "宮城県", null))
        assertEquals("福島市に入りました。", Phrases.enteredRegion(false, "福島県", "福島市"))
    }
}
