package com.ttech.ideamemo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaTextAndFileTest {
    private var seq = 0
    private fun newId() = "id${++seq}"

    private val habit = Idea(
        id = "a", title = "習慣トラッカー", oneLiner = "毎日のチェックだけで続く", category = Category.HEALTH,
        priority = 3, memo = "禁煙・筋トレなど複数管理", status = Status.IDEA,
    )
    private val kakeibo = Idea(id = "b", title = "レシート家計簿", category = Category.FINANCE, priority = 2, status = Status.CONSIDERING)
    private val shiori = Idea(id = "c", title = "旅のしおり", category = Category.HOBBY, priority = 3, status = Status.BUILT)

    @Test
    fun `1件の共有テキスト`() {
        val expected = """
            【アプリのアイデア】習慣トラッカー
            ★★★ 健康・ライフログ ・ 思いつき
            毎日のチェックだけで続く
            禁煙・筋トレなど複数管理
        """.trimIndent() + "\n"
        assertEquals(expected, habit.toShareText())
    }

    @Test
    fun `参考を書いていれば、共有テキストに含める`() {
        val withRef = habit.copy(reference = "既存の習慣アプリは有料が多い")
        assertTrue(withRef.toShareText().contains("きっかけ: 既存の習慣アプリは有料が多い"))
    }

    @Test
    fun `リスト全体の共有テキストは、状態ごと(思いつき→検討中→作る予定→作った→見送り)の順`() {
        val expected = """
            【アプリのアイデア帳】3件

            ■思いつき
            ★★★ 習慣トラッカー(健康・ライフログ ・ 毎日のチェックだけで続く)

            ■検討中
            ★★ レシート家計簿(家計・お金)

            ■作った
            ★★★ 旅のしおり(趣味・ホビー)
        """.trimIndent() + "\n"
        assertEquals(expected, ideasText(listOf(shiori, kakeibo, habit)))
    }

    @Test
    fun `空のリストの共有テキスト`() {
        assertEquals("【アプリのアイデア帳】0件\n", ideasText(emptyList()))
    }

    @Test
    fun `サンプルは全部整っていて、状態がいろいろある`() {
        val samples = sampleIdeas(::newId, nowMs = 1000)
        assertEquals(5, samples.size)
        assertEquals(samples.size, samples.map { it.id }.toSet().size)
        assertEquals(samples, samples.map { it.sanitized(nowMs = 1000) })
        assertTrue(samples.map { it.status }.toSet().size >= 3)
        assertEquals(samples.sortedByDescending { it.createdAtMs }, samples)
    }

    @Test
    fun `メモファイルを書き出して読み込むと、中身は同じで、idは新しくなる`() {
        val ideas = listOf(habit, kakeibo, shiori)
        val decoded = IdeaFile.decode(IdeaFile.encode(ideas), ::newId, nowMs = 99) as IdeaFile.Decoded.Ok
        assertEquals(ideas.map { it.title }, decoded.ideas.map { it.title })
        assertEquals(ideas.map { it.copy(id = "", updatedAtMs = 0) }, decoded.ideas.map { it.copy(id = "", updatedAtMs = 0) })
        assertEquals(3, decoded.ideas.map { it.id }.toSet().size)
        assertFalse(decoded.ideas.any { it.id in setOf("a", "b", "c") })
        assertTrue(decoded.ideas.all { it.updatedAtMs == 99L })
    }

    @Test
    fun `アイデア帳のファイルでないものは断る`() {
        for (text in listOf("hello", "{}", "", """{"format":"other","version":1,"ideas":[]}""", """{"format":"ttech-ideamemo","version":1}""")) {
            assertEquals(text, "アイデア帳のファイルではありません", (IdeaFile.decode(text, ::newId, 1) as IdeaFile.Decoded.Error).message)
        }
    }

    @Test
    fun `新しい版・大きすぎるファイルは断る`() {
        val newer = IdeaFile.encode(listOf(habit)).replace("\"version\": 1", "\"version\": 2")
        assertTrue((IdeaFile.decode(newer, ::newId, 1) as IdeaFile.Decoded.Error).message.contains("アプリを更新"))
        assertEquals("ファイルが大きすぎます", (IdeaFile.decode("x".repeat(IdeaFile.MAX_BYTES + 1), ::newId, 1) as IdeaFile.Decoded.Error).message)
    }

    @Test
    fun `知らない項目・知らない種類があっても読める。中身は整えられる`() {
        val text = """
            {"format":"ttech-ideamemo","version":1,"extra":true,"ideas":[
              {"id":"x","title":"未来のアプリ","category":"SPACE","status":"TELEPORTED","priority":50,"photo":"a.png"}
            ]}
        """.trimIndent()
        val i = (IdeaFile.decode(text, ::newId, 1) as IdeaFile.Decoded.Ok).ideas.single()
        assertEquals("未来のアプリ", i.title)
        assertEquals(Category.OTHER, i.category)
        assertEquals(Status.IDEA, i.status)
        assertEquals(3, i.priority)
    }

    @Test
    fun `読み込む件数には上限がある`() {
        val many = List(Limits.MAX_IDEAS + 50) { habit.copy(id = "p$it") }
        val decoded = IdeaFile.decode(IdeaFile.encode(many), ::newId, 1) as IdeaFile.Decoded.Ok
        assertEquals(Limits.MAX_IDEAS, decoded.ideas.size)
    }
}
