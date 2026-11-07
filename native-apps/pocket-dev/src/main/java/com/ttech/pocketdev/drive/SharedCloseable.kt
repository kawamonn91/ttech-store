package com.ttech.pocketdev.drive

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** [SharedCloseable] から借りたもの。閉じると返す（何度閉じても 1 回分） */
class Lease<T>(val value: T, private val onClose: () -> Unit) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

/**
 * 1 つの [Closeable] を複数の画面で使い回し、誰も使わなくなって [idleMs] たったら止める。
 *
 * フォルダの選択から IDE に移るときは、前の画面が返してから次の画面が借りるまでに少し間がある。
 * すぐに止めると起動し直しになるので、少し待つ。
 *
 * メインスレッドからも呼ばれる。止める処理は時間がかかることがあるので [runInBackground] で動かし、
 * ロックの中では待たない。
 */
class SharedCloseable<T : Closeable>(
    private val create: () -> T,
    private val idleMs: Long,
    /** 指定したミリ秒のあとで処理を動かす。戻り値を呼ぶと取り消す */
    private val schedule: (delayMs: Long, action: () -> Unit) -> () -> Unit,
    private val runInBackground: (action: () -> Unit) -> Unit,
) {
    private var current: T? = null
    private var users = 0
    private var cancelIdleStop: (() -> Unit)? = null

    @Synchronized
    fun acquire(): Lease<T> {
        clearIdleStop()
        val value = current ?: create().also { current = it }
        users++
        return Lease(value) { release(value) }
    }

    /** 借りている人がいても、すぐに止める（次に借りるときは新しく作る） */
    @Synchronized
    fun stopNow() {
        clearIdleStop()
        val value = current ?: return
        current = null
        users = 0
        runInBackground { value.close() }
    }

    @Synchronized
    private fun release(value: T) {
        // すでに stopNow で止めたものを返しただけなら、今のものには関係ない
        if (value !== current) return
        users--
        if (users == 0) cancelIdleStop = schedule(idleMs) { stopIfIdle(value) }
    }

    @Synchronized
    private fun stopIfIdle(value: T) {
        if (value === current && users == 0) stopNow()
    }

    private fun clearIdleStop() {
        cancelIdleStop?.invoke()
        cancelIdleStop = null
    }
}
