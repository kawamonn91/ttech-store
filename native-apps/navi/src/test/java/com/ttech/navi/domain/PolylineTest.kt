package com.ttech.navi.domain

import com.ttech.track.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {
    private val kLon = Polyline.METERS_PER_DEG * Math.cos(Math.toRadians(35.0))

    /** 北へ1000m、そこから東へ1000m のL字 */
    private fun lShape(): Polyline {
        val north = 1000.0 / Polyline.METERS_PER_DEG
        val east = 1000.0 / kLon
        return Polyline(listOf(LatLon(35.0, 139.0), LatLon(35.0 + north, 139.0), LatLon(35.0 + north, 139.0 + east)))
    }

    @Test
    fun `長さと、道のりから位置を求める`() {
        val line = lShape()
        assertEquals(2000.0, line.lengthM, 2.0)
        assertEquals(35.0 + 500.0 / Polyline.METERS_PER_DEG, line.pointAt(500.0).lat, 1e-6)
        assertEquals(line.points.last(), line.pointAt(5000.0))
        assertEquals(line.points.first(), line.pointAt(-5.0))
    }

    @Test
    fun `位置をルートに当てはめる`() {
        val line = lShape()
        // 北へ600m進んだ地点から、東へ30mずれた位置
        val p = LatLon(35.0 + 600.0 / Polyline.METERS_PER_DEG, 139.0 + 30.0 / kLon)
        val proj = line.project(p)
        assertEquals(600.0, proj.progressM, 3.0)
        assertEquals(30.0, proj.distM, 1.0)
        // 角を曲がった先(東向きの区間)から、北へ20mずれた位置
        val q = LatLon(35.0 + 1020.0 / Polyline.METERS_PER_DEG, 139.0 + 400.0 / kLon)
        val projQ = line.project(q)
        assertEquals(1400.0, projQ.progressM, 3.0)
        assertEquals(20.0, projQ.distM, 1.0)
    }

    @Test
    fun `一部分の折れ線を取り出す`() {
        val line = lShape()
        val part = line.slice(500.0, 1500.0)
        assertEquals(3, part.size) // 始まり + 角の点 + 終わり
        assertTrue(part.first() == line.pointAt(500.0))
        assertTrue(part.last() == line.pointAt(1500.0))
    }
}
