package com.kawamonn.store.ui.account

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewLabelsTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `アプリの状態は、Webと同じ言葉`() {
        assertEquals("非公開", appStatusLabel("draft"))
        assertEquals("審査中", appStatusLabel("pending"))
        assertEquals("公開中", appStatusLabel("published"))
        assertEquals("停止中", appStatusLabel("suspended"))
        assertEquals("unknown", appStatusLabel("unknown")) // 知らない状態は、そのまま出す
    }

    @Test
    fun `リリースの状態は、Webと同じ言葉`() {
        assertEquals("検査中", releaseStateLabel("uploaded", false, null))
        assertEquals("運営が確認中", releaseStateLabel("scanned", false, "needs_review"))
        assertEquals("承認待ち", releaseStateLabel("scanned", false, null))
        assertEquals("承認済み(非公開)", releaseStateLabel("approved", false, null))
        assertEquals("公開中", releaseStateLabel("published", false, null))
        assertEquals("公開中(自動審査を通過)", releaseStateLabel("published", true, "auto_approve"))
        assertEquals("公開できませんでした", releaseStateLabel("rejected", false, "reject"))
    }

    @Test
    fun `確認が必要な理由は、reviewとblockだけを、重複なく取り出す`() {
        val findings = json(
            """[
              {"code":"net.permission","severity":"review","title":"通信につながる権限を要求しています","detail":"...","evidence":["INTERNET"]},
              {"code":"perm.sensitive","severity":"info","title":"位置情報を読む権限があります"},
              {"code":"sig.debug","severity":"block","title":"デバッグ用の署名です"},
              {"code":"net.api","severity":"review","title":"通信につながる権限を要求しています"}
            ]""",
        )
        assertEquals(listOf("通信につながる権限を要求しています", "デバッグ用の署名です"), reviewReasons(findings))
    }

    @Test
    fun `理由の数には上限がある`() {
        val many = json("[" + (1..10).joinToString(",") { """{"severity":"review","title":"理由$it"}""" } + "]")
        assertEquals(listOf("理由1", "理由2", "理由3"), reviewReasons(many, max = 3))
    }

    @Test
    fun `想定と違う形式でも、落ちずに空になる`() {
        assertEquals(emptyList<String>(), reviewReasons(null))
        assertEquals(emptyList<String>(), reviewReasons(json("{}")))
        assertEquals(emptyList<String>(), reviewReasons(json("\"text\"")))
        assertEquals(emptyList<String>(), reviewReasons(json("[1, null, \"a\", {\"severity\": 5}, {\"severity\":\"review\"}, {\"severity\":\"review\",\"title\":\"  \"}]")))
    }

    @Test
    fun `最新のリリースは、作成日時が新しいもの`() {
        data class R(val id: String, val at: String)
        val list = listOf(R("a", "2026-09-01T10:00:00Z"), R("c", "2026-09-25T08:00:00Z"), R("b", "2026-09-10T09:00:00Z"))
        assertEquals("c", newestRelease(list) { it.at }?.id)
        assertNull(newestRelease(emptyList<R>()) { it.at })
    }
}
