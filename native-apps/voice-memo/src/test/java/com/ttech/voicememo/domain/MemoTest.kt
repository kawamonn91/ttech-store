package com.ttech.voicememo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoTest {

    @Test
    fun `空や空白だけの文字起こしは保存できない`() {
        assertTrue(isValidMemoText("今日は晴れ"))
        assertFalse(isValidMemoText(""))
        assertFalse(isValidMemoText("  \n"))
    }

    @Test
    fun `途中経過は確定済みの文字列に続けて表示される`() {
        val t = Transcript(committed = "今日は").withPartial("いい天気")
        assertEquals("今日はいい天気", t.text)
    }

    @Test
    fun `確定すると途中経過が消えて確定済みに加わる`() {
        val t = Transcript().withPartial("きょう").withFinal("今日は").withPartial("晴れ").withFinal("晴れです")
        assertEquals("今日は晴れです", t.text)
        assertEquals("", t.partial)
    }

    @Test
    fun `途中経過だけ残して終わったときも文字は失わない`() {
        val t = Transcript(committed = "今日は", partial = "晴れ").flushed()
        assertEquals("今日は晴れ", t.committed)
        assertEquals("", t.partial)
    }

    @Test
    fun `書き出しは日付と本文を空行で区切る`() {
        val memos = listOf(Memo("1", "2026-09-24", "買い物に行く"), Memo("2", "2026-09-23", "電話する"))
        assertEquals("[2026-09-24]\n買い物に行く\n\n[2026-09-23]\n電話する", exportText(memos))
    }

    @Test
    fun `メモが無ければ書き出しは空文字`() {
        assertEquals("", exportText(emptyList()))
    }
}
