package com.ttech.track.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun `緯度1度は約111km`() {
        val d = GeoMath.distanceMeters(35.0, 139.0, 36.0, 139.0)
        assertEquals(111_195.0, d, 200.0)
    }

    @Test
    fun `経度1度の長さは緯度で変わる(赤道と北緯60度)`() {
        assertEquals(111_195.0, GeoMath.distanceMeters(0.0, 10.0, 0.0, 11.0), 200.0)
        assertEquals(55_600.0, GeoMath.distanceMeters(60.0, 10.0, 60.0, 11.0), 300.0)
    }

    @Test
    fun `同じ点の距離は0で、逆向きでも同じ`() {
        assertEquals(0.0, GeoMath.distanceMeters(35.6, 139.7, 35.6, 139.7), 1e-9)
        val a = LatLon(35.68, 139.76)
        val b = LatLon(35.66, 139.74)
        assertEquals(GeoMath.distanceMeters(a, b), GeoMath.distanceMeters(b, a), 1e-9)
    }

    @Test
    fun `数メートルの短い距離も精度よく測れる`() {
        // 緯度方向に約10m(0.0000899度)
        val d = GeoMath.distanceMeters(35.0, 139.0, 35.0 + 10.0 / 111_195.0, 139.0)
        assertEquals(10.0, d, 0.01)
    }

    @Test
    fun `方位は北0・東90・南180・西270`() {
        val o = LatLon(35.0, 139.0)
        assertEquals(0.0, GeoMath.bearingDegrees(o, LatLon(35.1, 139.0)), 0.1)
        assertEquals(90.0, GeoMath.bearingDegrees(o, LatLon(35.0, 139.1)), 0.2)
        assertEquals(180.0, GeoMath.bearingDegrees(o, LatLon(34.9, 139.0)), 0.1)
        assertEquals(270.0, GeoMath.bearingDegrees(o, LatLon(35.0, 138.9)), 0.2)
    }

    @Test
    fun `方位の差は0度をまたいでも短い方で測る`() {
        assertEquals(20.0, GeoMath.angleDiffDegrees(350.0, 10.0), 1e-9)
        assertEquals(-20.0, GeoMath.angleDiffDegrees(10.0, 350.0), 1e-9)
        assertEquals(180.0, GeoMath.absAngleDiffDegrees(0.0, 180.0), 1e-9)
        assertEquals(90.0, GeoMath.absAngleDiffDegrees(45.0, 315.0), 1e-9)
    }

    @Test
    fun `範囲は全点を含み、空なら null`() {
        assertNull(GeoBounds.of(emptyList()))
        val b = GeoBounds.of(listOf(LatLon(35.0, 139.0), LatLon(36.0, 138.0), LatLon(35.5, 140.0)))!!
        assertEquals(35.0, b.minLat, 0.0)
        assertEquals(36.0, b.maxLat, 0.0)
        assertEquals(138.0, b.minLon, 0.0)
        assertEquals(140.0, b.maxLon, 0.0)
        assertEquals(35.5, b.center.lat, 1e-9)
    }
}

class TrackFilterTest {
    private val filter = TrackFilter(maxAccuracyM = 30.0, maxSpeedMps = 80.0)
    private fun p(t: Long, lat: Double = 35.0, lon: Double = 139.0, acc: Double? = 5.0) = TrackPoint(t, lat, lon, hAcc = acc)

    @Test
    fun `最初の点は精度が良ければ受け入れる`() {
        assertEquals(FixDecision.Accept, filter.decide(null, p(1000)))
    }

    @Test
    fun `精度が悪い測位は除く`() {
        assertEquals(RejectKind.ACCURACY, (filter.decide(null, p(1000, acc = 50.0)) as FixDecision.Reject).kind)
        assertEquals(FixDecision.Accept, filter.decide(null, p(1000, acc = 30.0)))
    }

    @Test
    fun `精度が不明(null)の測位は除かない`() {
        assertEquals(FixDecision.Accept, filter.decide(null, p(1000, acc = null)))
    }

    @Test
    fun `時刻が戻る・同時刻の測位は除く`() {
        val prev = p(2000)
        assertTrue(filter.decide(prev, p(2000)) is FixDecision.Reject)
        assertTrue(filter.decide(prev, p(1000)) is FixDecision.Reject)
    }

