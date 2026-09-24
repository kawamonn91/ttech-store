package com.ttech.meetingnotes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MeetingNotesTest {
    @Test
    fun `入力内容を議事録テキストに整形する`() {
        val form = NotesForm(
            title = "定例会議",
            date = "2026-09-24",
            attendees = "山田, 佐藤",
            agenda = "新機能の検討",
            decisions = "来週リリース",
            actionItems = "山田: テスト",
        )
        val expected = """
            # 定例会議

            日付: 2026-09-24
            参加者: 山田, 佐藤

            ## 議題
            新機能の検討

            ## 決定事項
            来週リリース

            ## ToDo / アクションアイテム
            山田: テスト
        """.trimIndent()
        assertEquals(expected, buildFormattedText(form))
    }

    @Test
    fun `未入力の項目は未記入と表示しタイトルは議事録になる`() {
        val text = buildFormattedText(NotesForm(date = "2026-09-24"))
        val lines = text.lines()
        assertEquals("# 議事録", lines[0])
        assertEquals("参加者: (未記入)", lines[3])
        assertEquals(3, lines.count { it == "(未記入)" })
    }

    @Test
    fun `ファイル名は会議名から作り使えない文字を置き換える`() {
        assertEquals("定例会議.txt", textFilename(NotesForm(title = "定例会議")))
        assertEquals("議事録.txt", textFilename(NotesForm()))
        assertEquals("9_24 定例_A.txt", textFilename(NotesForm(title = "9/24 定例:A")))
    }
}
