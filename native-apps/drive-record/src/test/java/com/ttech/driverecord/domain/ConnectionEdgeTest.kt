package com.ttech.driverecord.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionEdgeTest {
    @Test
    fun `切れている状態から、つながった瞬間だけtrue`() {
        val e = ConnectionEdge()
        assertTrue(e.onConnected(true))
        assertFalse(e.onConnected(true)) // つながったままの通知(同じ値の再通知)では、また立ち上がったことにしない
        assertFalse(e.onConnected(true))
    }

    @Test
    fun `切れたら、また次につながったときにtrue`() {
        val e = ConnectionEdge()
        assertTrue(e.onConnected(true))
        assertFalse(e.onConnected(false))
        assertFalse(e.onConnected(false))
        assertTrue(e.onConnected(true))
    }

    @Test
    fun `最初から切れている通知では、falseのまま`() {
        val e = ConnectionEdge()
        assertFalse(e.onConnected(false))
        assertFalse(e.onConnected(false))
    }
}