    @Test
    fun `1秒で1km動いたような位置の飛びは除く`() {
        val prev = p(1000)
        val jumped = p(2000, lat = 35.01) // 約1.1km
        val decision = filter.decide(prev, jumped)
        assertTrue(decision is FixDecision.Reject)
        assertEquals("位置が飛んだ", (decision as FixDecision.Reject).reason)
        assertEquals(RejectKind.JUMP, decision.kind)
    }

    @Test
    fun `普通の走行(時速100km=約28m秒)は受け入れる`() {
        val prev = p(1000)
        val next = p(2000, lat = 35.0 + 28.0 / 111_195.0)
        assertEquals(FixDecision.Accept, filter.decide(prev, next))
    }

    @Test
    fun `長く途切れたあとの移動(トンネルを抜けた)は受け入れる`() {
        val prev = p(1000)
        val next = p(61_000, lat = 35.0 + 1500.0 / 111_195.0) // 60秒で1.5km = 25m/秒
        assertEquals(FixDecision.Accept, filter.decide(prev, next))
    }

    @Test
    fun `座標が範囲外なら除く`() {
        assertTrue(filter.decide(null, p(1000, lat = 91.0)) is FixDecision.Reject)
        assertTrue(filter.decide(null, p(1000, lon = 181.0)) is FixDecision.Reject)
    }
}

class TrackCodecTest {
    @Test
    fun `書いて読むと元に戻る(全項目あり)`() {
        val p = TrackPoint(1_790_000_000_123, 35.6812345, 139.7671234, 12.34, 13.889, 271.5, 4.5, 6.0, 0.35, 17)
        val back = TrackCodec.decode(TrackCodec.encode(p))!!
        assertEquals(p.timeMs, back.timeMs)
        assertEquals(p.lat, back.lat, 1e-7)
        assertEquals(p.lon, back.lon, 1e-7)
        assertEquals(12.34, back.altitude!!, 0.005)
        assertEquals(13.889, back.speed!!, 0.0005)
        assertEquals(271.5, back.bearing!!, 0.05)
        assertEquals(4.5, back.hAcc!!, 0.05)
        assertEquals(6.0, back.vAcc!!, 0.05)
        assertEquals(0.35, back.sAcc!!, 0.005)
        assertEquals(17, back.satellites)
    }

    @Test
    fun `値が無い項目は空のまま読み戻せる`() {
        val p = TrackPoint(1000, 35.0, 139.0)
        val line = TrackCodec.encode(p)
        assertEquals("1000,35.0000000,139.0000000,,,,,,,", line)
        val back = TrackCodec.decode(line)!!
        assertNull(back.altitude)
        assertNull(back.speed)
        assertNull(back.satellites)
    }

