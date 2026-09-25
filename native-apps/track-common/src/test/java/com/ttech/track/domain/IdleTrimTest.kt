package com.ttech.track.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTrimTest {
    private fun pts(speeds: List<Double>): List<TrackPoint> =
        speeds.mapIndexed { i, v -> TrackPoint(1_000L * i, 35.0 + i * 1e-5, 139.0, speed = v, hAcc = 5.0) }

    @Test
    fun `出発前と到着後の停止を取り除き、前後5秒だけ残す`() {
        val speeds = List(60) { 0.0 } + List(100) { 12.0 } + List(90) { 0.0 }
        val trimmed = IdleTrim.trim(pts(speeds))
        assertEquals(110, trimmed.size) // 走行100点 + 前5点 + 後5点
        assertEquals(55_000L, trimmed.first().timeMs)
        assertEquals(164_000L, trimmed.last().timeMs)
    }

    @Test
    fun `途中の停止(信号待ち)は取り除かない`() {
        val speeds = List(30) { 0.0 } + List(20) { 12.0 } + List(40) { 0.0 } + List(20) { 12.0 } + List(30) { 0.0 }
        assertEquals(20 + 40 + 20 + 10, IdleTrim.trim(pts(speeds)).size)
    }

    @Test
    fun `最初から最後まで走っていれば、そのまま`() {
        val all = pts(List(50) { 12.0 })
        assertEquals(all, IdleTrim.trim(all))
    }

    @Test
    fun `一度も動いていない記録は、そのまま返す`() {
        val all = pts(List(50) { 0.1 })
        assertEquals(all, IdleTrim.trim(all))
    }

    @Test
    fun `点が少なければ触らない`() {
        assertEquals(1, IdleTrim.trim(pts(listOf(0.0))).size)
        assertTrue(IdleTrim.trim(emptyList()).isEmpty())
    }

    @Test
    fun `動いているとみなす速度は指定できる(歩き・ランニングは遅い)`() {
        // 時速4.5km(1.25m/秒)の歩き: 車の基準(1.5)では動いていない扱い、歩きの基準(0.8)なら動いている
        val walking = pts(List(40) { 0.0 } + List(60) { 1.25 } + List(40) { 0.0 })
        assertEquals(walking, IdleTrim.trim(walking, movingMps = 1.5))
        assertEquals(60 + 10, IdleTrim.trim(walking, movingMps = 0.8).size)
    }
}
