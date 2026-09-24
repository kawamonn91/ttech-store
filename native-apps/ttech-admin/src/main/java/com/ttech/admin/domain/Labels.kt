package com.ttech.admin.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 画面に出す日本語の表記。サーバーの値(英語のコード)を管理者が読める言葉にする */
object Labels {
    private val diaryReasons = mapOf(
        "spam" to "スパム・宣伝",
        "harassment" to "嫌がらせ・暴言",
        "inappropriate" to "不適切な内容",
        "privacy" to "プライバシーの侵害",
        "other" to "その他",
    )

    fun reason(code: String): String = diaryReasons[code] ?: code

    fun role(code: String): String = when (code) {
        "admin" -> "管理者"
        "developer" -> "開発者"
        else -> "利用者"
    }

    fun provider(code: String): String = when (code) {
        "google" -> "Google"
        "email" -> "メール"
        else -> code
    }

    fun visibility(code: String): String = when (code) {
        "public" -> "公開"
        "friends" -> "友達のみ"
        "private" -> "非公開"
        else -> code
    }

    fun reportStatus(code: String): String = when (code) {
        "open" -> "未対応"
        "reviewed" -> "確認済み"
        "actioned" -> "対応済み"
        "dismissed" -> "問題なし"
        "resolved" -> "対応済み"
        else -> code
    }

    fun appStatus(code: String): String = when (code) {
        "draft" -> "下書き"
        "pending" -> "審査中"
        "published" -> "公開中"
        "suspended" -> "停止中"
        else -> code
    }

    fun developerStatus(code: String): String = when (code) {
        "pending" -> "承認待ち"
        "approved" -> "承認済み"
        "suspended" -> "停止中"
        else -> code
    }

    fun reviewStatus(code: String): String = if (code == "hidden") "非表示" else "表示中"

    /** 監査ログの操作名(例: "user.ban" → "ユーザーをBAN") */
    fun auditAction(code: String): String = when {
        code == "user.ban" -> "ユーザーをBAN"
        code == "user.unban" -> "BANを解除"
        code == "diary_entry.delete" -> "日記の投稿を削除"
        code.startsWith("report.diary.") -> "日記の報告を処理(${reportAction(code.removePrefix("report.diary."))})"
        code.startsWith("report.review.") -> "レビューの通報を処理(${reportAction(code.removePrefix("report.review."))})"
        code == "review.hidden" -> "レビューを非表示"
        code == "review.visible" -> "レビューを再表示"
        code == "developer.approved" -> "開発者を承認"
        code == "developer.suspended" -> "開発者を停止"
        code == "app.published" -> "アプリを公開"
        code == "app.suspended" -> "アプリを公開停止"
        else -> code
    }

    private fun reportAction(code: String): String = when (code) {
        "dismiss" -> "問題なし"
        "delete_content" -> "削除・非表示"
        "ban_author" -> "投稿者をBAN"
        else -> code
    }

    private val dateTime = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    /** ISO 8601 の日時を「2026/09/24 20:34」(日本時間)にする。読めない値はそのまま返す */
    fun dateTime(iso: String?, zone: ZoneId = ZoneId.of("Asia/Tokyo")): String {
        if (iso.isNullOrBlank()) return "-"
        return runCatching { dateTime.format(Instant.parse(iso).atZone(zone)) }.getOrDefault(iso)
    }

    fun date(iso: String?, zone: ZoneId = ZoneId.of("Asia/Tokyo")): String {
        if (iso.isNullOrBlank()) return "-"
        return runCatching { DateTimeFormatter.ofPattern("yyyy/MM/dd").format(Instant.parse(iso).atZone(zone)) }.getOrDefault(iso)
    }
}
