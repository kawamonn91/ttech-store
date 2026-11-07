package com.ttech.pocketdev.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

class SharedCloseableTest {

    private class Resource : Closeable {
        var closeCount = 0

        override fun close() {
            closeCount++
        }
    }

    /** 時間の経過とバックグラウンドの処理を、テストから動かす */
    private class Harness {
        val created = mutableListOf<Resource>()
        val pendingTimers = mutableListOf<() -> Unit>()
        val background = mutableListOf<() -> Unit>()

        val shared = SharedCloseable(
            create = { Resource().also { created += it } },
            idleMs = 30_000,
            schedule = { _, action ->
                pendingTimers += action
                ({ pendingTimers.remove(action) })
            },
            runInBackground = { background += it },
        )

        fun fireTimers() {
            pendingTimers.toList().also { pendingTimers.clear() }.forEach { it() }
        }

        fun runBackground() {
            background.toList().also { background.clear() }.forEach { it() }
        }
    }

    @Test
    fun `返してもすぐには止めず_時間がたったら止める`() {
        val h = Harness()
        val lease = h.shared.acquire()
        lease.close()
        h.runBackground()
        assertEquals(0, lease.value.closeCount)

        h.fireTimers()
        h.runBackground()
        assertEquals(1, lease.value.closeCount)
    }

    @Test
    fun `時間がたつ前に借り直すと_同じものを使い続ける`() {
        val h = Harness()
        val first = h.shared.acquire()
        first.close()

        val second = h.shared.acquire()
        assertSame(first.value, second.value)
        assertTrue(h.pendingTimers.isEmpty())

        h.fireTimers()
        h.runBackground()
        assertEquals(0, second.value.closeCount)
    }

    @Test
    fun `借りている人が残っている間は止めない`() {
        val h = Harness()
        val a = h.shared.acquire()
        val b = h.shared.acquire()
        a.close()
        assertTrue(h.pendingTimers.isEmpty())

        b.close()
        assertEquals(1, h.pendingTimers.size)
    }

    @Test
    fun `同じ札を2回閉じても1回分しか返さない`() {
        val h = Harness()
        val a = h.shared.acquire()
        val b = h.shared.acquire()
        a.close()
        a.close()
        assertTrue(h.pendingTimers.isEmpty())
        b.close()
        assertEquals(1, h.pendingTimers.size)
    }

    @Test
    fun `stopNowは呼んだスレッドで待たず_借りている人がいても止める`() {
        val h = Harness()
        val lease = h.shared.acquire()

        h.shared.stopNow()
        assertEquals(0, lease.value.closeCount)
        assertEquals(1, h.background.size)

        h.runBackground()
        assertEquals(1, lease.value.closeCount)
    }

    @Test
    fun `止めたあとは新しく作り_古い札を返しても新しいものに影響しない`() {
        val h = Harness()
        val old = h.shared.acquire()
        h.shared.stopNow()
        h.runBackground()

        val fresh = h.shared.acquire()
        assertNotSame(old.value, fresh.value)

        old.close()
        assertTrue(h.pendingTimers.isEmpty())
        h.runBackground()
        assertEquals(0, fresh.value.closeCount)
        assertEquals(2, h.created.size)
    }

    @Test
    fun `時間を待っている間にstopNowで止めると_二重に閉じない`() {
        val h = Harness()
        val lease = h.shared.acquire()
        lease.close()

        h.shared.stopNow()
        h.runBackground()
        assertTrue(h.pendingTimers.isEmpty())
        h.fireTimers()
        h.runBackground()
        assertEquals(1, lease.value.closeCount)
    }

    @Test
    fun `一度も借りていなければstopNowは何もしない`() {
        val h = Harness()
        h.shared.stopNow()
        assertTrue(h.background.isEmpty())
        assertFalse(h.created.isNotEmpty())
    }
}