    @Test
    fun `小数点はロケールに関係なく「ピリオド」`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertTrue(TrackCodec.encode(TrackPoint(1, 35.5, 139.5, 1.5)).contains("35.5000000"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `壊れた行・空行は読み飛ばす`() {
        val lines = sequenceOf(
            TrackCodec.encode(TrackPoint(1000, 35.0, 139.0)),
            "",
            "abc,def",
            "2000,not-a-number,139.0,,,,,,,",
            "3000,95.0,139.0,,,,,,,", // 緯度が範囲外
            TrackCodec.encode(TrackPoint(4000, 35.1, 139.1)),
            "5000,35.2,139.2", // 途中で切れた行(列が足りない)
        )
        val points = TrackCodec.decodeAll(lines)
        assertEquals(listOf(1000L, 4000L), points.map { it.timeMs })
    }
}

class ExportTest {
    private val points = listOf(
        TrackPoint(1_790_000_000_000, 35.68, 139.76, 10.0, 8.0, 90.0, 4.0, null, null, 12),
        TrackPoint(1_790_000_001_000, 35.6801, 139.7601),
    )

    @Test
    fun `GPXは点の数だけ trkpt を持ち、時刻はUTCのISO形式`() {
        val gpx = Export.gpx("朝のドライブ", points, "T-tech")
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<time>2026-09-"))
        assertTrue(gpx.contains("lat=\"35.6800000\" lon=\"139.7600000\""))
        assertTrue(gpx.contains("<ele>10.00</ele>"))
        assertTrue(gpx.contains("<speed>8.000</speed>"))
        assertTrue(gpx.contains("<sat>12</sat>"))
        assertTrue(gpx.contains("creator=\"T-tech\""))
    }

    @Test
    fun `GPXの名前に含まれる記号は無害化される`() {
        val gpx = Export.gpx("A&B <\"x\">", points)
        assertTrue(gpx.contains("A&amp;B &lt;&quot;x&quot;&gt;"))
        assertFalse(gpx.contains("<\"x\">"))
    }

    @Test
    fun `CSVはヘッダーと点の行を持ち、速度はkm毎時も入る`() {
        val lines = Export.csv(points).trim().lines()
        assertEquals(Export.CSV_HEADER, lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains(",8.000,28.80,"))
    }

    @Test
    fun `点が無くても壊れない`() {
        assertTrue(Export.gpx("空", emptyList()).contains("</gpx>"))
        assertEquals(Export.CSV_HEADER, Export.csv(emptyList()).trim())
    }
}

class RouteSegmentsTest {
    @Test
    fun `途切れなければ1本の線`() {
        val pts = (0..5).map { TrackPoint(it * 1000L, 35.0 + it * 0.0001, 139.0) }
        val r = RouteSegments.from(pts)
        assertEquals(1, r.solid.size)
        assertEquals(6, r.solid[0].size)
        assertTrue(r.gaps.isEmpty())
    }

    @Test
    fun `10秒より空いたところで線を切り、両端を点線用に残す`() {
        val pts = listOf(
            TrackPoint(0, 35.0, 139.0), TrackPoint(1000, 35.0001, 139.0),
            TrackPoint(30_000, 35.003, 139.0), TrackPoint(31_000, 35.0031, 139.0),
        )
        val r = RouteSegments.from(pts)
        assertEquals(2, r.solid.size)
        assertEquals(1, r.gaps.size)
        assertEquals(LatLon(35.0001, 139.0), r.gaps[0].first)
        assertEquals(LatLon(35.003, 139.0), r.gaps[0].second)
        assertEquals(4, r.all.size)
    }

    @Test
    fun `空のときは空`() {
        assertTrue(RouteSegments.from(emptyList()).isEmpty)
    }
}

class GeometryTest {
    @Test
    fun `一直線上の点は端の2点だけに間引かれる`() {
        val line = (0..100).map { LatLon(35.0 + it * 0.0001, 139.0) }
        assertEquals(2, Geometry.simplify(line, 1.0).size)
    }

    @Test
    fun `曲がり角は残る`() {
        val pts = (0..10).map { LatLon(35.0 + it * 0.0001, 139.0) } + (1..10).map { LatLon(35.001, 139.0 + it * 0.0001) }
        val simple = Geometry.simplify(pts, 2.0)
        assertEquals(3, simple.size)
        assertEquals(LatLon(35.001, 139.0), simple[1])
    }

    @Test
    fun `許容誤差より小さいふらつきは消える`() {
        val wobble = (0..50).map { LatLon(35.0 + it * 0.0001, 139.0 + if (it % 2 == 0) 0.0 else 0.00000045) } // 約4cm
        assertEquals(2, Geometry.simplify(wobble, 1.0).size)
    }

    @Test
    fun `グラフ用の間引きは先頭・末尾と山を残す`() {
        val n = 2000
        val x = DoubleArray(n) { it.toDouble() }
        val y = DoubleArray(n) { if (it == 1234) 100.0 else (it % 7).toDouble() }
        val idx = Geometry.downsampleIndices(x, y, 200)
        assertTrue(idx.size <= 200)
        assertEquals(0, idx.first())
        assertEquals(n - 1, idx.last())
        assertTrue("最高値の点が消えている", idx.contains(1234))
        assertEquals(idx.sorted(), idx)
    }

    @Test
    fun `点が少なければ間引かない`() {
        val idx = Geometry.downsampleIndices(DoubleArray(10) { it.toDouble() }, DoubleArray(10), 200)
        assertEquals((0 until 10).toList(), idx)
    }
}

class FormatTest {
    @Test
    fun `距離は1km未満はm、以上はkm`() {
        assertEquals("850 m", Format.distance(850.0))
        assertEquals("1.50 km", Format.distance(1500.0))
        assertEquals("123.5 km", Format.distance(123_456.0))
    }

    @Test
    fun `時間の表記`() {
        assertEquals("48秒", Format.duration(48_000))
        assertEquals("5分12秒", Format.duration(312_000))
        assertEquals("1時間23分", Format.duration(4_980_000))
        assertEquals("1:23:20", Format.clock(5_000_000))
        assertEquals("0:00:05", Format.clock(5_000))
    }

    @Test
    fun `速度とペース`() {
        assertEquals("36 km/h", Format.speedKmh(10.0))
        assertEquals("5:00 /km", Format.pace(300.0))
        assertEquals("5:33 /km", Format.pace(332.6))
        assertEquals("-", Format.pace(Double.POSITIVE_INFINITY))
        assertEquals("-", Format.pace(0.0))
        assertEquals(300.0, Format.secPerKm(1000.0 / 300.0), 1e-9)
        assertTrue(Format.secPerKm(0.0).isInfinite())
    }

    @Test
    fun `日付は日本時間で表す(UTCの夜は日本の翌日)`() {
        // 2026-09-24T20:00:00Z = 日本時間 9/25 5:00
        val ms = java.time.Instant.parse("2026-09-24T20:00:00Z").toEpochMilli()
        assertEquals("2026-09-25", Format.dayKey(ms))
        assertEquals("5:00", Format.time(ms))
    }
}

class MercatorTest {
    @Test
    fun `世界座標の基準(ズーム0は256px四方)`() {
        assertEquals(128.0, WebMercator.worldX(0.0, 0.0), 1e-9)
        assertEquals(256.0, WebMercator.worldX(180.0, 0.0), 1e-9)
        assertEquals(0.0, WebMercator.worldX(-180.0, 0.0), 1e-9)
        assertEquals(128.0, WebMercator.worldY(0.0, 0.0), 1e-9)
        assertEquals(512.0, WebMercator.worldX(180.0, 1.0), 1e-9)
    }

    @Test
    fun `座標から世界座標へ、戻すと元の座標`() {
        for ((lat, lon) in listOf(35.6812 to 139.7671, -33.86 to 151.21, 64.13 to -21.9, 0.0 to 0.0)) {
            val z = 14.0
            assertEquals(lon, WebMercator.lon(WebMercator.worldX(lon, z), z), 1e-9)
            assertEquals(lat, WebMercator.lat(WebMercator.worldY(lat, z), z), 1e-9)
        }
    }

    @Test
    fun `タイル番号(ズーム1は2x2)`() {
        assertEquals(0, WebMercator.tileX(-100.0, 1))
        assertEquals(0, WebMercator.tileY(40.0, 1))
        assertEquals(1, WebMercator.tileX(100.0, 1))
        assertEquals(1, WebMercator.tileY(-40.0, 1))
    }

    @Test
    fun `タイルのURLはOpenStreetMapの標準タイルで、経度は一周して折り返す`() {
        assertEquals("https://tile.openstreetmap.org/5/10/12.png", TileUrls.url(5, 10, 12))
        assertEquals(3 to 2, TileUrls.normalize(3, -5, 2))
        assertEquals(1 to 2, TileUrls.normalize(3, 9, 2))
        assertNull(TileUrls.normalize(3, 1, -1))
        assertNull(TileUrls.normalize(3, 1, 8))
    }

    @Test
    fun `画面の密度が高いときは、細かいタイルを選んで引き伸ばさない`() {
        assertEquals(15, MapViewport(35.0, 139.0, 15.0, 1000, 1000, 1.0).tileZoom)
        assertEquals(16, MapViewport(35.0, 139.0, 15.0, 1000, 1000, 2.0).tileZoom) // 密度2倍 → 1段細かい
        assertEquals(17, MapViewport(35.0, 139.0, 15.2, 1000, 1000, 3.0).tileZoom) // 15.2 + log2(3)=16.8 → 17
        assertEquals(MapViewport.MAX_TILE_ZOOM, MapViewport(35.0, 139.0, 19.5, 1000, 1000, 3.0).tileZoom)
    }

    @Test
    fun `どんな小数のズーム・密度でも、見えているタイルの範囲が画面全体を覆う`() {
        for (scale in listOf(1.0, 1.5, 2.0, 2.625, 3.0)) {
            for (zoom in listOf(11.0, 11.3, 12.5, 13.49, 13.51, 14.7, 15.0, 16.25)) {
                val vp = MapViewport(35.68, 139.76, zoom, 1080, 1920, scale)
                val r = vp.visibleTiles()
                val z = r.z.toDouble()
                val left = vp.toScreenX(WebMercator.lon(r.minX * 256.0, z))
                val right = vp.toScreenX(WebMercator.lon((r.maxX + 1) * 256.0, z))
                val top = vp.toScreenY(WebMercator.lat(r.minY * 256.0, z))
                val bottom = vp.toScreenY(WebMercator.lat((r.maxY + 1) * 256.0, z))
                assertTrue("左が足りない zoom=$zoom scale=$scale left=$left", left <= 0.5)
                assertTrue("右が足りない zoom=$zoom scale=$scale right=$right", right >= 1079.5)
                assertTrue("上が足りない zoom=$zoom scale=$scale top=$top", top <= 0.5)
                assertTrue("下が足りない zoom=$zoom scale=$scale bottom=$bottom", bottom >= 1919.5)
                // タイルは、画面で256px前後(0.7〜1.42倍)に描かれる = ぼやけず、多すぎもしない
                val tilePx = r.tilePx
                assertTrue("タイルの大きさ $tilePx", tilePx in 256 * 0.69..256 * 1.43)
            }
        }
    }

    @Test
    fun `ルートが収まるズームで、全点が余白の内側に入る`() {
        val pts = listOf(LatLon(35.60, 139.60), LatLon(35.75, 139.85), LatLon(35.68, 139.70))
        val bounds = GeoBounds.of(pts)!!
        val vp = MapViewport.fit(bounds, 1080, 1350, paddingPx = 80, scale = 2.0)
        for (p in pts) {
            val x = vp.toScreenX(p.lon)
            val y = vp.toScreenY(p.lat)
            assertTrue("x=$x", x >= 79.0 && x <= 1080 - 79.0)
            assertTrue("y=$y", y >= 79.0 && y <= 1350 - 79.0)
        }
        // 収まる最大のズームなので、縦か横のどちらかはほぼ余白いっぱいまで使っている
        val xs = pts.map { vp.toScreenX(it.lon) }
        val ys = pts.map { vp.toScreenY(it.lat) }
        val usesFullWidth = abs((xs.max() - xs.min()) - (1080 - 160)) < 3.0
        val usesFullHeight = abs((ys.max() - ys.min()) - (1350 - 160)) < 3.0
        assertTrue(usesFullWidth || usesFullHeight)
    }

    @Test
    fun `1点だけのときは上限のズームで止まる`() {
        val vp = MapViewport.fit(GeoBounds.of(listOf(LatLon(35.0, 139.0)))!!, 800, 800, 50, maxFitZoom = 16.0)
        assertEquals(16.0, vp.zoom, 1e-9)
        assertEquals(35.0, vp.centerLat, 1e-6)
    }

    @Test
    fun `画面と地図の座標は往復できる`() {
        val vp = MapViewport(35.68, 139.76, 14.3, 1080, 1920, 3.0)
        val x = vp.toScreenX(139.77)
        val y = vp.toScreenY(35.69)
        assertEquals(139.77, vp.lonAt(x), 1e-9)
        assertEquals(35.69, vp.latAt(y), 1e-9)
        assertEquals(540.0, vp.toScreenX(139.76), 1e-6)
    }

    @Test
    fun `見えているタイルの範囲は画面全体を覆う`() {
        val vp = MapViewport(35.68, 139.76, 15.0, 1080, 1920, 3.0)
        val r = vp.visibleTiles()
        assertEquals(17, r.z) // 密度3倍なので、ズーム15の表示には1段細かい(ズーム17)タイルを使う
        assertTrue(r.count in 40..120)
        // 範囲の端のタイルが、画面の外まで届いている
        val leftEdge = vp.toScreenX(WebMercator.lon(r.minX * 256.0, r.z.toDouble()))
        val rightEdge = vp.toScreenX(WebMercator.lon((r.maxX + 1) * 256.0, r.z.toDouble()))
        assertTrue(leftEdge <= 0.0)
        assertTrue(rightEdge >= 1080.0)
    }
}
