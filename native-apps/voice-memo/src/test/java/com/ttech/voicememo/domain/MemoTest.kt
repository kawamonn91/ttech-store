package com.ttech.voicememo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoTest {
    @Test
    fun `確定した文の後ろに認識途中の文をつなげる`() {
        assertEquals("今日は晴れです明日は", liveTranscript("今日は晴れです", "明日は"))
        assertEquals("今日は", liveTranscript("", "今日は"))
    }

    @Test
    fun `メモは前後の空白を除き空なら作らない`() {
        assertEquals(Memo("1", "2026-09-24", "買い物に行く"), buildMemo("1", "2026-09-24", "  買い物に行く "))
        assertNull(buildMemo("1", "2026-09-24", "   "))
    }

    @Test
    fun `書き出しは日付付きで空行区切り`() {
        val memos = listOf(Memo("1", "2026-09-24", "一つ目"), Memo("2", "2026-09-23", "二つ目"))
        assertEquals("[2026-09-24]\n一つ目\n\n[2026-09-23]\n二つ目", exportText(memos))
        assertEquals("", exportText(emptyList()))
    }
}
