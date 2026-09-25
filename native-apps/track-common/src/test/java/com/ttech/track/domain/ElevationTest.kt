package com.ttech.track.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationTest {
    private fun pts(vararg alts: Double?) = alts.mapIndexed { i, a -> TrackPoint(i * 1000L, 35.0, 139.0, altitude = a) }

    @Test
    fun `標高が1点も無ければ、上り下りは0で、最低・最高は無い`() {
        val p = Elevation.profile(pts(null, null, null))
        assertEquals(0.0, p.gain, 0.0)
        assertEquals(0.0, p.loss, 0.0)
        assertNull(p.min)
        assertNull(p.max)
        assertNull(Elevation.smoothed(pts(null, null)))
    }

    @Test
    fun `数mのぶれは上り下りに数えない`() {
        val p = Elevation.profile(pts(100.0, 101.5, 99.5, 101.0, 100.0, 101.5, 99.0, 100.5, 100.0, 100.0))
        assertEquals(0.0, p.gain, 0.0)
        assertEquals(0.0, p.loss, 0.0)
    }

    @Test
    fun `なだらかに登って下りれば、上りと下りを数える`() {
        val up = (0..30).map { 100.0 + it }            // 30m登る
        val down = (1..30).map { 130.0 - it * 0.5 }    // 15m下る
        val p = Elevation.profile(pts(*(up + down).toTypedArray()))
        assertEquals(30.0, p.gain, 4.0)
        assertEquals(15.0, p.loss, 4.0)
        assertNotNull(p.min)
        assertNotNull(p.max)
    }

    @Test
    fun `標高が取れなかった点は、前の値で埋めてならす`() {
        val s = Elevation.smoothed(pts(null, 100.0, null, 100.0, null))!!
        assertEquals(5, s.size)
        s.forEach { assertEquals(100.0, it, 1e-9) }
    }
}
