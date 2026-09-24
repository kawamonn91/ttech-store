package com.ttech.admin.domain

import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtTest {
    private fun token(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "${enc.encodeToString("""{"alg":"HS256"}""".toByteArray())}.${enc.encodeToString(payloadJson.toByteArray())}.sig"
    }

    @Test
    fun `有効期限と認証の強さを読める`() {
        val t = token("""{"sub":"u1","exp":1790000000,"aal":"aal2"}""")
        assertEquals(1790000000L, Jwt.expiresAt(t))
        assertEquals("aal2", Jwt.aal(t))
    }

    @Test
    fun `項目が無ければnull`() {
        val t = token("""{"sub":"u1"}""")
        assertNull(Jwt.expiresAt(t))
        assertNull(Jwt.aal(t))
    }

    @Test
    fun `壊れたトークンは例外にせずnull`() {
        assertNull(Jwt.payload("not-a-jwt"))
        assertNull(Jwt.payload("a.b.c"))
        assertNull(Jwt.expiresAt(""))
    }

    @Test
    fun `日本語を含むペイロードも読める`() {
        assertEquals("aal1", Jwt.aal(token("""{"name":"管理者","aal":"aal1"}""")))
    }
}

class SessionTest {
    private val session = Session("a", "r", expiresAtEpochSec = 1_000, userId = "u", email = null)

    @Test
    fun `期限まで余裕があれば更新しない`() {
        assertFalse(session.needsRefresh(nowEpochSec = 500))
        assertFalse(session.needsRefresh(nowEpochSec = 939)) // 残り61秒
    }

    @Test
    fun `期限が近い・過ぎていれば更新する`() {
        assertTrue(session.needsRefresh(nowEpochSec = 940)) // 残り60秒(境界)
        assertTrue(session.needsRefresh(nowEpochSec = 999))
        assertTrue(session.needsRefresh(nowEpochSec = 5_000))
    }
}

class LabelsTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `日時は日本時間で表示する`() {
        assertEquals("2026/09/24 20:34", Labels.dateTime("2026-09-24T11:34:00Z", tokyo))
        assertEquals("2026/09/25 09:00", Labels.dateTime("2026-09-25T00:00:00Z", tokyo)) // 日付をまたぐ
        assertEquals("2026/09/24", Labels.date("2026-09-24T11:34:00.123Z", tokyo))
    }

    @Test
    fun `日時が無い・読めない場合`() {
        assertEquals("-", Labels.dateTime(null))
        assertEquals("-", Labels.dateTime(""))
        assertEquals("いつか", Labels.dateTime("いつか"))
    }

    @Test
    fun `報告の理由・状態を日本語にする(未知の値はそのまま)`() {
        assertEquals("嫌がらせ・暴言", Labels.reason("harassment"))
        assertEquals("new-reason", Labels.reason("new-reason"))
        assertEquals("未対応", Labels.reportStatus("open"))
        assertEquals("対応済み", Labels.reportStatus("actioned"))
        assertEquals("対応済み", Labels.reportStatus("resolved"))
        assertEquals("問題なし", Labels.reportStatus("dismissed"))
    }

    @Test
    fun `権限・ログイン方法・公開範囲`() {
        assertEquals("管理者", Labels.role("admin"))
        assertEquals("利用者", Labels.role("user"))
        assertEquals("Google", Labels.provider("google"))
        assertEquals("友達のみ", Labels.visibility("friends"))
    }

    @Test
    fun `監査ログの操作名`() {
        assertEquals("ユーザーをBAN", Labels.auditAction("user.ban"))
        assertEquals("日記の報告を処理(投稿者をBAN)", Labels.auditAction("report.diary.ban_author"))
        assertEquals("レビューの通報を処理(問題なし)", Labels.auditAction("report.review.dismiss"))
        assertEquals("アプリを公開停止", Labels.auditAction("app.suspended"))
        assertEquals("something.new", Labels.auditAction("something.new"))
    }
}

class ReportRulesTest {
    @Test
    fun `未対応で内容も投稿者も残っていれば、3つの操作が選べる`() {
        val actions = ReportRules.availableActions("open", contentId = "e1", targetUserId = "u1")
        assertEquals(listOf(ReportAction.Dismiss, ReportAction.DeleteContent, ReportAction.BanAuthor), actions)
    }

    @Test
    fun `内容が既に削除済みなら「削除する」は出さない`() {
        assertEquals(listOf(ReportAction.Dismiss, ReportAction.BanAuthor), ReportRules.availableActions("open", null, "u1"))
    }

    @Test
    fun `投稿者のアカウントが削除済みなら「BAN」は出さない`() {
        assertEquals(listOf(ReportAction.Dismiss, ReportAction.DeleteContent), ReportRules.availableActions("open", "e1", null))
    }

    @Test
    fun `対応済みの報告には操作を出さない`() {
        for (status in listOf("actioned", "dismissed", "reviewed", "resolved")) {
            assertTrue(status, ReportRules.availableActions(status, "e1", "u1").isEmpty())
        }
    }

    @Test
    fun `APIに送る操作名はサーバーの定義と一致する`() {
        assertEquals(listOf("dismiss", "delete_content", "ban_author"), ReportAction.entries.map { it.id })
    }

    @Test
    fun `削除の表示名は日記とレビューで変わる`() {
        assertEquals("投稿を削除する", ReportRules.deleteLabel("diary"))
        assertEquals("レビューを非表示にする", ReportRules.deleteLabel("review"))
    }
}

class TotpTest {
    @Test
    fun `空白やハイフンが混ざっても6桁として扱う`() {
        assertEquals("123456", Totp.normalize("123 456"))
        assertEquals("123456", Totp.normalize("123-456"))
        assertTrue(Totp.isValid(" 123 456 "))
    }

    @Test
    fun `6桁でなければ無効`() {
        assertFalse(Totp.isValid("12345"))
        assertFalse(Totp.isValid("1234567"))
        assertFalse(Totp.isValid("abcdef"))
        assertFalse(Totp.isValid(""))
    }
}
